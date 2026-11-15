package io.github.qtclover.tendril.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.util.jar.JarFile;

/**
 * Java agent entry points.
 *
 * <p>{@code premain} runs for {@code -javaagent:tendril-agent.jar}; {@code agentmain}
 * runs when the jar is loaded into an already running JVM. Both do the same thing:
 * put this jar on the bootstrap class path and hand the {@link Instrumentation}
 * instance to {@link Bridge}, where the Tendril core picks it up.
 */
public final class Agent {

    private static final String BRIDGE = "io.github.qtclover.tendril.agent.Bridge";
    private static final String PREMAIN_HOOK = "io.github.qtclover.tendril.internal.PremainHook";

    private Agent() {
    }

    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        install(args, instrumentation, true);
        // If the Tendril core is on the application class path, let it register the
        // hook classes named in the agent arguments before main() runs.
        try {
            Class<?> hook = Class.forName(PREMAIN_HOOK, true, ClassLoader.getSystemClassLoader());
            hook.getMethod("run", String.class).invoke(null, args);
        } catch (ClassNotFoundException ignored) {
            // Core not visible from the system class loader; the application will
            // call Injector.attach() itself and find the instrumentation waiting.
        } catch (InvocationTargetException e) {
            System.err.println("[tendril] failed to register hooks from agent arguments:");
            e.getCause().printStackTrace();
        }
    }

    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        install(args, instrumentation, false);
    }

    private static synchronized void install(String args, Instrumentation instrumentation, boolean premain)
            throws Exception {
        Class<?> bridge;
        try {
            // Loader "null" is the bootstrap loader: this only succeeds once the jar
            // has been appended, so the agent can safely be loaded more than once.
            bridge = Class.forName(BRIDGE, false, null);
        } catch (ClassNotFoundException notYet) {
            File jar = new File(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(jar));
            bridge = Class.forName(BRIDGE, false, null);
        }
        // Reflection, so that we are certain to write the bootstrap copy of Bridge
        // and not one that some other loader may have defined.
        if (bridge.getField("instrumentation").get(null) == null) {
            bridge.getField("agentArgs").set(null, args);
            bridge.getField("premain").set(null, premain);
            bridge.getField("instrumentation").set(null, instrumentation);
        }
    }
}
