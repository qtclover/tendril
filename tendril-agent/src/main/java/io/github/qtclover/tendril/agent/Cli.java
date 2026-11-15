package io.github.qtclover.tendril.agent;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.File;
import java.io.PrintStream;

/**
 * Command line for hooking a JVM that is already running:
 *
 * <pre>
 * java -jar tendril-agent-all.jar list
 * java -jar tendril-agent-all.jar attach &lt;pid&gt; --hooks com.example.Hooks --classpath hooks.jar
 * java -jar tendril-agent-all.jar detach &lt;pid&gt;
 * </pre>
 */
public final class Cli {

    private Cli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            String command = args.length == 0 ? "help" : args[0];
            switch (command) {
                case "list":
                    for (VirtualMachineDescriptor vm : VirtualMachine.list()) {
                        out.println(vm.id() + "\t" + vm.displayName());
                    }
                    return 0;
                case "attach": {
                    String pid = null;
                    String hooks = null;
                    String classpath = null;
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--hooks") && i + 1 < args.length) {
                            hooks = args[++i];
                        } else if (args[i].equals("--classpath") && i + 1 < args.length) {
                            classpath = args[++i];
                        } else if (pid == null && !args[i].startsWith("--")) {
                            pid = args[i];
                        } else {
                            return usage(err, "unexpected argument: " + args[i]);
                        }
                    }
                    if (pid == null || hooks == null) {
                        return usage(err, "attach needs a pid and --hooks");
                    }
                    StringBuilder agentArgs = new StringBuilder("hooks=").append(hooks);
                    if (classpath != null) {
                        StringBuilder absolute = new StringBuilder();
                        for (String jar : classpath.split(File.pathSeparator)) {
                            absolute.append(absolute.length() > 0 ? File.pathSeparator : "")
                                    .append(new File(jar).getAbsolutePath());
                        }
                        agentArgs.append(";classpath=").append(absolute);
                    }
                    load(pid, agentArgs.toString());
                    out.println("Hooks from " + hooks + " installed in JVM " + pid);
                    return 0;
                }
                case "detach":
                    if (args.length != 2) {
                        return usage(err, "detach needs a pid");
                    }
                    load(args[1], "remove");
                    out.println("All Tendril hooks removed from JVM " + args[1]);
                    return 0;
                case "help":
                case "--help":
                case "-h":
                    usage(out, null);
                    return 0;
                default:
                    return usage(err, "unknown command: " + command);
            }
        } catch (Throwable t) {
            err.println("tendril: " + t);
            err.println("The target must run as the same user, on a JDK with the attach mechanism enabled, "
                    + "and must not have been started with -XX:-EnableDynamicAgentLoading.");
            return 1;
        }
    }

    private static void load(String pid, String agentArgs) throws Exception {
        File jar = new File(Cli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            vm.loadAgent(jar.getAbsolutePath(), agentArgs);
        } finally {
            vm.detach();
        }
    }

    private static int usage(PrintStream stream, String problem) {
        if (problem != null) {
            stream.println("tendril: " + problem);
        }
        stream.println("Usage:");
        stream.println("  list                                        show the JVMs that can be attached to");
        stream.println("  attach <pid> --hooks <classes> [--classpath <jars>]");
        stream.println("                                              register @Inject hook classes in that JVM");
        stream.println("  detach <pid>                                remove every Tendril hook from that JVM");
        return problem == null ? 0 : 2;
    }
}
