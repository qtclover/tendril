package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.PlayerService;
import io.github.qtclover.tendril.fixtures.Shop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hooks on exceptions leaving a method. */
class ThrowTest {

    private final PlayerService player = new PlayerService();

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    @Test
    void throwHookSeesTheExceptionAndItStillPropagates() {
        List<Throwable> seen = new ArrayList<>();
        Injector.into(PlayerService.class).method("parse").atThrow(call -> {
            assertEquals(At.THROW, call.at());
            assertEquals("nope", call.arg(0));
            assertSame(player, call.self());
            seen.add(call.getThrown());
        });

        NumberFormatException thrown = assertThrows(NumberFormatException.class, () -> player.parse("nope"));
        assertEquals(List.of(thrown), seen);
        assertEquals(12, player.parse("12"));
        assertEquals(1, seen.size(), "no exception, no throw hook");
    }

    @Test
    void theExceptionCanBeReplaced() {
        Injector.into(PlayerService.class).method("parse")
                .atThrow(call -> call.setThrown(new IllegalStateException("wrapped", call.getThrown())));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> player.parse("nope"));
        assertTrue(thrown.getCause() instanceof NumberFormatException);
    }

    @Test
    void theExceptionCanBeSwallowedWithOrWithoutAResult() {
        Injection zero = Injector.into(PlayerService.class).method("parse").atThrow(CallInfo::swallow);
        assertEquals(0, player.parse("nope"));
        zero.remove();

        AtomicInteger returns = new AtomicInteger();
        Injector.into(PlayerService.class).method("parse").atThrow(call -> call.setResult(-1));
        Injector.into(PlayerService.class).method("parse").atReturn(call -> returns.incrementAndGet());
        assertEquals(-1, player.parse("nope"));
        assertEquals(0, returns.get(), "return hooks do not run for a swallowed exception");
        assertEquals(7, player.parse("7"));
        assertEquals(1, returns.get());
    }

    @Test
    void exceptionsTheMethodCatchesItselfAreNotSeen() {
        AtomicInteger thrown = new AtomicInteger();
        Injector.into(Shop.class).method("total").atThrow(call -> thrown.incrementAndGet());
        Injector.into(Shop.class).method("checked").atThrow(call -> thrown.addAndGet(100));

        // checked(-5) throws and total() catches it.
        assertEquals(2, new Shop().total(new int[] {1, 2, -5}));
        assertEquals(100, thrown.get());
    }

    @Test
    void exceptionsThrownByHooksAreNotSeen() {
        AtomicInteger thrown = new AtomicInteger();
        Injector.into(PlayerService.class).method("greet").atThrow(call -> thrown.incrementAndGet());
        Injection head = Injector.into(PlayerService.class).method("greet")
                .atHead(call -> { throw new IllegalStateException("from head hook"); });
        assertThrows(IllegalStateException.class, () -> player.greet("Ada"));
        head.remove();

        Injection ret = Injector.into(PlayerService.class).method("greet")
                .atReturn(call -> { throw new IllegalStateException("from return hook"); });
        assertThrows(IllegalStateException.class, () -> player.greet("Ada"));
        ret.remove();

        assertEquals(0, thrown.get());
        assertEquals("Hello, Ada", player.greet("Ada"));
    }

    @Test
    void voidMethodsAndLaterHooksSeeEarlierChanges() {
        List<String> order = new ArrayList<>();
        Injector.into(PlayerService.class).method("damage")
                .atHead(call -> { });
        Injector.into(PlayerService.class).method("explode").atThrow(call -> {
            order.add("first sees " + call.getThrown().getMessage());
            call.setThrown(new RuntimeException("second"));
        });
        Injector.into(PlayerService.class).method("explode").atThrow(call -> {
            order.add("second sees " + call.getThrown().getMessage());
            call.swallow();
            assertNull(call.getResult());
        });

        player.explode("first");
        assertEquals(List.of("first sees first", "second sees second"), order);
    }

    @Test
    void misuseIsReported() {
        List<Throwable> errors = new ArrayList<>();
        Injector.into(PlayerService.class).method("parse").atThrow(call -> {
            errors.add(assertThrows(IllegalStateException.class, call::cancel));
            errors.add(assertThrows(NullPointerException.class, () -> call.setThrown(null)));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.setResult("text")));
        });
        Injector.into(PlayerService.class).method("parse").atHead(call -> {
            errors.add(assertThrows(IllegalStateException.class, call::swallow));
            errors.add(assertThrows(IllegalStateException.class, () -> call.setThrown(new Error())));
            assertNull(call.getThrown());
        });

        assertThrows(NumberFormatException.class, () -> player.parse("nope"));
        assertEquals(5, errors.size());
    }

    @Test
    void undoRestoresTheBytecode() throws Exception {
        byte[] original = TestSupport.currentBytes(PlayerService.class);
        Injection hook = Injector.into(PlayerService.class).method("parse").atThrow(CallInfo::swallow);
        assertEquals(0, player.parse("nope"));

        hook.remove();
        assertThrows(NumberFormatException.class, () -> player.parse("nope"));
        assertArrayEquals(original, TestSupport.currentBytes(PlayerService.class));
    }
}
