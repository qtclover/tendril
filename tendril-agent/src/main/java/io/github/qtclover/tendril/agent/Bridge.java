package io.github.qtclover.tendril.agent;

import java.lang.instrument.Instrumentation;

/**
 * The one class that woven bytecode calls into.
 *
 * <p>Re-transformation may only change method bodies, so a hooked method cannot be
 * given new fields or helper methods. Instead every hooked method gets a few
 * instructions that call the static methods below, and those forward to whatever
 * {@link Handler} the Tendril core installed.
 *
 * <p>This class is loaded by the <em>bootstrap</em> class loader (the agent appends
 * its own jar to the bootstrap search path). That makes it visible from every class
 * in the JVM, including JDK classes, which is the only reason hooking them can work.
 * For the same reason it must depend on nothing but {@code java.base} and
 * {@code java.instrument}.
 */
public final class Bridge {

    /** Implemented by the Tendril core, which lives in an ordinary class loader. */
    public interface Handler {
        /** Runs head hooks. Returns an opaque per-call context, or null when nothing is hooked. */
        Object enter(int site, Class<?> owner, Object self, Object[] args);

        /** True when a head hook cancelled the call. */
        boolean cancelled(Object context);

        /** The boxed value to return from a cancelled call. */
        Object result(Object context);

        /** Runs return hooks. Returns the (possibly replaced) boxed return value. */
        Object exit(Object context, Object result);
    }

    /** Set once by {@link Agent}. */
    public static volatile Instrumentation instrumentation;

    /** The string after {@code =} in {@code -javaagent:tendril-agent.jar=...}, if any. */
    public static volatile String agentArgs;

    /** True when the agent was started with {@code -javaagent}, false when attached later. */
    public static volatile boolean premain;

    /** Set by the Tendril core once it is ready to dispatch. */
    public static volatile Handler handler;

    private Bridge() {
    }

    public static Object enter(int site, Class<?> owner, Object self, Object[] args) {
        Handler h = handler;
        return h == null ? null : h.enter(site, owner, self, args);
    }

    public static boolean cancelled(Object context) {
        if (context == null) {
            return false;
        }
        Handler h = handler;
        return h != null && h.cancelled(context);
    }

    public static Object result(Object context) {
        Handler h = handler;
        return h == null ? null : h.result(context);
    }

    public static Object exit(Object context, Object result) {
        if (context == null) {
            return result;
        }
        Handler h = handler;
        return h == null ? result : h.exit(context, result);
    }
}
