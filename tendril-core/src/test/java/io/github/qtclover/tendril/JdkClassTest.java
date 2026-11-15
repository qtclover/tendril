package io.github.qtclover.tendril;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Classes defined by the bootstrap class loader, in the java.base module. These can
 * only call the bridge because the agent put it on the bootstrap class path.
 */
class JdkClassTest {

    @AfterEach
    void cleanUp() {
        Injector.removeAll();
    }

    @Test
    void staticJdkMethodCanBeHookedAndRestored() {
        assertFalse(Year.isLeap(1900));
        assertNull(Year.class.getClassLoader(), "sanity: this is a bootstrap class");

        Injection injection = Injector.into(Year.class).method("isLeap", long.class).atHead(call -> {
            if (call.<Long>arg(0) == 1900L) {
                call.cancel(true);
            }
        });

        assertTrue(Year.isLeap(1900));
        assertFalse(Year.isLeap(1901));
        assertTrue(Year.isLeap(2024));

        injection.remove();
        assertFalse(Year.isLeap(1900));
    }

    @Test
    void instanceJdkMethodCanBeHookedAndRestored() {
        StringJoiner joiner = new StringJoiner(", ").add("a").add("b");
        AtomicReference<Object> self = new AtomicReference<>();

        Injection injection = Injector.into(StringJoiner.class).method("toString").atReturn(call -> {
            if (call.self() == joiner) {
                self.set(call.self());
                call.setResult("[" + call.getResult() + "]");
            }
        });

        assertEquals("[a, b]", joiner.toString());
        assertSame(joiner, self.get());
        assertEquals("x", new StringJoiner(",").add("x").toString());

        injection.remove();
        assertEquals("a, b", joiner.toString());
    }
}
