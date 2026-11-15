package io.github.qtclover.tendril;

import io.github.qtclover.tendril.fixtures.PremainTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Only meaningful when the JVM was started with
 * {@code -javaagent:tendril-agent.jar=...PremainHooks}; the "agentArgsTest" Gradle
 * task does that and runs nothing but this class.
 */
@EnabledIfSystemProperty(named = "tendril.test.agentArgs", matches = "true")
class AgentArgumentsTest {

    @Test
    void hookClassNamedInAgentArgumentsIsRegisteredBeforeMain() {
        assertEquals(AttachMode.PREMAIN, Injector.attachMode());
        assertEquals("hooked before main", new PremainTarget().name());
    }
}
