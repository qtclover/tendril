package io.github.qtclover.tendril.fixtures;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.CallInfo;
import io.github.qtclover.tendril.Inject;

/** Named in the agent arguments of the "agentArgsTest" Gradle task. */
public final class PremainHooks {

    private PremainHooks() {
    }

    @Inject(targetName = "io.github.qtclover.tendril.fixtures.PremainTarget", method = "name", at = At.RETURN)
    static void rename(CallInfo call) {
        call.setResult("hooked before main");
    }
}
