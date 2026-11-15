package io.github.qtclover.tendril;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Class files from before Java 6 have no stack map frames, and before Java 5 no
 * class constants, so the weaver takes a different path for them.
 */
class OldClassFileTest {

    private static final String NAME = "generated.Java14Class";

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    /** {@code public static int sign(int x) { if (x < 0) return -1; return 1; }} as a Java 1.4 class. */
    private static byte[] java14Class() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_4, Opcodes.ACC_PUBLIC, NAME.replace('.', '/'), null, "java/lang/Object", null);
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "sign", "(I)I", null, null);
        mv.visitCode();
        Label positive = new Label();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitJumpInsn(Opcodes.IFGE, positive);
        mv.visitInsn(Opcodes.ICONST_M1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(positive);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void java14ClassIsHookedOnLoadAndRestored() throws Exception {
        byte[] bytes = java14Class();
        Injection head = Injector.into(NAME).method("sign").atHead(call -> {
            assertNull(call.owner(), "no class constant available before Java 5");
            call.setArg(0, -call.<Integer>arg(0));
        });
        Injection ret = Injector.into(NAME).method("sign").atReturn(call -> call.setResult((int) call.getResult() * 10));

        Class<?> type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() {
                return defineClass(NAME, bytes, 0, bytes.length);
            }
        }.define();
        Method sign = type.getMethod("sign", int.class);

        assertEquals(-10, sign.invoke(null, 5));
        assertEquals(10, sign.invoke(null, -5));

        head.remove();
        assertEquals(10, sign.invoke(null, 5));
        ret.remove();
        assertEquals(1, sign.invoke(null, 5));
        assertEquals(-1, sign.invoke(null, -5));
    }
}
