package demohooks;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.CallInfo;
import io.github.qtclover.tendril.Inject;
import io.github.qtclover.tendril.Injector;

public final class Hooks {

    static {
        // The core must come from the application class loader, never the bootstrap one.
        System.out.println("core loader " + (Injector.class.getClassLoader() == null ? "bootstrap" : "application"));
    }

    private Hooks() {
    }

    @Inject(targetName = "demo.Greeter", method = "greet", at = At.RETURN)
    static void greet(CallInfo call) {
        call.setResult("hooked");
    }
}
