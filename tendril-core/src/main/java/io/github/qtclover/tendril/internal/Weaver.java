package io.github.qtclover.tendril.internal;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites method bodies so they call {@code Bridge}. For a hooked
 * {@code int damage(int amount)} the result is equivalent to:
 *
 * <pre>{@code
 * int damage(int amount) {
 *     Object[] args = { amount };
 *     Object ctx = Bridge.enter(SITE_ID, PlayerService.class, this, args);
 *     if (Bridge.cancelled(ctx)) return (int) Bridge.result(ctx);
 *     amount = (int) args[0];                      // hooks may have changed it
 *
 *     ... original body, with every "return x" turned into
 *     return (int) Bridge.exit(ctx, x);
 * }
 * }</pre>
 *
 * A hooked call inside the method, {@code r = receiver.name(a, b)}, becomes:
 *
 * <pre>{@code
 * Object[] callArgs = { a, b };
 * Object call = Bridge.beforeCall(ctx, POINT_ID, receiver, callArgs);
 * r = Bridge.skipped(call)
 *         ? (T) Bridge.callResult(call)
 *         : receiver.name((A) callArgs[0], (B) callArgs[1]);
 * r = (T) Bridge.afterCall(call, r);
 * }</pre>
 *
 * Only method bodies change: no fields, no methods, no signatures. That is the one
 * kind of change the JVM accepts for a class that is already loaded.
 */
final class Weaver {

