package io.github.qtclover.tendril.agent;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

/**
 * Java agent entry points.
 *
 * <p>{@code premain} runs for {@code -javaagent:tendril-agent.jar}; {@code agentmain}
 * runs when the jar is loaded into an already running JVM. Both put the bridge on
 * the bootstrap class path and hand the {@link Instrumentation} instance to
 * {@link Bridge}, where the Tendril core picks it up.
 *
 * <p>The agent arguments are either a comma-separated list of hook classes, or
 * {@code key=value} pairs separated by semicolons:
 * {@code hooks=com.example.Hooks,com.example.More;classpath=/path/hooks.jar;remove}.
 * {@code classpath} is handled here; the rest is passed on to the core.
 */
public final class Agent {

    private static final String BRIDGE = "io.github.qtclover.tendril.agent.Bridge";
    private static final String CORE_ENTRY = "io.github.qtclover.tendril.internal.PremainHook";
    /** Where tendril-core carries the plain agent jar. Present when this class runs from the standalone jar. */
    private static final String EMBEDDED_AGENT = "/io/github/qtclover/tendril/internal/tendril-agent.jar";

    private Agent() {
    }

    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        install(args, instrumentation, true);
        runCore(args, instrumentation);
    }

    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        install(args, instrumentation, false);
        runCore(args, instrumentation);
    }

    private static synchronized void install(String args, Instrumentation instrumentation, boolean premain)
            throws Exception {
        Class<?> bridge;
        try {
            // Loader "null" is the bootstrap loader: this only succeeds once the jar
            // has been appended, so the agent can safely be loaded more than once.
            bridge = Class.forName(BRIDGE, false, null);
        } catch (ClassNotFoundException notYet) {
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(bridgeJar()));
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

    /**
     * The jar to put on the bootstrap class path. Only the bridge belongs there. The
     * plain agent jar is small enough to go as it is; the standalone jar also holds the
     * core and ASM, so from that one the embedded plain jar is unpacked and used instead.
     */
    private static File bridgeJar() throws Exception {
        try (InputStream embedded = Agent.class.getResourceAsStream(EMBEDDED_AGENT)) {
            if (embedded == null) {
                return new File(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            }
            Path jar = Files.createTempFile("tendril-agent-", ".jar");
            jar.toFile().deleteOnExit();
            Files.copy(embedded, jar, StandardCopyOption.REPLACE_EXISTING);
            return jar.toFile();
        }
    }

    /**
     * If the Tendril core is visible from the system class loader, lets it act on the
     * arguments: register hook classes, or remove all hooks. If it is not, the
     * application will call Injector.attach() itself and find the instrumentation waiting.
     */
    private static void runCore(String args, Instrumentation instrumentation) {
        if (args == null || args.trim().isEmpty()) {
            return;
        }
        try {
            for (String part : args.split(";")) {
                if (part.trim().startsWith("classpath=")) {
                    for (String jar : part.trim().substring("classpath=".length()).split(File.pathSeparator)) {
                        if (!jar.trim().isEmpty()) {
                            instrumentation.appendToSystemClassLoaderSearch(new JarFile(jar.trim()));
                        }
                    }
                }
            }
            Class<?> core = Class.forName(CORE_ENTRY, true, ClassLoader.getSystemClassLoader());
            core.getMethod("run", String.class).invoke(null, args);
        } catch (ClassNotFoundException e) {
            System.err.println("[tendril] agent arguments were given, but tendril-core is not on the class path. "
                    + "Add it, or use the standalone tendril-agent-all jar.");
        } catch (InvocationTargetException e) {
            System.err.println("[tendril] failed to act on agent arguments \"" + args + "\":");
            e.getCause().printStackTrace();
        } catch (Exception e) {
            System.err.println("[tendril] failed to act on agent arguments \"" + args + "\": " + e);
        }
    }
}
