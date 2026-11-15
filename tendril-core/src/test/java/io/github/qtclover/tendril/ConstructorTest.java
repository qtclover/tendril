package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.Widgets;
import io.github.qtclover.tendril.fixtures.Widgets.Box;
import io.github.qtclover.tendril.fixtures.Widgets.Widget;
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

/** Hooks inside constructors, and hooks on {@code new} expressions. */
class ConstructorTest {

    private final Widgets widgets = new Widgets();

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    // ---- constructors as targets -------------------------------------------

    @Test
    void headHookRunsAfterSuperAndCanChangeArgumentsForTheRest() {
        List<String> seen = new ArrayList<>();
        Injector.into(Widget.class).constructor(int.class).atHead(call -> {
            Widget self = call.self();
            assertEquals("<init>", call.methodName());
            seen.add("tag=" + self.tag + " size=" + self.size);
            call.setArg(0, 9);
        });

        Widget widget = new Widget(5);

        // super(describe(5)) had already run; the body then saw 9.
        assertEquals(List.of("tag=size 5 size=0"), seen);
        assertEquals("size 5", widget.tag);
        assertEquals(9, widget.size);
    }

    @Test
    void returnHookSeesTheFinishedObject() {
        List<Integer> sizes = new ArrayList<>();
        Injector.into(Widget.class).constructor(int.class)
                .atReturn(call -> sizes.add(call.<Widget>self().size));

        new Widget(4);
        assertEquals(List.of(4), sizes);
    }

    @Test
    void constructorsAreSelectedLikeOverloads() {
        List<String> calls = new ArrayList<>();
        Injector.into(Widget.class).constructor().atHead(call -> calls.add("any " + call.descriptor()));
        Injector.into(Widget.class).constructor(String.class).atHead(call -> calls.add("string"));

        Widget widget = new Widget("abc");

        // Widget(String) delegates to Widget(int) first.
        assertEquals(List.of("any (I)V", "any (Ljava/lang/String;)V", "string"), calls);
        assertEquals("abc", widget.label);
        assertEquals(3, widget.size);
    }

    @Test
    void throwHookSeesAnExceptionFromAConstructor() {
        List<Throwable> seen = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        Injector.into(Widget.class).constructor(boolean.class).atThrow(call -> {
            seen.add(call.getThrown());
            errors.add(assertThrows(IllegalStateException.class, call::swallow));
        });
        Injector.into(Widget.class).constructor(boolean.class)
                .atHead(call -> errors.add(assertThrows(IllegalStateException.class, call::cancel)));

        assertThrows(IllegalStateException.class, () -> new Widget(true));
        new Widget(false);
        assertEquals(1, seen.size());
        assertEquals(3, errors.size());
    }

    @Test
    void callsInTheSuperArgumentsCannotBeHooked() {
        InjectionException e = assertThrows(InjectionException.class,
                () -> Injector.into(Widget.class).constructor(int.class).atInvoke("describe", call -> { }));
        assertTrue(e.getMessage().contains("No call matching"), e.getMessage());
        assertEquals(2, new Widget(2).size);
    }

    // ---- new expressions as invoke points ----------------------------------

    @Test
    void argumentsOfANewExpressionCanBeChanged() {
        Injector.into(Widgets.class).method("make").atInvoke("new Widget", call -> {
            InvokedCall invoked = call.invoked();
            assertEquals("<init>", invoked.name());
            assertEquals("io.github.qtclover.tendril.fixtures.Widgets$Widget", invoked.owner());
            assertNull(invoked.receiver());
            invoked.setArg(0, 42);
        });

        assertEquals(42, widgets.make(1).size);
    }

    @Test
    void theNewObjectCanBeSeenAndReplacedAfterwards() {
        Widget replacement = new Widget(7);
        List<Object> created = new ArrayList<>();
        Injector.into(Widgets.class).method("make").afterInvoke("Widget.<init>", call -> {
            created.add(call.invoked().getResult());
            call.invoked().setResult(replacement);
        });

        assertSame(replacement, widgets.make(1));
        assertEquals(1, ((Widget) created.get(0)).size);
    }

    @Test
    void aNewExpressionCanBeSkippedWithAReplacement() {
        Widget replacement = new Widget(7);
        List<Throwable> errors = new ArrayList<>();
        Injector.into(Widgets.class).method("make").atInvoke("new Widget(I)", call -> {
            errors.add(assertThrows(IllegalStateException.class, () -> call.invoked().skip()));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.invoked().skip("not a widget")));
            errors.add(assertThrows(IllegalArgumentException.class, () -> call.invoked().skip(null)));
            call.invoked().skip(replacement);
        });
        int before = Widgets.built;

        assertSame(replacement, widgets.make(1));
        assertEquals(before, Widgets.built, "the constructor must not have run");
        assertEquals(3, errors.size());
    }

    @Test
    void nestedNewExpressions() {
        AtomicInteger boxes = new AtomicInteger();
        Injector.into(Widgets.class).method("nested").atInvoke("new Widget", call -> call.invoked().setArg(0, 8));
        Injector.into(Widgets.class).method("nested").atInvoke("new Box", call -> {
            boxes.incrementAndGet();
            assertEquals(8, call.invoked().<Widget>arg(0).size);
            call.invoked().setArg(1, "hooked");
        });

        Box box = widgets.nested(1);
        assertEquals(8, box.widget.size);
        assertEquals("hooked", box.name);
        assertEquals(1, boxes.get());
    }

    @Test
    void newExpressionsWithBranchesInTheirArguments() {
        Injector.into(Widgets.class).method("choice")
                .afterInvoke("new Widget", call -> call.invoked().<Widget>getResultAs().label = "seen");
        Injector.into(Widgets.class).method("choice")
                .atInvoke("new Box", call -> call.invoked().setArg(1, call.invoked().arg(1) + "!"));

        Box big = widgets.choice(true);
        Box small = widgets.choice(false);
        assertEquals(100, big.widget.size);
        assertEquals("big!", big.name);
        assertEquals("seen", big.widget.label);
        assertEquals(1, small.widget.size);
        assertEquals("small!", small.name);
    }

    @Test
    void aDiscardedNewObjectAndUndo() throws Exception {
        byte[] original = TestSupport.currentBytes(Widgets.class);
        AtomicInteger count = new AtomicInteger();
        Injection hook = Injector.into(Widgets.class).method("discard")
                .afterInvoke("new Widget", call -> count.incrementAndGet());

        widgets.discard();
        assertEquals(1, count.get());

        hook.remove();
        widgets.discard();
        assertEquals(1, count.get());
        assertArrayEquals(original, TestSupport.currentBytes(Widgets.class));
    }
}
