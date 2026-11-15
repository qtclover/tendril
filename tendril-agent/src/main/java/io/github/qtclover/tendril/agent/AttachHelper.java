package io.github.qtclover.tendril.agent;

import com.sun.tools.attach.VirtualMachine;

/**
 * Tiny main class run in a short-lived child JVM to attach the agent to its parent.
 *
 * <p>A JVM refuses to attach to itself unless started with
 * {@code -Djdk.attach.allowAttachSelf=true}, but it happily accepts an attach
 * request from another process. So self-attach is done by proxy.
 */
public final class AttachHelper {

    private AttachHelper() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: AttachHelper <pid> <agent-jar>");
            System.exit(2);
        }
        try {
            VirtualMachine vm = VirtualMachine.attach(args[0]);
            try {
                vm.loadAgent(args[1]);
            } finally {
                vm.detach();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}
