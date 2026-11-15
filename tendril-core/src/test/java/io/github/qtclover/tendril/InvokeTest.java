package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.Shop;
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

/** Hooks on a particular call inside a method. */
class InvokeTest {

    private final Shop shop = new Shop();

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    // "tea" costs 3 * 10 * quantity; shipping is 5 up to a price of 100, then free.

    @Test
    void hookBeforeACallSeesTheCallAndTheSurroundingMethod() {
        AtomicInteger calls = new AtomicInteger();
        Injector.into(Shop.class).method("checkout").atInvoke("priceOf", call -> {
            calls.incrementAndGet();
            assertEquals(At.INVOKE, call.at());
            // The surrounding method:
            assertSame(shop, call.self());
            assertEquals("checkout", call.methodName());
            assertArrayEquals(new Object[] {"tea", 2}, call.args());
            // The call inside it:
            InvokedCall invoked = call.invoked();
            assertEquals("io.github.qtclover.tendril.fixtures.Shop$Pricing", invoked.owner());
            assertEquals("priceOf", invoked.name());
            assertEquals("(Ljava/lang/String;I)I", invoked.descriptor());
            assertTrue(invoked.receiver() instanceof Shop.Catalog);
            assertEquals(2, invoked.argCount());
            assertArrayEquals(new Object[] {"tea", 2}, invoked.args());
            assertFalse(invoked.isSkipped());
        });

        assertEquals(65, shop.checkout("tea", 2));
        assertEquals(1, calls.get());
    }

    @Test
    void argumentsOfTheCallCanBeChanged() {
        Injector.into(Shop.class).method("checkout")
                .atInvoke("priceOf", call -> call.invoked().setArg(1, call.invoked().<Integer>arg(1) * 10));

        // priceOf sees quantity 20: 600, free shipping. checkout's own argument is untouched.
        assertEquals(600, shop.checkout("tea", 2));
        assertEquals(List.of("sold tea"), shop.log);
    }

    @Test
    void aCallCanBeSkippedWithAResult() {
        Injector.into(Shop.class).method("checkout").atInvoke("Pricing.priceOf", call -> call.invoked().skip(1));

        assertEquals(6, shop.checkout("tea", 2));
    }

    @Test
    void aSkippedCallWithoutAResultYieldsZero() {
        Injector.into(Shop.class).method("checkout").atInvoke("priceOf", call -> call.invoked().skip());

        assertEquals(5, shop.checkout("tea", 2));
    }

    @Test
    void aVoidCallCanBeSkipped() {
        Injector.into(Shop.class).method("checkout").atInvoke("audit", call -> {
            assertEquals("sold tea", call.invoked().arg(0));
            call.invoked().skip();
        });

        assertEquals(65, shop.checkout("tea", 2));
        assertTrue(shop.log.isEmpty());
    }

    @Test
    void hookAfterACallSeesAndReplacesItsResult() {
        List<Object> seen = new ArrayList<>();
        Injector.into(Shop.class).method("checkout").afterInvoke("priceOf", call -> {
            assertEquals(At.AFTER_INVOKE, call.at());
            seen.add(call.invoked().getResult());
            call.invoked().setResult(1000);
        });
        Injector.into(Shop.class).method("checkout").afterInvoke("audit", call -> {
            assertNull(call.invoked().getResult());
            seen.add("audited");
        });

        assertEquals(1000, shop.checkout("tea", 2));
        assertEquals(List.of(60, "audited"), seen);
    }

    @Test
    void severalHooksOnOneCallRunInOrderAndSkipStopsTheRest() {
        List<String> order = new ArrayList<>();
        Injector.into(Shop.class).method("checkout").atInvoke("priceOf", call -> order.add("before 1"));
        Injector.into(Shop.class).method("checkout").afterInvoke("priceOf", call -> order.add("after 1"));
        Injector.into(Shop.class).method("checkout").atInvoke("priceOf", call -> {
            order.add("before 2");
            call.invoked().skip(7);
        });
        Injector.into(Shop.class).method("checkout").atInvoke("priceOf", call -> order.add("before 3"));
        Injector.into(Shop.class).method("checkout").afterInvoke("priceOf", call -> {
            order.add("after 2 sees " + call.invoked().getResult() + " skipped " + call.invoked().isSkipped());
        });

        assertEquals(12, shop.checkout("tea", 2));
        assertEquals(List.of("before 1", "before 2", "after 1", "after 2 sees 7 skipped true"), order);
    }

