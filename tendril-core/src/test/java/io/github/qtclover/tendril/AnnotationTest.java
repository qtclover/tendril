package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationTest {

    private final PlayerService player = new PlayerService();

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
        StaticHooks.returned.clear();
        StaticHooks.plainCalls = 0;
    }

    static class StaticHooks {
        static final List<Object> returned = new ArrayList<>();
        static int plainCalls;

        @Inject(target = PlayerService.class, method = "damage", at = At.HEAD)
        static void onDamage(CallInfo call) {
            if (call.<Integer>arg(0) > 100) {
                call.cancel();
            }
        }

        @Inject(target = PlayerService.class, method = "greet", at = At.RETURN)
        private static void onGreet(CallInfo call) {
            returned.add(call.getResult());
            call.setResult("annotated");
        }

        @Inject(target = PlayerService.class, method = "describe", descriptor = "(I)")
        static void onDescribeInt(CallInfo call) {
            call.cancel("annotated int");
        }

        @Inject(target = PlayerService.class, method = "add")
        static void noParameters() {
            plainCalls++;
        }
    }

    @Test
    void staticAnnotatedMethodsAreInstalledAndRemovedTogether() {
        Injection hooks = Injector.register(StaticHooks.class);

        player.damage(500);
        player.damage(10);
        assertEquals(90, player.health());
        assertEquals("annotated", player.greet("Ada"));
        assertEquals(List.of("Hello, Ada"), StaticHooks.returned);
        assertEquals("annotated int", player.describe(1));
        assertEquals("string s", player.describe("s"));
        assertEquals(5, player.add(2, 3));
        assertEquals(1, StaticHooks.plainCalls);

        hooks.remove();

        assertFalse(hooks.isActive());
        player.damage(500);
        assertEquals(-410, player.health());
        assertEquals("Hello, Ada", player.greet("Ada"));
        assertEquals("int 1", player.describe(1));
    }

    static class InstanceHooks {
        int limit;
        int cancelled;

        InstanceHooks(int limit) {
            this.limit = limit;
        }

        @Inject(target = PlayerService.class, method = "damage")
        void capDamage(CallInfo call) {
            if (call.<Integer>arg(0) > limit) {
                call.setArg(0, limit);
                cancelled++;
            }
        }
    }

    @Test
    void instanceAnnotatedMethodsKeepTheirState() {
        InstanceHooks hooks = new InstanceHooks(25);
        Injector.register(hooks);

        player.damage(80);
        assertEquals(75, player.health());
        hooks.limit = 5;
        player.damage(80);
        assertEquals(70, player.health());
        assertEquals(2, hooks.cancelled);
    }

    static class LateHooks {
        @Inject(targetName = "io.github.qtclover.tendril.fixtures.LateAnnotated", method = "name", at = At.RETURN)
        static void rename(CallInfo call) {
            call.setResult(call.getResult() + ", but hooked");
        }
    }

    @Test
    void targetNameHooksAClassThatIsNotLoadedYet() throws Exception {
        String name = "io.github.qtclover.tendril.fixtures.LateAnnotated";
        assertFalse(TestSupport.isLoaded(name));
        Injection hooks = Injector.register(LateHooks.class);
        assertFalse(TestSupport.isLoaded(name));

        Object late = Class.forName(name).getDeclaredConstructor().newInstance();
        assertEquals("late, but hooked", late.getClass().getMethod("name").invoke(late));

        hooks.remove();
        assertEquals("late", late.getClass().getMethod("name").invoke(late));
    }

    static class NotStatic {
        @Inject(target = PlayerService.class, method = "add")
        void hook(CallInfo call) {
        }
    }

    static class NoTarget {
        @Inject(method = "add")
        static void hook(CallInfo call) {
        }
    }

    static class BadSignature {
        @Inject(target = PlayerService.class, method = "add")
        static void hook(String notACallInfo) {
        }
    }

    static class Empty {
    }

    @Test
    void mistakesInHookClassesAreReportedAtRegistration() {
        assertTrue(assertThrows(InjectionException.class, () -> Injector.register(NotStatic.class))
                .getMessage().contains("not static"));
        assertTrue(assertThrows(InjectionException.class, () -> Injector.register(NoTarget.class))
                .getMessage().contains("target"));
        assertTrue(assertThrows(InjectionException.class, () -> Injector.register(BadSignature.class))
                .getMessage().contains("CallInfo"));
        assertThrows(InjectionException.class, () -> Injector.register(Empty.class));
        // Nothing was half-installed along the way.
        assertEquals(5, player.add(2, 3));
    }
}