    private static final int ASM_API = Opcodes.ASM9;
    private static final Type BRIDGE = Type.getObjectType("io/github/qtclover/tendril/agent/Bridge");
    private static final Type OBJECT = Type.getObjectType("java/lang/Object");
    private static final Type OBJECT_ARRAY = Type.getType("[Ljava/lang/Object;");
    private static final Method ENTER = new Method("enter",
            "(ILjava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    private static final Method CANCELLED = new Method("cancelled", "(Ljava/lang/Object;)Z");
    private static final Method RESULT = new Method("result", "(Ljava/lang/Object;)Ljava/lang/Object;");
    private static final Method EXIT = new Method("exit", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    private static final Method BEFORE_CALL = new Method("beforeCall",
            "(Ljava/lang/Object;ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    private static final Method SKIPPED = new Method("skipped", "(Ljava/lang/Object;)Z");
    private static final Method CALL_RESULT = new Method("callResult", "(Ljava/lang/Object;)Ljava/lang/Object;");
    private static final Method AFTER_CALL = new Method("afterCall",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    private static final int NOT_HOOKABLE = Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE;

    private Weaver() {
    }

    /**
     * Every method that has a body and is not a constructor or initialiser, keyed by
     * name + descriptor, with the calls it makes in instruction order. The position
     * in that list is how a call is identified, so {@link HookAdapter} must count
     * exactly the same instructions.
     */
    static Map<String, List<CallRef>> scan(ClassReader reader) {
        Map<String, List<CallRef>> result = new LinkedHashMap<>();
        reader.accept(new ClassVisitor(ASM_API) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if ((access & NOT_HOOKABLE) != 0 || name.startsWith("<")) {
                    return null;
                }
                List<CallRef> calls = new ArrayList<>();
                result.put(name + desc, calls);
                return new MethodVisitor(ASM_API) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String callee, String calleeDesc,
                                                boolean isInterface) {
                        calls.add(new CallRef(owner, callee, calleeDesc));
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }

    static byte[] weave(ClassReader reader, ClassLoader loader, Map<String, ClassPlan.Weave> plan) {
        int major = reader.readUnsignedShort(6);
        // Class files before Java 6 carry no stack map frames, and may use JSR/RET,
        // which ASM cannot compute frames for. Maximums are all they need.
        boolean frames = major >= Opcodes.V1_6;
        // "ldc Foo.class" only exists from Java 5 on.
        boolean classConstants = major >= Opcodes.V1_5;
        Type owner = Type.getObjectType(reader.getClassName());

        ClassWriter writer = new HierarchyClassWriter(reader,
                frames ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS, loader);
        ClassVisitor visitor = new ClassVisitor(ASM_API, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                ClassPlan.Weave weave = plan.get(name + desc);
                if (weave == null || mv == null || (access & NOT_HOOKABLE) != 0) {
                    // Returned untouched, which lets ASM copy the method byte for byte.
                    return mv;
                }
                return new HookAdapter(mv, access, name, desc, weave, classConstants ? owner : null);
            }
        };
        // ASM recomputes the frames of the methods we touch, so the old ones are noise.
        reader.accept(visitor, frames ? ClassReader.SKIP_FRAMES : 0);
        return writer.toByteArray();
    }

    private static final class HookAdapter extends AdviceAdapter {

        private final int siteId;
        private final Type owner;
        private final boolean isStatic;
        private final Type returnType;
        private final Type[] argumentTypes;
        private final Map<Integer, InvokePoint> points;
        private int contextLocal;
        private int callIndex;

        HookAdapter(MethodVisitor mv, int access, String name, String desc, ClassPlan.Weave weave, Type owner) {
            super(ASM_API, mv, access, name, desc);
            this.siteId = weave.site().id;
            this.points = weave.points();
            this.owner = owner;
            this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
            this.returnType = Type.getReturnType(desc);
            this.argumentTypes = Type.getArgumentTypes(desc);
        }

        @Override
        protected void onMethodEnter() {
            int argsLocal = newLocal(OBJECT_ARRAY);
            contextLocal = newLocal(OBJECT);

            // Object[] args = { boxed arguments };
            loadArgArray();
            storeLocal(argsLocal);

            // Object ctx = Bridge.enter(siteId, Owner.class, this, args);
            push(siteId);
            if (owner != null) {
                visitLdcInsn(owner);
            } else {
                visitInsn(ACONST_NULL);
            }
            if (isStatic) {
                visitInsn(ACONST_NULL);
            } else {
                loadThis();
            }
            loadLocal(argsLocal);
            invokeStatic(BRIDGE, ENTER);
            storeLocal(contextLocal);

            // if (Bridge.cancelled(ctx)) return (T) Bridge.result(ctx);
            Label proceed = newLabel();
            loadLocal(contextLocal);
            invokeStatic(BRIDGE, CANCELLED);
            ifZCmp(EQ, proceed);
            if (returnType.getSort() != Type.VOID) {
                loadLocal(contextLocal);
                invokeStatic(BRIDGE, RESULT);
                unbox(returnType);
            }
            // Straight to the underlying visitor: going through AdviceAdapter would
            // treat this as a return of the original method and weave exit code into it.
            mv.visitInsn(returnType.getOpcode(IRETURN));
            mark(proceed);

            // Copy the arguments back, in case a hook replaced any.
            for (int i = 0; i < argumentTypes.length; i++) {
                loadLocal(argsLocal);
                push(i);
                arrayLoad(OBJECT);
                unbox(argumentTypes[i]);
                storeArg(i);
            }
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (opcode == ATHROW) {
                return;
            }
            if (opcode == RETURN) {
                // Bridge.exit(ctx, null);
                loadLocal(contextLocal);
                visitInsn(ACONST_NULL);
                invokeStatic(BRIDGE, EXIT);
                pop();
            } else {
                // The return value is on top of the stack:
                // value = (T) Bridge.exit(ctx, box(value));
                valueOf(returnType);
                loadLocal(contextLocal);
                swap();
                invokeStatic(BRIDGE, EXIT);
                unbox(returnType);
            }
        }
    
        @Override
        public void visitMethodInsn(int opcode, String calleeOwner, String name, String desc, boolean isInterface) {
            // Code this adapter emits goes straight to "mv", so only instructions of
            // the original method arrive here, in the order scan() saw them.
            InvokePoint point = points.get(callIndex++);
            if (point == null) {
                super.visitMethodInsn(opcode, calleeOwner, name, desc, isInterface);
                return;
            }
            Type[] parameters = Type.getArgumentTypes(desc);
            Type result = Type.getReturnType(desc);
            boolean hasReceiver = opcode != INVOKESTATIC;

            // The receiver and arguments are on the stack. Move them into locals.
            // Typed locals keep the receiver's exact type for the verifier.
            int[] argumentLocals = new int[parameters.length];
            for (int i = parameters.length - 1; i >= 0; i--) {
                argumentLocals[i] = newLocal(parameters[i]);
                storeLocal(argumentLocals[i]);
            }
            int receiverLocal = -1;
            if (hasReceiver) {
                receiverLocal = newLocal(Type.getObjectType(calleeOwner));
                storeLocal(receiverLocal);
            }

            // Object[] callArgs = { boxed arguments };
            int argsLocal = newLocal(OBJECT_ARRAY);
            push(parameters.length);
            newArray(OBJECT);
            for (int i = 0; i < parameters.length; i++) {
                dup();
                push(i);
                loadLocal(argumentLocals[i]);
                valueOf(parameters[i]);
                arrayStore(OBJECT);
            }
            storeLocal(argsLocal);

            // Object call = Bridge.beforeCall(ctx, pointId, receiver, callArgs);
            int callLocal = newLocal(OBJECT);
            loadLocal(contextLocal);
            push(point.id);
            if (hasReceiver) {
                loadLocal(receiverLocal);
            } else {
                visitInsn(ACONST_NULL);
            }
            loadLocal(argsLocal);
            invokeStatic(BRIDGE, BEFORE_CALL);
            storeLocal(callLocal);

            // if (Bridge.skipped(call)) result = (T) Bridge.callResult(call);
            // else result = receiver.name(callArgs...);
            Label make = newLabel();
            Label done = newLabel();
            loadLocal(callLocal);
            invokeStatic(BRIDGE, SKIPPED);
            ifZCmp(EQ, make);
            if (result.getSort() != Type.VOID) {
                loadLocal(callLocal);
                invokeStatic(BRIDGE, CALL_RESULT);
                unbox(result);
            }
            goTo(done);
            mark(make);
            if (hasReceiver) {
                loadLocal(receiverLocal);
            }
            for (int i = 0; i < parameters.length; i++) {
                loadLocal(argsLocal);
                push(i);
                arrayLoad(OBJECT);
                unbox(parameters[i]);
            }
            mv.visitMethodInsn(opcode, calleeOwner, name, desc, isInterface);
            mark(done);

            // result = (T) Bridge.afterCall(call, box(result));
            if (result.getSort() == Type.VOID) {
                loadLocal(callLocal);
                visitInsn(ACONST_NULL);
                invokeStatic(BRIDGE, AFTER_CALL);
                pop();
            } else {
                valueOf(result);
                loadLocal(callLocal);
                swap();
                invokeStatic(BRIDGE, AFTER_CALL);
                unbox(result);
            }
        }
    }
}
