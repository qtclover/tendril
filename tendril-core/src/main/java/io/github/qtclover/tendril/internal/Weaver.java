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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 *     try {
 *         ... original body, with every "return x" turned into
 *         return (int) Bridge.exit(ctx, x);
 *     } catch (Throwable t) {
 *         t = Bridge.thrown(ctx, t);
 *         if (t != null) throw t;
 *         return (int) Bridge.result(ctx);         // a hook swallowed it
 *     }
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

    private static final Method THROWN = new Method("thrown",
            "(Ljava/lang/Object;Ljava/lang/Throwable;)Ljava/lang/Throwable;");
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
     * Every method that has a body, keyed by name + descriptor, with the instructions
     * in it that a hook could be attached to, in order. The position in that list is
     * how an instruction is identified, so {@link HookAdapter} must count exactly the
     * same instructions.
     */
    static Map<String, List<CallRef>> scan(ClassReader reader) {
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);
        Map<String, List<CallRef>> result = new LinkedHashMap<>();
        for (MethodNode method : node.methods) {
            if ((method.access & NOT_HOOKABLE) != 0 || method.name.equals("<clinit>")) {
                continue;
            }
            result.put(method.name + method.desc, scan(method));
        }
        return result;
    }

    private static List<CallRef> scan(MethodNode method) {
        List<CallRef> found = new ArrayList<>();
        // In a constructor nothing can be wrapped until super(...) or this(...) has
        // returned: before that "this" is not an object yet and the woven head code,
        // which sets up the context, has not run.
        boolean ready = !method.name.equals("<init>");
        // One entry per "new" whose constructor call has not been seen yet; the value
        // says whether the usual "dup" follows, which leaves the new object on the stack.
        Deque<Boolean> pendingNew = new ArrayDeque<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn.getOpcode() == Opcodes.NEW) {
                AbstractInsnNode next = insn.getNext();
                while (next != null && next.getOpcode() < 0) {
                    next = next.getNext();
                }
                pendingNew.push(next != null && next.getOpcode() == Opcodes.DUP);
            } else if (insn instanceof FieldInsnNode field) {
                boolean read = field.getOpcode() == Opcodes.GETFIELD || field.getOpcode() == Opcodes.GETSTATIC;
                found.add(new CallRef(read ? CallRef.Kind.FIELD_READ : CallRef.Kind.FIELD_WRITE, field.owner,
                        field.name, read ? "()" + field.desc : "(" + field.desc + ")V", ready));
            } else if (insn instanceof MethodInsnNode call) {
                if (!call.name.equals("<init>")) {
                    found.add(new CallRef(CallRef.Kind.CALL, call.owner, call.name, call.desc, ready));
                } else if (!pendingNew.isEmpty()) {
                    boolean duplicated = pendingNew.pop();
                    found.add(new CallRef(CallRef.Kind.NEW, call.owner, call.name, call.desc, ready && duplicated));
                } else {
                    // The super(...) or this(...) call of a constructor.
                    found.add(new CallRef(CallRef.Kind.CALL, call.owner, call.name, call.desc, false));
                    ready = true;
                }
            }
        }
        return found;
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
        private final int instructionCount;
        private int callIndex;
        private Label bodyStart;

        HookAdapter(MethodVisitor mv, int access, String name, String desc, ClassPlan.Weave weave, Type owner) {
            super(ASM_API, mv, access, name, desc);
            this.siteId = weave.site().id;
            this.points = weave.points();
            this.instructionCount = weave.instructionCount();
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

            // Everything from here on is covered by the catch-all added in visitMaxs.
            bodyStart = mark();
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            if (callIndex != instructionCount) {
                throw new IllegalStateException("instruction count changed between scan and weave: "
                        + instructionCount + " then " + callIndex);
            }
            if (bodyStart != null) {
                // catch (Throwable t) {
                //     t = Bridge.thrown(ctx, t);
                //     if (t != null) throw t;
                //     return (T) Bridge.result(ctx);      // a hook swallowed it
                // }
                // Declared last, so the method's own handlers keep priority.
                Label bodyEnd = mark();
                catchException(bodyStart, bodyEnd, null);
                loadLocal(contextLocal);
                swap();
                invokeStatic(BRIDGE, THROWN);
                dup();
                Label swallowed = newLabel();
                ifNull(swallowed);
                mv.visitInsn(ATHROW);
                mark(swallowed);
                pop();
                if (returnType.getSort() != Type.VOID) {
                    loadLocal(contextLocal);
                    invokeStatic(BRIDGE, RESULT);
                    unbox(returnType);
                }
                mv.visitInsn(returnType.getOpcode(IRETURN));
            }
            super.visitMaxs(maxStack, maxLocals);
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
            if (point == null || bodyStart == null) {
                super.visitMethodInsn(opcode, calleeOwner, name, desc, isInterface);
                return;
            }
            boolean isNew = point.call.kind() == CallRef.Kind.NEW;
            Type[] parameters = Type.getArgumentTypes(desc);
            Type result = isNew ? Type.getObjectType(calleeOwner) : Type.getReturnType(desc);
            boolean hasReceiver = opcode != INVOKESTATIC && !isNew;
            wrap(point, hasReceiver ? Type.getObjectType(calleeOwner) : null, parameters, result, isNew,
                    () -> mv.visitMethodInsn(opcode, calleeOwner, name, desc, isInterface));
        }

        @Override
        public void visitFieldInsn(int opcode, String fieldOwner, String name, String desc) {
            InvokePoint point = points.get(callIndex++);
            if (point == null || bodyStart == null) {
                super.visitFieldInsn(opcode, fieldOwner, name, desc);
                return;
            }
            // A read is wrapped like a call with no arguments that returns the value,
            // a write like a call that takes the value and returns nothing.
            boolean read = opcode == GETFIELD || opcode == GETSTATIC;
            boolean isStatic = opcode == GETSTATIC || opcode == PUTSTATIC;
            Type type = Type.getType(desc);
            wrap(point, isStatic ? null : Type.getObjectType(fieldOwner),
                    read ? new Type[0] : new Type[] {type}, read ? type : Type.VOID_TYPE, false,
                    () -> mv.visitFieldInsn(opcode, fieldOwner, name, desc));
        }

        /**
         * Emits the wrapper around one instruction. On entry the receiver (if any) and
         * the operands are on the stack, exactly as the instruction expects them.
         *
         * @param receiver type of the object operated on, or null if there is none
         * @param isNew    the instruction is the constructor call of a {@code new}
         *                 expression: two references to the uninitialised object sit
         *                 below the arguments, and one initialised reference is left
         */
        private void wrap(InvokePoint point, Type receiver, Type[] parameters, Type result, boolean isNew,
                          Runnable original) {
            boolean hasReceiver = receiver != null;

            // The receiver and arguments are on the stack. Move them into locals.
            // Typed locals keep the receiver's exact type for the verifier.
            int[] argumentLocals = new int[parameters.length];
            for (int i = parameters.length - 1; i >= 0; i--) {
                argumentLocals[i] = newLocal(parameters[i]);
                storeLocal(argumentLocals[i]);
            }
            int receiverLocal = -1;
            if (hasReceiver) {
                receiverLocal = newLocal(receiver);
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
            if (isNew) {
                // Drop the two references to the object that will now never be constructed.
                mv.visitInsn(POP2);
            }
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
            original.run();
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
