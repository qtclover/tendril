package example;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.CallInfo;
import io.github.qtclover.tendril.Inject;

/** Registered by the agent before main() runs. Targets are given by name so nothing is loaded early. */
public final class AgentHooks {

    private AgentHooks() {
    }

    @Inject(targetName = "example.PlayerService", method = "damage", at = At.HEAD)
    static void noBigHits(CallInfo call) {
        if (call.<Integer>arg(0) > 100) {
            System.out.println("  [agent hook] big hit cancelled");
            call.cancel();
        }
    }

    @Inject(targetName = "example.PlayerService", method = "title", at = At.RETURN)
    static void title(CallInfo call) {
        call.setResult(call.getResult() + " (hooked from the command line)");
    }
}