    @Test
    void ordinalPicksOneOfSeveralIdenticalCalls() {
        // scaled(10) = scale(10, 1.5) + scale(10, 2.0) = 15 + 20
        Injector.into(Shop.class).method("scaled").atInvoke("scale", 1, call -> call.invoked().skip(1000L));
        assertEquals(1015, shop.scaled(10));
        Injector.removeAll();

        Injector.into(Shop.class).method("scaled").atInvoke("scale", 0, call -> call.invoked().skip(1000L));
        assertEquals(1020, shop.scaled(10));
        Injector.removeAll();

        Injector.into(Shop.class).method("scaled").atInvoke("scale", call -> call.invoked().skip(1000L));
        assertEquals(2000, shop.scaled(10));
    }

    @Test
    void staticCallWithWideArguments() {
        List<Object> receivers = new ArrayList<>();
        Injector.into(Shop.class).method("scaled").atInvoke("Shop.scale(JD)J", call -> {
            receivers.add(call.invoked().receiver());
            call.invoked().setArg(0, 100);      // an Integer for a long parameter
            call.invoked().setArg(1, 1.0);
        });
        Injector.into(Shop.class).method("scaled")
                .afterInvoke("scale", 1, call -> call.invoked().setResult((long) call.invoked().getResult() + 1));

        assertEquals(201, shop.scaled(10));
        assertEquals(2, receivers.size());
        assertNull(receivers.get(0));
    }

    @Test
    void callInsideAConstructorArgument() {
        Injector.into(Shop.class).method("wrap").atInvoke("decorate", call -> call.invoked().setArg(0, "hooked"));
        Injector.into(Shop.class).method("wrap")
                .afterInvoke("decorate", call -> call.invoked().setResult(call.invoked().getResult() + "?"));

        assertEquals("[hooked]?!", shop.wrap("plain"));
    }

    @Test
    void callInsideALoopAndATryCatch() {
        AtomicInteger before = new AtomicInteger();
        AtomicInteger after = new AtomicInteger();
        Injector.into(Shop.class).method("total").atInvoke("checked", call -> {
            before.incrementAndGet();
            if (call.invoked().<Integer>arg(0) == 100) {
                call.invoked().setArg(0, 1);
            }
        });
        Injector.into(Shop.class).method("total").afterInvoke("checked", call -> after.incrementAndGet());

        // 1 + 2 + (-5 throws, caught: -1) + (100 becomes 1)
        assertEquals(3, shop.total(new int[] {1, 2, -5, 100}));
        assertEquals(4, before.get());
        assertEquals(3, after.get(), "a call that throws has no result, so its after-hooks do not run");
    }

    @Test
    void callInsideSynchronizedAndFinally() {
        Injector.into(Shop.class).method("locked").atInvoke("shipping", call -> call.invoked().skip(42));

        assertEquals(42, shop.locked(1));
        assertEquals(List.of("unlocked"), shop.log);
        // The monitor was released: another thread can take it.
        assertDoesNotBlock(() -> shop.locked(1));
    }

    private static void assertDoesNotBlock(Runnable action) {
        Thread thread = new Thread(action);
        thread.start();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(thread.isAlive(), "the monitor is still held");
    }

    @Test
    void headAndInvokeHooksShareAMethodAndComeOutInEitherOrder() throws Exception {
        shop.checkout("tea", 1);
        byte[] original = TestSupport.currentBytes(Shop.class);

        Injection head = Injector.into(Shop.class).method("checkout").atHead(call -> call.setArg(1, 2));
        assertEquals(65, shop.checkout("tea", 1));
        Injection invoke = Injector.into(Shop.class).method("checkout")
                .atInvoke("priceOf", call -> call.invoked().skip(200));
        assertEquals(200, shop.checkout("tea", 1));

        head.remove();
        assertEquals(200, shop.checkout("tea", 1));
        invoke.remove();
        assertEquals(35, shop.checkout("tea", 1));
        assertArrayEquals(original, TestSupport.currentBytes(Shop.class));

        // And the other way round.
        invoke = Injector.into(Shop.class).method("checkout").atInvoke("priceOf", call -> call.invoked().skip(200));
        head = Injector.into(Shop.class).method("checkout").atHead(call -> call.setArg(1, 2));
        Injection ret = Injector.into(Shop.class).method("checkout").atReturn(call -> call.setResult(-1));
        assertEquals(-1, shop.checkout("tea", 1));
        invoke.remove();
        ret.remove();
        assertEquals(65, shop.checkout("tea", 1));
        head.remove();
        assertEquals(35, shop.checkout("tea", 1));
        assertArrayEquals(original, TestSupport.currentBytes(Shop.class));
    }

