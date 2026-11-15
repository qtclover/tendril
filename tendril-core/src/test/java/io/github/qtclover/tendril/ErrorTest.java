package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.PlayerService;
import io.github.qtclover.tendril.fixtures.Shapes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorTest {

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    @Test
    void unknownMethodIsRejectedAndListsCandidates() {
        InjectionException e = assertThrows(InjectionException.class,
                () -> Injector.into(PlayerService.class).method("heal").atHead(call -> { }));

        assertTrue(e.getMessage().contains("PlayerService.heal"), e.getMessage());
        assertTrue(e.getMessage().contains("damage(I)V"), e.getMessage());
    }

    @Test
    void unknownOverloadIsRejected() {
        assertThrows(InjectionException.class,
                () -> Injector.into(PlayerService.class).method("describe", long.class).atHead(call -> { }));
    }

    @Test
    void aFailedBatchInstallsNothing() {
        PlayerService player = new PlayerService();

        assertThrows(InjectionException.class, () -> Injector.batch()
                .add(PlayerService.class, "add", null, At.HEAD, call -> call.cancel(-1))
                .add(PlayerService.class, "nope", null, At.HEAD, call -> { })
                .install());

        assertEquals(5, player.add(2, 3));
        // And the class is still perfectly hookable afterwards.
        Injector.into(PlayerService.class).method("add").atHead(call -> call.cancel(-2));
        assertEquals(-2, player.add(2, 3));
    }

    @Test
    void inheritedAndAbstractMethodsAreNotHookableOnTheSubclass() {
        new Shapes.Square(2);
        // name() is declared in Base, not Square.
        assertThrows(InjectionException.class,
                () -> Injector.into(Shapes.Square.class).method("name").atHead(call -> { }));
        // sides() is abstract in Base: no body to weave into.
        assertThrows(InjectionException.class,
                () -> Injector.into(Shapes.Base.class).method("sides").atHead(call -> { }));
    }

    @Test
    void hookingTheDeclaringClassAffectsSubclassesThatInherit() {
        Shapes.Square square = new Shapes.Square(2);
        Injector.into(Shapes.Base.class).method("name").atReturn(call -> call.setResult("hooked"));
        Injector.into(Shapes.Shape.class).method("label").atReturn(call -> call.setResult("default method hooked"));
        Injector.into(Shapes.Square.class).method("area").atReturn(call -> call.setResult(1.5));

        assertEquals("hooked", square.name());
        assertEquals("default method hooked", square.label());
        assertEquals(1.5, square.area());
    }

    @Test
    void staticInitialisersAndBadDescriptorsAreRejectedUpFront() {
        assertThrows(InjectionException.class,
                () -> Injector.into(PlayerService.class).method("<clinit>").atHead(call -> { }));
        assertThrows(InjectionException.class,
                () -> Injector.into(PlayerService.class).methodDescriptor("add", "II").atHead(call -> { }));
        assertThrows(InjectionException.class,
                () -> Injector.into(PlayerService.class).methodDescriptor("add", "(II").atHead(call -> { }));
    }

    @Test
    void tendrilRefusesToHookItselfAndItsDispatchPath() {
        assertThrows(InjectionException.class,
                () -> Injector.into(Injector.class).method("attach").atHead(call -> { }));
        assertThrows(InjectionException.class,
                () -> Injector.into(ThreadLocal.class).method("get").atHead(call -> { }));
    }
}
