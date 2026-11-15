package io.github.qtclover.tendril.standalone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs the standalone jar the way a user would: against a separate JVM whose class
 * path contains nothing of Tendril.
 */
class StandaloneAgentTest {

    private static final String AGENT = System.getProperty("tendril.test.agentJar");
    private static final String HOOKS = System.getProperty("tendril.test.hooksJar");
    private static final String APP = System.getProperty("tendril.test.appClasses");
    private static final String JAVA = new File(System.getProperty("java.home"), "bin/java").getPath();

    private final List<Process> started = new ArrayList<>();

    @AfterEach
    void killEverything() {
        for (Process process : started) {
            process.destroyForcibly();
        }
    }

    /** A running process whose output is collected line by line. */
    private final class Running {
        final Process process;
        final List<String> lines = new CopyOnWriteArrayList<>();

        Running(String... command) throws Exception {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            started.add(process);
            Thread reader = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line; (line = in.readLine()) != null; ) {
                        lines.add(line);
                    }
                } catch (Exception ignored) {
                    // The process was killed.
                }
            });
            reader.setDaemon(true);
            reader.start();
        }

        /** Waits until a line equal to {@code expected} appears after position {@code from}. */
        int await(String expected, int from) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                for (int i = from; i < lines.size(); i++) {
                    if (lines.get(i).equals(expected)) {
                        return i + 1;
                    }
                }
                if (!process.isAlive() && process.getInputStream().available() == 0) {
                    Thread.sleep(200);
                    break;
                }
                Thread.sleep(50);
            }
            for (int i = from; i < lines.size(); i++) {
                if (lines.get(i).equals(expected)) {
                    return i + 1;
                }
            }
            return fail("never saw \"" + expected + "\" after line " + from + " in:\n" + String.join("\n", lines));
        }

        String pid() throws Exception {
            for (int i = 0; i < 300; i++) {
                for (String line : lines) {
                    if (line.startsWith("pid ")) {
                        return line.substring(4);
                    }
                }
                Thread.sleep(50);
            }
            return fail("no pid line in:\n" + String.join("\n", lines));
        }

        int exit() throws Exception {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "process did not finish");
            Thread.sleep(200);
            return process.exitValue();
        }
    }

    @Test
    void theJarIsSelfContainedAndKeepsTheBridgeOut() throws Exception {
        try (JarFile jar = new JarFile(AGENT)) {
            assertNotNull(jar.getEntry("io/github/qtclover/tendril/Injector.class"));
            assertNotNull(jar.getEntry("io/github/qtclover/tendril/shaded/asm/ClassReader.class"));
            assertNull(jar.getEntry("org/objectweb/asm/ClassReader.class"), "ASM must be relocated");
            assertNull(jar.getEntry("io/github/qtclover/tendril/agent/Bridge.class"),
                    "the bridge must only come from the embedded agent jar");
            assertNotNull(jar.getEntry("io/github/qtclover/tendril/internal/tendril-agent.jar"));
        }
    }

    @Test
    void javaagentFlagHooksAnApplicationThatDoesNotKnowTendril() throws Exception {
        Running app = new Running(JAVA, "-javaagent:" + AGENT + "=hooks=demohooks.Hooks;classpath=" + HOOKS,
                "-cp", APP, "demo.TargetApp");

        app.await("core loader application", 0);
        app.await("tick hooked", 0);
        assertFalse(app.lines.contains("tick hello"), "the hook was registered before main ran");
    }

    @Test
    void aRunningJvmCanBeHookedAndUnhookedByPid() throws Exception {
        Running app = new Running(JAVA, "-XX:+EnableDynamicAgentLoading", "-cp", APP, "demo.TargetApp");
        String pid = app.pid();
        int position = app.await("tick hello", 0);

        Running list = new Running(JAVA, "-jar", AGENT, "list");
        assertEquals(0, list.exit(), String.join("\n", list.lines));
        assertTrue(list.lines.stream().anyMatch(line -> line.startsWith(pid + "\t")), list.lines.toString());

        Running attach = new Running(JAVA, "-jar", AGENT, "attach", pid, "--hooks", "demohooks.Hooks",
                "--classpath", HOOKS);
        assertEquals(0, attach.exit(), String.join("\n", attach.lines));
        position = app.await("core loader application", position);
        position = app.await("tick hooked", position);

        Running detach = new Running(JAVA, "-jar", AGENT, "detach", pid);
        assertEquals(0, detach.exit(), String.join("\n", detach.lines));
        app.await("tick hello", position);
    }

    @Test
    void badCommandLinesExplainThemselves() throws Exception {
        Running none = new Running(JAVA, "-jar", AGENT, "attach");
        assertEquals(2, none.exit());
        assertTrue(none.lines.stream().anyMatch(line -> line.contains("Usage")), none.lines.toString());

        Running noSuchJvm = new Running(JAVA, "-jar", AGENT, "detach", "999999999");
        assertEquals(1, noSuchJvm.exit());
    }
}
