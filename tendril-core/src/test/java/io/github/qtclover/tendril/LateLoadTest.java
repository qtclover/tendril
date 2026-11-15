package io.github.qtclover.tendril;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hooks registered by name before the target class exists in the JVM. */
class LateLoadTest {

    private static final String LATE = "io.github.qtclover.tendril.fixtures.LateLoaded";

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    @Test
    void hooksAreWovenWhenTheClassIsFirstLoadedAndCanStillBeUndone() throws Exception {
        assertFalse(TestSupport.isLoaded(LATE), "fixture must not be loaded before the hooks are registered");

        Injection head = Injector.into(LATE).method("name").atHead(call -> call.cancel("hooked"));
        Injection ret = Injector.into(LATE).method("number")
                .atReturn(call -> call.setResult((int) call.getResult() + 1));
        assertFalse(TestSupport.isLoaded(LATE), "registering by name must not load the class");

        // First use: the class is loaded here, through the transformer.
        Object late = Class.forName(LATE).getDeclaredConstructor().newInstance();
        assertTrue(TestSupport.isLoaded(LATE));
        assertEquals("hooked", late.getClass().getMethod("name").invoke(late));
        assertEquals(8, late.getClass().getMethod("number").invoke(late));

        head.remove();
        assertEquals("late", late.getClass().getMethod("name").invoke(late));
        assertEquals(8, late.getClass().getMethod("number").invoke(late));

        ret.remove();
        assertEquals(7, late.getClass().getMethod("number").invoke(late));
    }
}
