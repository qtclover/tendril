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
     *
     * @throws IllegalStateException    if the method returns void
     * @throws IllegalArgumentException if the value does not fit the return type
     */
    void setResult(Object value);

    /**
     * Skips the method body. Only valid at {@link At#HEAD}. A non-void method returns
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
}
