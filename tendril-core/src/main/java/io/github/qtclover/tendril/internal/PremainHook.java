package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.Injector;

/**
 * Called reflectively by the agent, at JVM start and whenever it is loaded into a
 * running JVM, with the agent arguments. They are either a comma-separated list of
 * classes with {@code @Inject} methods, or {@code key=value} pairs separated by
 * semicolons:
 *
 * <ul>
 *   <li>{@code hooks=com.example.Hooks,com.example.More} registers hook classes;</li>
 *   <li>{@code remove} removes every hook;</li>
 *   <li>{@code classpath=...} has already been dealt with by the agent.</li>
 * </ul>
 *
 * So an application can be hooked without changing a line of it:
 * {@code -javaagent:tendril-agent.jar=com.example.Hooks}.
 */
public final class PremainHook {

    private PremainHook() {
    }

    public static void run(String args) {
        if (args == null || args.isBlank()) {
            return;
        }
        for (String part : args.split(";")) {
            part = part.trim();
            if (part.equals("remove")) {
                Injector.removeAll();
            } else if (part.startsWith("hooks=")) {
                register(part.substring("hooks=".length()));
            } else if (!part.isEmpty() && part.indexOf('=') < 0) {
                register(part);
            }
        }
    }

    private static void register(String classNames) {
        for (String name : classNames.split(",")) {
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
