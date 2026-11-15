package io.github.qtclover.tendril;

/**
 * One invocation of a hooked method, as seen by a {@link Hook}.
 *
 * <p>An instance is only valid while the hook runs; do not keep it.
 */
public interface CallInfo {

    /** Whether this hook is running at the head of the method or at a return. */
    At at();

    /** The class that declares the hooked method, or null for classes older than Java 5. */
    Class<?> owner();

    /** Name of the hooked method. */
    String methodName();

    /** JVM descriptor of the hooked method, for example {@code (ILjava/lang/String;)V}. */
    String descriptor();

    /** The receiver ({@code this}) of the call, or null for a static method. */
    <T> T self();

    /** Number of arguments. */
    int argCount();

    /**
     * Argument {@code index} (0-based, not counting {@code this}). Primitives are boxed.
     * From Kotlin: {@code call.arg<Int>(0)}; from Java: {@code int n = call.arg(0);}.
     */
    <T> T arg(int index);

    /** A copy of all arguments. */
    Object[] args();

    /**
     * Replaces argument {@code index}. At {@link At#HEAD} the method body then runs
     * with the new value. At {@link At#RETURN} it only changes what later hooks see.
     *
     * @throws IllegalArgumentException if the value does not fit the parameter type
     */
    void setArg(int index, Object value);

    /**
     * At {@link At#RETURN}: the value the method is about to return (null for void).
     * At {@link At#HEAD}: the value set by {@link #setResult}, or null.
     */
    Object getResult();

    /**
     * Sets the return value. At {@link At#HEAD} this also cancels the call, so the
     * method body never runs. At {@link At#RETURN} it replaces the value being returned.
     * At {@link At#THROW} it swallows the exception and returns the value instead.
     *
     * @throws IllegalStateException    if the method returns void, or at an invoke point
     * @throws IllegalArgumentException if the value does not fit the return type
     */
    void setResult(Object value);

    /**
     * Skips the method body. Only valid at {@link At#HEAD}: leaving a method from the
     * middle would jump over its {@code finally} blocks and monitor exits. A non-void method returns
     * {@code null}, {@code 0} or {@code false} unless a result has been set.
     * Remaining head hooks and all return hooks are skipped too.
     */
    void cancel();

    /** Shorthand for {@link #setResult} at {@link At#HEAD}. */
    default void cancel(Object result) {
        setResult(result);
    }

    /** True once the call has been cancelled. */
    boolean isCancelled();

    /** At {@link At#THROW}: the exception that is leaving the method. Otherwise null. */
    Throwable getThrown();

    /**
     * At {@link At#THROW}: throws {@code throwable} out of the method instead.
     *
     * @throws IllegalStateException anywhere else
     */
    void setThrown(Throwable throwable);

    /**
     * At {@link At#THROW}: drops the exception, so the method returns normally with
     * {@code null}, {@code 0} or {@code false}. To return something else, use
     * {@link #setResult}, which swallows the exception as well. Return hooks do not
     * run for a method that ended this way.
     *
     * @throws IllegalStateException anywhere else
     */
    void swallow();

    /**
     * The call this hook is attached to, inside the hooked method. Everything else on
     * {@code CallInfo} keeps describing the surrounding method.
     *
     * @throws IllegalStateException at {@link At#HEAD} and {@link At#RETURN}
     */
    InvokedCall invoked();
}
