package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Head and return hooks on a class that is already loaded and has already run. */
class HookTest {

    private final PlayerService player = new PlayerService();

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    // ---- hooks fire --------------------------------------------------------

    @Test
    void headHookFiresBeforeTheBody() {
        List<Integer> healthSeen = new ArrayList<>();
        Injector.into(PlayerService.class).method("damage")
                .atHead(call -> healthSeen.add(call.<PlayerService>self().health()));

        player.damage(10);
        player.damage(5);

        assertEquals(List.of(100, 90), healthSeen);
        assertEquals(85, player.health());
    }

    @Test
    void returnHookFiresAfterTheBodyAndSeesTheResult() {
        List<Object> results = new ArrayList<>();
        Injector.into(PlayerService.class).method("greet").atReturn(call -> results.add(call.getResult()));

        assertEquals("Hello, Ada", player.greet("Ada"));
        assertEquals(List.of("Hello, Ada"), results);
    }

    @Test
    void returnHookOnVoidMethodSeesTheEffectOfTheBody() {
        AtomicInteger healthAtReturn = new AtomicInteger();
        Injector.into(PlayerService.class).method("damage").atReturn(call -> {
            assertNull(call.getResult());
            healthAtReturn.set(call.<PlayerService>self().health());
        });

        player.damage(30);

        assertEquals(70, healthAtReturn.get());
    }

    @Test
    void returnHookFiresAtEveryReturnInstruction() {
        List<Object> results = new ArrayList<>();
        Injector.into(PlayerService.class).method("classify").atReturn(call -> results.add(call.getResult()));

        assertEquals("negative", player.classify(-1));
        assertEquals("zero", player.classify(0));
        assertEquals("one", player.classify(1));
        assertEquals("small", player.classify(3));
        assertEquals("big", player.classify(9));

        assertEquals(List.of("negative", "zero", "one", "small", "big"), results);
    }

    @Test
    void callInfoDescribesTheCall() {
        Injector.into(PlayerService.class).method("add").atHead(call -> {
            assertEquals(At.HEAD, call.at());
            assertSame(PlayerService.class, call.owner());
            assertEquals("add", call.methodName());
            assertEquals("(II)I", call.descriptor());
            assertSame(player, call.self());
            assertEquals(2, call.argCount());
            assertArrayEquals(new Object[] {2, 3}, call.args());
            assertFalse(call.isCancelled());
        });
        Injector.into(PlayerService.class).method("add").atReturn(call -> assertEquals(At.RETURN, call.at()));

        assertEquals(5, player.add(2, 3));
    }

    @Test
    void staticMethodsHaveNoSelf() {
        List<Object> selves = new ArrayList<>();
        Injector.into(PlayerService.class).method("twice").atHead(call -> {
            selves.add(call.self());
            call.setArg(0, 21);
        });

        assertEquals(42, PlayerService.twice(1));
        assertEquals(1, selves.size());
        assertNull(selves.get(0));
    }

    @Test
    void severalHooksRunInRegistrationOrder() {
        List<String> order = new ArrayList<>();
        Injector.into(PlayerService.class).method("greet").atHead(call -> order.add("head 1"));
        Injector.into(PlayerService.class).method("greet").atReturn(call -> order.add("return 1"));
        Injector.into(PlayerService.class).method("greet").atHead(call -> order.add("head 2"));
        Injector.into(PlayerService.class).method("greet").atReturn(call -> order.add("return 2"));

        player.greet("x");

        assertEquals(List.of("head 1", "head 2", "return 1", "return 2"), order);
    }

    // ---- cancel ------------------------------------------------------------

    @Test
    void cancelSkipsAVoidMethod() {
        Injector.into(PlayerService.class).method("damage", int.class).atHead(call -> {
            if (call.<Integer>arg(0) > 100) {
                call.cancel();
            }
        });

        player.damage(500);
        assertEquals(100, player.health());
        player.damage(40);
        assertEquals(60, player.health());
    }

    @Test
    void cancelWithAnObjectResult() {
        Injector.into(PlayerService.class).method("greet").atHead(call -> call.cancel("Go away"));

        assertEquals("Go away", player.greet("Ada"));
    }

