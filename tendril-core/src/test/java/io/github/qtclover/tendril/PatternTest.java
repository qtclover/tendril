package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.Shapes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hooks on every subtype of a class, or every class matching a name pattern.
 * The zoo classes are loaded one by one as the tests reach them; the tests are
 * written so that they pass in any order.
 */
class PatternTest {

    private static final String ZOO = "io.github.qtclover.tendril.fixtures.zoo.";

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    private static String call(String simpleName, String method) throws Exception {
        Object instance = Class.forName(ZOO + simpleName).getDeclaredConstructor().newInstance();
        return (String) instance.getClass().getMethod(method).invoke(instance);
    }

    @Test
    void subtypesAreHookedAsTheyLoadAndThoseAlreadyLoaded() throws Exception {
        Injection hook = Injector.intoSubtypes(ZOO + "Animal").method("sound")
                .atReturn(call -> call.setResult(call.getResult() + "!"));

        assertEquals("woof!", call("Dog", "sound"));
        // Puppy does not declare sound(); it runs Dog's hooked one.
        assertEquals("woof!", call("Puppy", "sound"));
        assertEquals("puppy", call("Puppy", "name"));
        // Not an Animal.
        assertEquals("silence", call("Rock", "sound"));

        hook.remove();
        assertEquals("woof", call("Dog", "sound"));
        assertEquals("woof", call("Puppy", "sound"));

        // Dog is loaded now. A new subtype hook must find it.
        AtomicInteger calls = new AtomicInteger();
        Injector.intoSubtypes(ZOO + "Animal").method("sound").atHead(call -> calls.incrementAndGet());
        call("Dog", "sound");
        call("Puppy", "sound");
        assertEquals(2, calls.get());
    }

    @Test
    void namePatternsMatchByName() throws Exception {
        Injection hook = Injector.intoMatching(ZOO + "*").method("sound").atHead(call -> call.cancel("hooked"));

        assertEquals("hooked", call("Dog", "sound"));
        assertEquals("hooked", call("Rock", "sound"), "a name pattern does not care about the hierarchy");
        // The default method in the interface calls the hooked sound().
        assertEquals("says hooked", call("Dog", "describe"));

        hook.remove();
        assertEquals("woof", call("Dog", "sound"));
        assertEquals("silence", call("Rock", "sound"));
    }

    @Test
    void removeAllStopsPatternsFromApplyingToLaterClasses() throws Exception {
        Injector.intoSubtypes(ZOO + "Animal").method("sound").atHead(call -> call.cancel("hooked"));
        Injector.removeAll();

        assertFalse(TestSupport.isLoaded(ZOO + "Cat"), "Cat is reserved for this test");
        assertEquals("meow", call("Cat", "sound"));
    }

    @Test
    void loadedSubtypesOfALoadedClassViaTheClassOverloadAndAnnotations() {
        Shapes.Square square = new Shapes.Square(3);
        Injection hook = Injector.intoSubtypes(Shapes.Shape.class).method("area")
                .atReturn(call -> call.setResult(1.0));
        // Shape.area is abstract and Base does not declare it: both skipped without complaint.
        assertEquals(1.0, square.area());
        hook.remove();
        assertEquals(9.0, square.area());

        Injection annotated = Injector.register(Hooks.class);
        assertEquals(-1, square.sides());
        assertEquals("hooked", square.name());
        annotated.remove();
        assertEquals(4, square.sides());
        assertEquals("base", square.name());
    }

    static class Hooks {
        @Inject(target = Shapes.Base.class, subtypes = true, method = "sides", at = At.RETURN)
        static void sides(CallInfo call) {
            call.setResult(-1);
        }

        @Inject(targetName = "io.github.qtclover.tendril.fixtures.Shapes$*", method = "name", at = At.RETURN)
        static void name(CallInfo call) {
            call.setResult("hooked");
        }
    }

    @Test
    void jdkClassesAndTendrilItselfNeverMatchAPattern() {
        Injection everything = Injector.intoMatching("*").method("toString").atHead(call -> call.cancel("hooked"));
        Injection lists = Injector.intoSubtypes(List.class).method("size").atHead(call -> call.cancel(99));

        assertEquals("[1]", List.of(1).toString());
        assertEquals(1, new java.util.ArrayList<>(List.of(1)).size());
        assertEquals("HEAD", At.HEAD.toString());

        everything.remove();
        lists.remove();
        assertThrows(InjectionException.class, () -> Injector.intoMatching("no.wildcard.Here"));
    }
}
