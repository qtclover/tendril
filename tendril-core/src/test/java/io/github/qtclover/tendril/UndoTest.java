package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoTest {

    private final PlayerService player = new PlayerService();

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    @Test
    void removeRestoresOriginalBehaviour() {
        AtomicInteger calls = new AtomicInteger();
        Injection injection = Injector.into(PlayerService.class).method("greet").atHead(call -> {
            calls.incrementAndGet();
            call.cancel("hooked");
        });
        assertTrue(injection.isActive());
        assertEquals("hooked", player.greet("Ada"));

        injection.remove();

        assertFalse(injection.isActive());
        assertEquals("Hello, Ada", player.greet("Ada"));
        assertEquals(1, calls.get());
    }

    @Test
    void removeRestoresTheOriginalBytecodeExactly() throws Exception {
        player.greet("warm up");
        byte[] original = TestSupport.currentBytes(PlayerService.class);

        Injection injection = Injector.into(PlayerService.class).method("greet").atHead(call -> { });
        byte[] woven = TestSupport.currentBytes(PlayerService.class);
        assertFalse(java.util.Arrays.equals(original, woven), "the class should have been rewritten");

        injection.remove();

        assertArrayEquals(original, TestSupport.currentBytes(PlayerService.class));
    }

    @Test
    void removingOneHookLeavesTheOthers() throws Exception {
        Injection first = Injector.into(PlayerService.class).method("add").atReturn(call -> call.setResult(1));
        Injection second = Injector.into(PlayerService.class).method("greet").atReturn(call -> call.setResult("two"));
        Injection third = Injector.into(PlayerService.class).method("greet").atHead(call -> call.setArg(0, "three"));

        first.remove();
        assertEquals(5, player.add(2, 3));
        assertEquals("two", player.greet("Ada"));

        second.remove();
        assertEquals("Hello, three", player.greet("Ada"));

        third.remove();
        assertEquals("Hello, Ada", player.greet("Ada"));
    }

    @Test
    void removeIsIdempotentAndWorksWithTryWithResources() {
        try (Injection injection = Injector.into(PlayerService.class).method("add").atHead(call -> call.cancel(0))) {
            assertEquals(0, player.add(2, 3));
            injection.remove();
            injection.remove();
        }
        assertEquals(5, player.add(2, 3));
    }

    @Test
    void aBatchIsRemovedAsOne() {
        Injection batch = Injector.batch()
                .add(PlayerService.class, "add", null, At.HEAD, call -> call.cancel(1))
                .add(PlayerService.class, "greet", "(Ljava/lang/String;)", At.RETURN, call -> call.setResult("batch"))
                .install();
        assertEquals(1, player.add(2, 3));
        assertEquals("batch", player.greet("Ada"));

        batch.remove();

        assertEquals(5, player.add(2, 3));
        assertEquals("Hello, Ada", player.greet("Ada"));
    }

    @Test
    void hooksCanBeReinstalledAfterRemoval() {
        for (int round = 0; round < 5; round++) {
            int value = round;
            Injection injection = Injector.into(PlayerService.class).method("add").atHead(call -> call.cancel(value));
            assertEquals(round, player.add(2, 3));
            injection.remove();
            assertEquals(5, player.add(2, 3));
        }
    }

    @Test
    void hooksCanChangeWhileOtherThreadsAreCalling() throws Exception {
        AtomicInteger unexpected = new AtomicInteger();
        AtomicInteger iterations = new AtomicInteger();
        Thread[] callers = new Thread[4];
        for (int i = 0; i < callers.length; i++) {
            callers[i] = new Thread(() -> {
                PlayerService own = new PlayerService();
                while (!Thread.currentThread().isInterrupted()) {
                    int result = own.add(2, 3);
                    if (result != 5 && result != 100) {
                        unexpected.incrementAndGet();
                    }
                    iterations.incrementAndGet();
                }
            });
            callers[i].start();
        }
        try {
            for (int round = 0; round < 40; round++) {
                Injection injection = Injector.into(PlayerService.class).method("add")
                        .atReturn(call -> call.setResult(100));
                assertEquals(100, player.add(2, 3));
                injection.remove();
                assertEquals(5, player.add(2, 3));
            }
        } finally {
            for (Thread caller : callers) {
                caller.interrupt();
            }
            for (Thread caller : callers) {
                caller.join(5000);
            }
        }
        assertEquals(0, unexpected.get());
        assertTrue(iterations.get() > 0);
    }
}
