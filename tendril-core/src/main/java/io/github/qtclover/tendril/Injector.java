package io.github.qtclover.tendril;

import io.github.qtclover.tendril.internal.AnnotationScanner;
import io.github.qtclover.tendril.internal.Attacher;
import io.github.qtclover.tendril.internal.Registry;

import java.lang.instrument.Instrumentation;

/**
 * Entry point of Tendril.
 *
 * <pre>{@code
 * Injector.attach();
 *
 * Injection hook = Injector.into(PlayerService.class)
 *         .method("damage", int.class)
 *         .atHead(call -> { if (call.<Integer>arg(0) > 100) call.cancel(); });
 *
 * hook.remove();   // original bytecode is back
 * }</pre>
 */
public final class Injector {

    private Injector() {
    }

    /**
     * Makes sure Tendril has an {@link Instrumentation} handle. If the JVM was started
     * with {@code -javaagent:tendril-agent.jar} this is free; otherwise the agent is
     * loaded into the running JVM. Calling it again does nothing.
     *
     * <p>Every method that installs a hook calls this for you; call it yourself to
     * pay the cost (and see any failure) at a moment of your choosing.
     *
     * @throws InjectionException if no agent is present and one cannot be attached
     */
    public static void attach() {
        Attacher.ensureAttached();
    }

    public static boolean isAttached() {
        return Attacher.isAttached();
    }

    /** How the agent got in, or null if {@link #attach()} has not succeeded yet. */
    public static AttachMode attachMode() {
        return Attacher.mode();
    }

    /** The underlying handle, for anything Tendril does not wrap. Attaches if necessary. */
    public static Instrumentation instrumentation() {
        return Attacher.ensureAttached();
    }

    /** Starts a fluent injection into a class that is already loaded. */
    public static ClassSelector into(Class<?> target) {
        return new ClassSelector(target.getName());
    }

    /**
     * Starts a fluent injection into a class by binary name. The class does not need
     * to be loaded: the hooks are woven in when it is. The name may also be a pattern,
     * see {@link #intoMatching} and {@link #intoSubtypes}.
     */
    public static ClassSelector into(String className) {
        return new ClassSelector(className);
    }

    /**
     * Starts a fluent injection into {@code supertype} and every class that extends or
     * implements it, whether loaded already or later. Classes that do not declare the
     * chosen method themselves are left alone, as are JDK classes.
     */
    public static ClassSelector intoSubtypes(Class<?> supertype) {
        return new ClassSelector(supertype.getName() + "+");
    }

    /** Like {@link #intoSubtypes(Class)}, by binary name. */
    public static ClassSelector intoSubtypes(String supertypeName) {
        return new ClassSelector(supertypeName + "+");
    }

    /**
     * Starts a fluent injection into every class whose binary name matches
     * {@code pattern}, in which {@code *} stands for any run of characters:
     * {@code "com.example.*"}, {@code "*.service.*Impl"}. JDK classes never match.
     */
    public static ClassSelector intoMatching(String pattern) {
        if (pattern.indexOf('*') < 0) {
            throw new InjectionException("\"" + pattern + "\" has no * in it; use into() for a single class");
        }
        return new ClassSelector(pattern);
    }

    /** Installs every static {@link Inject}-annotated method of {@code hooks}. */
    public static Injection register(Class<?> hooks) {
        return AnnotationScanner.scan(hooks, null).install();
    }

    /** Installs every {@link Inject}-annotated method of {@code hooks}, static or not. */
    public static Injection register(Object hooks) {
        if (hooks instanceof Class<?> c) {
            return register(c);
        }
        return AnnotationScanner.scan(hooks.getClass(), hooks).install();
    }

    /** Starts a batch: several hooks installed, and later removed, as one. */
    public static InjectionBatch batch() {
        return new InjectionBatch();
    }

    /** Removes every hook and restores every class Tendril has changed. */
    public static void removeAll() {
        if (Attacher.isAttached()) {
            Registry.get().removeAll();
        }
    }
}
