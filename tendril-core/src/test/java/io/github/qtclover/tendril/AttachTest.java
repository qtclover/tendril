package io.github.qtclover.tendril;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachTest {

    @Test
    void attachIsIdempotentAndReportsHowItGotIn() {
        Injector.attach();
        Injector.attach();

        assertTrue(Injector.isAttached());
        assertNotNull(Injector.instrumentation());
        assertTrue(Injector.instrumentation().isRetransformClassesSupported());

        // The "agentTest" Gradle task runs these same tests with -javaagent.
        AttachMode expected = Boolean.getBoolean("tendril.test.expectPremain")
                ? AttachMode.PREMAIN : AttachMode.DYNAMIC;
        assertEquals(expected, Injector.attachMode());
    }
}