    @Test
    void cancelWithPrimitiveResults() {
        Injector.into(PlayerService.class).method("add").atHead(call -> call.cancel(-1));
        Injector.into(PlayerService.class).method("big").atHead(call -> call.cancel(Long.MAX_VALUE));
        Injector.into(PlayerService.class).method("half").atHead(call -> call.cancel(1.25));
        Injector.into(PlayerService.class).method("ratio").atHead(call -> call.cancel(9.5f));
        Injector.into(PlayerService.class).method("isAlive").atHead(call -> call.cancel(false));
        Injector.into(PlayerService.class).method("initial").atHead(call -> call.cancel('Z'));
        Injector.into(PlayerService.class).method("lowByte").atHead(call -> call.cancel((byte) 7));
        Injector.into(PlayerService.class).method("lowShort").atHead(call -> call.cancel((short) 8));
        Injector.into(PlayerService.class).method("range").atHead(call -> call.cancel(new int[] {9}));

        assertEquals(-1, player.add(2, 3));
        assertEquals(Long.MAX_VALUE, player.big());
        assertEquals(1.25, player.half(100));
        assertEquals(9.5f, player.ratio());
        assertFalse(player.isAlive());
        assertEquals('Z', player.initial("Ada"));
        assertEquals(7, player.lowByte(300));
        assertEquals(8, player.lowShort(70000));
        assertArrayEquals(new int[] {9}, player.range(3));
    }

    @Test
    void plainCancelOnNonVoidMethodsReturnsZeroValues() {
        for (String method : List.of("greet", "add", "big", "half", "isAlive", "initial", "range")) {
            Injector.into(PlayerService.class).method(method).atHead(CallInfo::cancel);
        }

        assertNull(player.greet("Ada"));
        assertEquals(0, player.add(2, 3));
        assertEquals(0L, player.big());
        assertEquals(0.0, player.half(3));
        assertFalse(player.isAlive());
        assertEquals('\0', player.initial("Ada"));
        assertNull(player.range(3));
    }

    @Test
    void cancelSkipsLaterHeadHooksAndAllReturnHooks() {
        List<String> ran = new ArrayList<>();
        Injector.into(PlayerService.class).method("greet").atHead(call -> {
            ran.add("first");
            call.cancel("cancelled");
        });
        Injector.into(PlayerService.class).method("greet").atHead(call -> ran.add("second"));
        Injector.into(PlayerService.class).method("greet").atReturn(call -> ran.add("return"));

        assertEquals("cancelled", player.greet("Ada"));
        assertEquals(List.of("first"), ran);
    }

    // ---- return value override ---------------------------------------------

    @Test
    void returnHookReplacesAnObjectResult() {
        Injector.into(PlayerService.class).method("greet")
                .atReturn(call -> call.setResult(call.getResult() + "!"));

        assertEquals("Hello, Ada!", player.greet("Ada"));
    }

    @Test
    void returnHookReplacesPrimitiveResults() {
        Injector.into(PlayerService.class).method("add").atReturn(call -> call.setResult((int) call.getResult() * 10));
        Injector.into(PlayerService.class).method("big").atReturn(call -> call.setResult(5L));
        Injector.into(PlayerService.class).method("half").atReturn(call -> call.setResult((double) call.getResult() + 0.5));
        Injector.into(PlayerService.class).method("isAlive").atReturn(call -> call.setResult(!(boolean) call.getResult()));
        Injector.into(PlayerService.class).method("initial").atReturn(call -> call.setResult('?'));

        assertEquals(50, player.add(2, 3));
        assertEquals(5L, player.big());
        assertEquals(2.5, player.half(4));
        assertFalse(player.isAlive());
        assertEquals('?', player.initial("Ada"));
    }

    @Test
    void numericResultsAreConvertedToTheReturnType() {
        // An Integer where a long is expected, as Kotlin or a careless caller would pass.
        Injector.into(PlayerService.class).method("big").atReturn(call -> call.setResult(3));
        Injector.into(PlayerService.class).method("half").atHead(call -> call.cancel(2));

        assertEquals(3L, player.big());
        assertEquals(2.0, player.half(100));
    }

    // ---- arguments ---------------------------------------------------------

    @Test
    void headHookChangesArguments() {
        Injector.into(PlayerService.class).method("greet").atHead(call -> call.setArg(0, "Grace"));
        Injector.into(PlayerService.class).method("add").atHead(call -> call.setArg(1, call.<Integer>arg(1) * 100));

        assertEquals("Hello, Grace", player.greet("Ada"));
        assertEquals(302, player.add(2, 3));
    }

    @Test
    void headHookChangesWideArguments() {
        Injector.into(PlayerService.class).method("mix").atHead(call -> {
            assertArrayEquals(new Object[] {1L, 2.0, 3, "four"}, call.args());
            call.setArg(0, 1000L);
            call.setArg(1, 200.9);
            call.setArg(2, 30);
            call.setArg(3, "x");
        });

        assertEquals(1000 + 200 + 30 + 1, player.mix(1L, 2.0, 3, "four"));
    }

    @Test
    void untouchedArgumentsSurviveTheRoundTrip() {
        Injector.into(PlayerService.class).method("mix").atHead(call -> { });

        assertEquals(1 + 2 + 3 + 4, player.mix(1L, 2.9, 3, "four"));
    }

