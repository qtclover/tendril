package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.Ledger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading and changing the local variables of the surrounding method at an invoke or field point. */
class LocalsTest {

    private final Ledger ledger = new Ledger();

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    // settle(100, 3): total = 100 + 0 + 10 + 20 = 130, note = "start012"

    @Test
    void localsInScopeCanBeRead() {
        List<String> seen = new ArrayList<>();
        Injector.into(Ledger.class).method("settle").withLocals().atInvoke("fee", call -> {
            assertEquals(List.of("base", "count", "total", "note", "i"), call.localNames());
            seen.add(call.<Integer>local("i") + ":" + call.<Long>local("total") + ":" + call.local("note"));
            assertEquals(100L, call.<Long>local("base"));
        });
        Injector.into(Ledger.class).method("settle").withLocals().atInvoke("record",
                call -> assertEquals(List.of("base", "count", "total", "note"), call.localNames(),
                        "the loop variable is out of scope after the loop"));

        assertEquals(130, ledger.settle(100, 3));
        assertEquals(List.of("0:100:start", "1:100:start0", "2:110:start01"), seen);
    }

    @Test
    void localsCanBeChangedBeforeAndAfterACall() {
        Injector.into(Ledger.class).method("settle").withLocals().atInvoke("fee", call -> {
            if (call.<Integer>local("i") == 0) {
                call.setLocal("count", 2);          // the loop now runs twice instead of three times
            }
        });
        Injector.into(Ledger.class).method("settle").withLocals().afterInvoke("fee", call -> {
            if (call.<Integer>local("i") == 1) {
                call.setLocal("note", "cut");
            }
        });
        assertEquals(110, ledger.settle(100, 3));
        assertEquals("cut1", ledger.last);

        Injector.into(Ledger.class).method("settle").withLocals()
                .afterInvoke("record", call -> call.setLocal("total", 7));   // an Integer for a long variable
        assertEquals(7, ledger.settle(100, 3));
    }

    @Test
    void fieldPointsCanCaptureLocalsToo() {
        Injector.into(Ledger.class).method("fee").withLocals()
                .onFieldRead("rate", call -> call.invoked().setResult(call.<Integer>local("step") == 2 ? 100L : 1L));

        assertEquals(100 + 0 + 1 + 200, ledger.settle(100, 3));
    }

    @Test
    void addingALocalsHookToAnAlreadyHookedCallAndUndo() throws Exception {
        byte[] original = TestSupport.currentBytes(Ledger.class);
        Injection plain = Injector.into(Ledger.class).method("settle")
                .atInvoke("fee", call -> assertTrue(call.localNames().isEmpty()));
        assertEquals(130, ledger.settle(100, 3));

        Injection withLocals = Injector.into(Ledger.class).method("settle").withLocals()
                .atInvoke("fee", call -> call.setLocal("count", 1));
        assertEquals(100, ledger.settle(100, 3));

        withLocals.remove();
        assertEquals(130, ledger.settle(100, 3));
        plain.remove();
        assertArrayEquals(original, TestSupport.currentBytes(Ledger.class));
    }

    @Test
    void misuseIsReported() {
        List<Throwable> errors = new ArrayList<>();
        Injector.into(Ledger.class).method("settle").atInvoke("record", call ->
                errors.add(assertThrows(IllegalStateException.class, () -> call.local("total"))));
        Injector.into(Ledger.class).method("settle").withLocals().atInvoke("record", call -> {
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.local("i")));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.setLocal("total", "text")));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.setLocal("note", 5)));
        });
        Injector.into(Ledger.class).method("settle").atHead(call ->
                errors.add(assertThrows(IllegalStateException.class, () -> call.local("base"))));

        assertEquals(130, ledger.settle(100, 3));
        assertEquals(5, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("[base, count, total, note]")),
                errors.toString());

        assertThrows(InjectionException.class, () -> Injector.batch()
                .add(Ledger.class.getName(), "settle", null, At.HEAD, null, -1, true, call -> { }).install());
    }

    @Test
    void aClassWithoutDebugInformationIsRejected() throws Exception {
        // static int twice(int x) { return Math.abs(x) * 2; }  with no LocalVariableTable
        String name = "generated.NoDebugInfo";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "twice", "(I)I", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false);
        mv.visitInsn(Opcodes.ICONST_2);
        mv.visitInsn(Opcodes.IMUL);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() {
                return defineClass(name, bytes, 0, bytes.length);
            }
        }.define();
        assertEquals(6, type.getMethod("twice", int.class).invoke(null, -3));

        InjectionException e = assertThrows(InjectionException.class,
                () -> Injector.into(name).method("twice").withLocals().atInvoke("Math.abs", call -> { }));
        assertTrue(e.getMessage().contains("local variable table"), e.getMessage());

        // Without locals the same hook is fine.
        Injector.into(name).method("twice").atInvoke("Math.abs", call -> call.invoked().skip(10));
        assertEquals(20, type.getMethod("twice", int.class).invoke(null, -3));
    }
}
