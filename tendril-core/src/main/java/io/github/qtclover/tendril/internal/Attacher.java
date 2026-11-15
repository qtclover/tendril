package io.github.qtclover.tendril.internal;

import com.sun.management.HotSpotDiagnosticMXBean;
import io.github.qtclover.tendril.AttachMode;
import io.github.qtclover.tendril.InjectionException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * Gets hold of an {@link Instrumentation}: from an agent that is already there, or
 * by loading one into this JVM.
 *
 * <p>Note that nothing here may refer to the {@code Bridge} class directly. It only
 * becomes loadable once the agent has put its jar on the bootstrap class path, and
 * a failed attempt to resolve a class is remembered by the JVM.
 */
public final class Attacher {

    private static final String BRIDGE = "io.github.qtclover.tendril.agent.Bridge";
    private static final String HELPER = "io.github.qtclover.tendril.agent.AttachHelper";
    private static final String AGENT_RESOURCE = "tendril-agent.jar";
    private static final String HOW_TO_AVOID = "To avoid attaching at runtime altogether, start the JVM with "
            + "-javaagent:tendril-agent.jar";

    private static volatile Instrumentation instrumentation;
    private static volatile AttachMode mode;

    private Attacher() {
    }

    public static boolean isAttached() {
        return instrumentation != null;
    }

    public static AttachMode mode() {
        return mode;
    }

    public static Instrumentation ensureAttached() {
        Instrumentation result = instrumentation;
        return result != null ? result : attach();
    }

    private static synchronized Instrumentation attach() {
        if (instrumentation != null) {
            return instrumentation;
        }
        Instrumentation found = findInstalledAgent();
        if (found == null) {
            loadAgentIntoThisJvm();
            found = findInstalledAgent();
            if (found == null) {
                throw new InjectionException("The agent was loaded but did not initialise. " + HOW_TO_AVOID);
            }
        }
        if (!found.isRetransformClassesSupported()) {
            throw new InjectionException("This JVM does not support re-transforming classes");
        }
        Registry.start(found);
        mode = readPremainFlag() ? AttachMode.PREMAIN : AttachMode.DYNAMIC;
        instrumentation = found;
        return found;
    }

    private static Instrumentation findInstalledAgent() {
        try {
            Class<?> bridge = Class.forName(BRIDGE, false, null);
            return (Instrumentation) bridge.getField("instrumentation").get(null);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new InjectionException("Incompatible tendril-agent on the bootstrap class path", e);
        }
    }

    private static boolean readPremainFlag() {
        try {
            return (boolean) Class.forName(BRIDGE, false, null).getField("premain").get(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    static String agentArgs() {
        try {
            return (String) Class.forName(BRIDGE, false, null).getField("agentArgs").get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Dynamic attach. A JVM will not attach to itself, so a child JVM is started
     * whose only job is to attach to us and load the agent jar.
     */
    private static void loadAgentIntoThisJvm() {
        if (Boolean.FALSE.equals(dynamicAgentLoadingFlag())) {
            throw new InjectionException("This JVM was started with -XX:-EnableDynamicAgentLoading, "
                    + "so no agent can be loaded at runtime. " + HOW_TO_AVOID);
        }
        Path jar = extractAgentJar();
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
        String pid = Long.toString(ProcessHandle.current().pid());
        String output;
        int exit;
        try {
            Process process = new ProcessBuilder(java.toString(), "-cp", jar.toString(), HELPER, pid, jar.toString())
                    .redirectErrorStream(true)
                    .start();
            process.getOutputStream().close();
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new InjectionException("Timed out attaching to this JVM. " + HOW_TO_AVOID);
            }
            exit = process.exitValue();
        } catch (IOException e) {
            throw new InjectionException("Could not start " + java + " to attach to this JVM. " + HOW_TO_AVOID, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InjectionException("Interrupted while attaching to this JVM", e);
        }
        if (exit != 0) {
            throw new InjectionException("Could not attach to this JVM (helper exited with " + exit + "). "
                    + "Common causes: a runtime image without the jdk.attach module, "
                    + "-XX:+DisableAttachMechanism, or -XX:-EnableDynamicAgentLoading. "
                    + HOW_TO_AVOID + "\n" + output);
        }
    }

    /**
     * The value of {@code -XX:[+-]EnableDynamicAgentLoading} if it was given on the
     * command line, otherwise null. Since JDK 21 (JEP 451) the JVM prints a warning
     * when an agent is loaded dynamically and the flag was not given; a later release
     * is expected to refuse unless it is.
     */
    public static Boolean dynamicAgentLoadingFlag() {
        try {
            var option = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                    .getVMOption("EnableDynamicAgentLoading");
            if (option.getOrigin() == com.sun.management.VMOption.Origin.DEFAULT) {
                return null;
            }
            return Boolean.valueOf(option.getValue());
        } catch (Throwable notHotSpot) {
            return null;
        }
    }

    private static Path extractAgentJar() {
        try (InputStream in = Attacher.class.getResourceAsStream(AGENT_RESOURCE)) {
            if (in == null) {
                throw new InjectionException("tendril-agent.jar is missing from tendril-core; the build is broken");
            }
            Path jar = Files.createTempFile("tendril-agent-", ".jar");
            jar.toFile().deleteOnExit();
            Files.copy(in, jar, StandardCopyOption.REPLACE_EXISTING);
            return jar;
        } catch (IOException e) {
            throw new InjectionException("Could not unpack the agent jar to a temporary file", e);
        }
    }
}
