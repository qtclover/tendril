package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.Injector;

/**
 * Called reflectively by the agent's {@code premain}. The agent arguments are a
 * comma-separated list of classes with {@code @Inject} methods, so an application
 * can be hooked without changing a line of it:
 * {@code -javaagent:tendril-agent.jar=com.example.Hooks}.
 */
public final class PremainHook {

    private PremainHook() {
    }

    public static void run(String args) {
        if (args == null || args.isBlank()) {
            return;
        }
        for (String name : args.split(",")) {
            name = name.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                Injector.register(Class.forName(name, true, ClassLoader.getSystemClassLoader()));
            } catch (Throwable t) {
                System.err.println("[tendril] could not register hook class " + name + ": " + t);
            }
        }
    }
}
