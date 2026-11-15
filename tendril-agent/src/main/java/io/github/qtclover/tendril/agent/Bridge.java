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

        /**
         * Runs the hooks placed before a call inside a hooked method. Returns an opaque
         * per-call-site context, or null when nothing is hooked there.
         */
        Object beforeCall(Object context, int point, Object receiver, Object[] args);

        /** True when a hook asked for the call not to be made. */
        boolean skipped(Object callContext);

        /** The boxed value to use in place of a skipped call's result. */
        Object callResult(Object callContext);

        /** Runs the hooks placed after a call. Returns the (possibly replaced) boxed result. */
        Object afterCall(Object callContext, Object result);

        /**
         * Runs throw hooks for an exception leaving the method. Returns the exception to
         * throw, or null if a hook swallowed it and the method should return normally.
         */
        Throwable thrown(Object context, Throwable throwable);
    }

    /**
     * Bumped whenever this class changes shape, so that the core can refuse to work
     * with an agent jar from a different release instead of failing inside woven code.
     */
    public static final int VERSION = 3;

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

    public static Throwable thrown(Object context, Throwable throwable) {
        if (context == null) {
            return throwable;
        }
        Handler h = handler;
        return h == null ? throwable : h.thrown(context, throwable);
    }

    public static Object beforeCall(Object context, int point, Object receiver, Object[] args) {
        if (context == null) {
            return null;
        }
        Handler h = handler;
        return h == null ? null : h.beforeCall(context, point, receiver, args);
    }

    public static boolean skipped(Object callContext) {
        if (callContext == null) {
            return false;
        }
        Handler h = handler;
        return h != null && h.skipped(callContext);
    }

    public static Object callResult(Object callContext) {
        Handler h = handler;
        return h == null ? null : h.callResult(callContext);
    }

    public static Object afterCall(Object callContext, Object result) {
        if (callContext == null) {
            return result;
        }
        Handler h = handler;
        return h == null ? result : h.afterCall(callContext, result);
    }
}