    // ---- overloads ---------------------------------------------------------

    @Test
    void nameAloneMatchesEveryOverload() {
        AtomicInteger calls = new AtomicInteger();
        Injector.into(PlayerService.class).method("describe").atHead(call -> calls.incrementAndGet());

        player.describe();
        player.describe(1);
        player.describe("s");

        assertEquals(3, calls.get());
    }

    @Test
    void parameterTypesSelectOneOverload() {
        Injector.into(PlayerService.class).method("describe", int.class).atHead(call -> call.cancel("hooked int"));

        assertEquals("nothing", player.describe());
        assertEquals("hooked int", player.describe(1));
        assertEquals("string s", player.describe("s"));
    }

    @Test
    void descriptorSelectsOneOverload() {
        Injector.into(PlayerService.class).methodDescriptor("describe", "()")
                .atHead(call -> call.cancel("hooked none"));
        Injector.into(PlayerService.class).methodDescriptor("describe", "(Ljava/lang/String;)Ljava/lang/String;")
                .atHead(call -> call.cancel("hooked string"));

        assertEquals("hooked none", player.describe());
        assertEquals("int 1", player.describe(1));
        assertEquals("hooked string", player.describe("s"));
    }

    // ---- awkward method shapes ---------------------------------------------

    @Test
    void recursionIsHookedAtEveryLevel() {
        AtomicInteger calls = new AtomicInteger();
        Injector.into(PlayerService.class).method("factorial").atHead(call -> calls.incrementAndGet());

        assertEquals(120, player.factorial(5));
        assertEquals(5, calls.get());
    }

    @Test
    void hookCallingItsOwnTargetDoesNotRecurseForever() {
        AtomicInteger calls = new AtomicInteger();
        Injector.into(PlayerService.class).method("factorial").atHead(call -> {
            calls.incrementAndGet();
            // Runs the real method, without hooks, because we are inside its hook.
            assertEquals(6, call.<PlayerService>self().factorial(3));
        });

        assertEquals(24, player.factorial(4));
        assertEquals(4, calls.get());
    }

    @Test
    void synchronizedMethodAndFinallyBlockStillWork() {
        Injector.into(PlayerService.class).method("guarded").atReturn(call -> call.setResult(0));
        Injector.into(PlayerService.class).method("classify").atHead(call -> call.setArg(0, 0));

        assertEquals(0, player.guarded(5));
        int before = player.health();
        assertEquals("zero", player.classify(99));
        assertEquals(before + 1, player.health(), "the finally block of the original method must still run");
    }

    @Test
    void methodWithBranchesOfDifferentTypesStillVerifies() {
        Injector.into(PlayerService.class).method("pick").atReturn(call -> { });

        assertEquals(1, player.pick(true, 1, "b"));
        assertEquals("b", player.pick(false, 1, "b"));
    }

    // ---- exceptions --------------------------------------------------------

    @Test
    void exceptionFromAHookPropagatesToTheCaller() {
        Injector.into(PlayerService.class).method("damage")
                .atHead(call -> { throw new IllegalStateException("no"); });

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> player.damage(1));
        assertEquals("no", thrown.getMessage());
        assertEquals(100, player.health());
    }

    @Test
    void returnHookDoesNotRunWhenTheMethodThrows() {
        AtomicInteger returns = new AtomicInteger();
        Injector.into(PlayerService.class).method("parse").atReturn(call -> returns.incrementAndGet());

        assertThrows(NumberFormatException.class, () -> player.parse("nope"));
        assertEquals(12, player.parse("12"));
        assertEquals(1, returns.get());
    }

    // ---- misuse is reported in the hook ------------------------------------

    @Test
    void wrongTypesAreRejectedWithAReadableError() {
        List<Throwable> errors = new ArrayList<>();
        Injector.into(PlayerService.class).method("add").atHead(call -> {
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.setArg(0, "text")));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.setArg(0, null)));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.setResult("text")));
            errors.add(assertThrows(IndexOutOfBoundsException.class, () -> call.arg(2)));
        });
        Injector.into(PlayerService.class).method("greet").atHead(call -> {
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.setArg(0, 42)));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.setResult(42)));
        });
        Injector.into(PlayerService.class).method("damage").atHead(call ->
                errors.add(assertThrows(IllegalStateException.class, () -> call.setResult(1))));
        Injector.into(PlayerService.class).method("damage").atReturn(call ->
                errors.add(assertThrows(IllegalStateException.class, call::cancel)));

        assertEquals(5, player.add(2, 3));
        assertEquals("Hello, Ada", player.greet("Ada"));
        player.damage(1);

        assertEquals(8, errors.size());
        assertTrue(errors.get(0).getMessage().contains("java.lang.String"), errors.get(0).getMessage());
    }
}