    @Test
    void classNotLoadedYet() throws Exception {
        String name = "io.github.qtclover.tendril.fixtures.LateCaller";
        assertFalse(TestSupport.isLoaded(name));
        Injection hook = Injector.into(name).method("shout")
                .afterInvoke("String.toUpperCase", call -> call.invoked().setResult("quiet"));

        Object late = Class.forName(name).getDeclaredConstructor().newInstance();
        assertEquals("quiet!", late.getClass().getMethod("shout", String.class).invoke(late, "loud"));

        hook.remove();
        assertEquals("LOUD!", late.getClass().getMethod("shout", String.class).invoke(late, "loud"));
    }

    static class AnnotatedHooks {
        @Inject(target = Shop.class, method = "scaled", at = At.INVOKE, invoke = "scale", ordinal = 1)
        static void second(CallInfo call) {
            call.invoked().skip(0L);
        }

        @Inject(target = Shop.class, method = "checkout", at = At.AFTER_INVOKE, invoke = "Shop.shipping")
        static void freeShipping(CallInfo call) {
            call.invoked().setResult(0);
        }
    }

    @Test
    void annotationsCanTargetCalls() {
        Injection hooks = Injector.register(AnnotatedHooks.class);

        assertEquals(15, shop.scaled(10));
        assertEquals(60, shop.checkout("tea", 2));

        hooks.remove();
        assertEquals(35, shop.scaled(10));
        assertEquals(65, shop.checkout("tea", 2));
    }

    // ---- misuse ------------------------------------------------------------

    @Test
    void aSelectorThatMatchesNoCallIsRejectedAndListsTheCalls() {
        InjectionException e = assertThrows(InjectionException.class,
                () -> Injector.into(Shop.class).method("checkout").atInvoke("refund", call -> { }));
        assertTrue(e.getMessage().contains("Shop$Pricing.priceOf"), e.getMessage());

        assertThrows(InjectionException.class,
                () -> Injector.into(Shop.class).method("checkout").atInvoke("Catalog.priceOf", call -> { }),
                "the owner is the compile-time type, Pricing, not the runtime class");
        assertThrows(InjectionException.class,
                () -> Injector.into(Shop.class).method("scaled").atInvoke("scale", 2, call -> { }));
        assertThrows(InjectionException.class,
                () -> Injector.into(Shop.class).method("scaled").atInvoke("scale(I)", call -> { }));
        assertEquals(65, shop.checkout("tea", 2));
    }

    @Test
    void selectorsAndPointsAreValidatedUpFront() {
        assertThrows(InjectionException.class,
                () -> Injector.into(Shop.class).method("wrap").atInvoke("StringBuilder.<init>", call -> { }));
        assertThrows(InjectionException.class,
                () -> Injector.into(Shop.class).method("wrap").atInvoke("", call -> { }));
        assertThrows(InjectionException.class,
                () -> Injector.into(Shop.class).method("wrap").at(At.INVOKE, call -> { }));
        assertThrows(InjectionException.class, () -> Injector.batch()
                .add("x.Y", "m", null, At.HEAD, "someCall", -1, call -> { }));
    }

    @Test
    void theSurroundingMethodCannotBeLeftFromAnInvokePoint() {
        List<Throwable> errors = new ArrayList<>();
        Injector.into(Shop.class).method("checkout").atInvoke("priceOf", call -> {
            errors.add(assertThrows(IllegalStateException.class, call::cancel));
            errors.add(assertThrows(IllegalStateException.class, () -> call.setResult(1)));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.invoked().setArg(0, 5)));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.invoked().setResult("text")));
        });
        Injector.into(Shop.class).method("checkout").afterInvoke("priceOf", call -> {
            errors.add(assertThrows(IllegalStateException.class, () -> call.invoked().skip()));
            errors.add(assertThrows(IllegalStateException.class, () -> call.invoked().setArg(1, 5)));
        });
        Injector.into(Shop.class).method("checkout").atInvoke("audit", call ->
                errors.add(assertThrows(IllegalStateException.class, () -> call.invoked().setResult(1))));
        Injector.into(Shop.class).method("checkout").atHead(call ->
                errors.add(assertThrows(IllegalStateException.class, call::invoked)));

        assertEquals(65, shop.checkout("tea", 2));
        assertEquals(8, errors.size());
    }
}
