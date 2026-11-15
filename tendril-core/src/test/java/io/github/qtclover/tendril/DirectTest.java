package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.DirectHooks;
import io.github.qtclover.tendril.fixtures.PlayerService;
import io.github.qtclover.tendril.fixtures.Widgets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hooks called straight from the target method, without the dispatcher. */
class DirectTest {

    private final PlayerService player = new PlayerService();

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
        DirectHooks.seen.clear();
    }

    @Test
    void headAndReturnHooksReceiveTheRealArguments() {
        Injector.into(PlayerService.class).method("damage").direct(At.HEAD, DirectHooks.class, "onDamage");
        Injector.into(PlayerService.class).method("add").direct(At.RETURN, DirectHooks.class, "afterAdd");
        Injector.into(PlayerService.class).method("mix").direct(At.RETURN, DirectHooks.class, "afterMix");
        Injector.into(PlayerService.class).method("twice").direct(At.HEAD, DirectHooks.class, "onTwice");
        Injector.into(PlayerService.class).method("greet").direct(At.RETURN, DirectHooks.class, "afterGreet");

        player.damage(10);
        assertEquals(5, player.add(2, 3));
        assertEquals(10, player.mix(1L, 2.0, 3, "four"));
        assertEquals(14, PlayerService.twice(7));
        assertEquals("Hello, Ada", player.greet("Ada"));

        assertEquals(List.of("damage 10 at health 100", "2+3=5", "mix 1 2.0 3 four -> 10", "twice 7",
                "greet -> Hello, Ada"), DirectHooks.seen);
    }

    @Test
    void theOverloadFollowsFromTheHookParameters() {
        Injector.into(PlayerService.class).method("describe").direct(At.HEAD, DirectHooks.class, "describeString");

        player.describe();
        player.describe(1);
        player.describe("s");
        assertEquals(List.of("describe string s"), DirectHooks.seen);
    }

    @Test
    void directHooksCombineWithOrdinaryHooksAndSeeTheirChanges() throws Exception {
        byte[] original = TestSupport.currentBytes(PlayerService.class);
        Injection ordinary = Injector.into(PlayerService.class).method("add").atHead(call -> call.setArg(0, 40));
        Injection changed = Injector.into(PlayerService.class).method("add").atReturn(call -> call.setResult(0));
        Injection direct = Injector.into(PlayerService.class).method("add")
                .direct(At.RETURN, DirectHooks.class, "afterAdd");

        // Head hooks run first, so the direct hook sees 40; it runs before return hooks, so it sees 43.
        assertEquals(0, player.add(2, 3));
        assertEquals(List.of("40+3=43"), DirectHooks.seen);

        direct.remove();
        assertEquals(0, player.add(2, 3));
        assertEquals(1, DirectHooks.seen.size(), "a removed direct hook is gone from the bytecode");

        ordinary.remove();
        changed.remove();
        assertEquals(5, player.add(2, 3));
        assertArrayEquals(original, TestSupport.currentBytes(PlayerService.class));
    }

    @Test
    void constructorsAndAnnotations() {
        Injector.into(Widgets.Widget.class).constructor().direct(At.HEAD, DirectHooks.class, "onWidget");
        Injection annotated = Injector.register(DirectHooks.Annotated.class);

        new Widgets.Widget(6);
        player.damage(1);
        assertTrue(player.isAlive());
        assertEquals(List.of("widget 6 tag size 6", "annotated damage 1", "alive true"), DirectHooks.seen);

        annotated.remove();
        player.damage(1);
        assertEquals(3, DirectHooks.seen.size());
    }

    @Test
    void mistakesAreReportedBeforeAnythingIsWoven() {
        ClassSelector target = Injector.into(PlayerService.class);
        assertContains("fit no method", () -> target.method("damage").direct(At.HEAD, DirectHooks.class, "wrongShape"));
        assertContains("fit no method", () -> target.method("add").direct(At.HEAD, DirectHooks.class, "afterAdd"));
        assertContains("static void", () -> target.method("damage").direct(At.HEAD, DirectHooks.class, "returnsSomething"));
        assertContains("public", () -> target.method("damage").direct(At.HEAD, DirectHooks.class, "notPublic"));
        assertContains("public class", () -> target.method("damage").direct(At.HEAD, DirectTest.class, "hidden"));
        assertContains("HEAD and RETURN", () -> target.method("damage").direct(At.THROW, DirectHooks.class, "onDamage"));
        assertContains("no method named", () -> target.method("damage").direct(At.HEAD, DirectHooks.class, "nope"));
        // A JDK class is loaded by the bootstrap loader and cannot see the hook class.
        assertContains("cannot see", () -> Injector.into(Year.class).method("isLeap")
                .direct(At.HEAD, DirectHooks.class, "onTwice"));

        player.damage(1);
        assertTrue(DirectHooks.seen.isEmpty());
    }

    public static void hidden(PlayerService self, int amount) {
    }

    private static void assertContains(String expected, Runnable action) {
        InjectionException e = assertThrows(InjectionException.class, action::run);
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }
}
