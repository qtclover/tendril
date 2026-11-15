package io.github.qtclover.tendril;

/**
 * A method call inside a hooked method, as seen by a hook at {@link At#INVOKE} or
 * {@link At#AFTER_INVOKE}. Obtained from {@link CallInfo#invoked()}.
 *
 * <p>Given {@code int price = pricing.priceOf(item, quantity);} inside a hooked
 * method, the receiver is {@code pricing}, the arguments are {@code item} and
 * {@code quantity}, and the result is what ends up in {@code price}.
 *
 * <p>A {@code new Foo(a, b)} expression is treated as a call named {@code <init>}
 * with no receiver, whose result is the new object.
 *
 * <p>Field accesses use the same shape. Reading {@code player.health} is a call
 * named {@code health} on {@code player} with no arguments, whose result is the
 * value read. Writing {@code player.health = v} is a call with the single argument
 * {@code v} and no result; skipping it leaves the field as it was.
 */
public interface InvokedCall {

    /**
     * Binary name of the class the call is compiled against. This is the static type
     * in the source ({@code java.util.List}), not the runtime class of the receiver.
     */
    String owner();

    /** Name of the method being called. */
    String name();

    /** JVM descriptor of the method being called. */
    String descriptor();

    /** The object the method is called on, or null for a static call. */
    <T> T receiver();

    int argCount();

    /** Argument {@code index} of the call (0-based). Primitives are boxed. */
    <T> T arg(int index);

    /** A copy of the call's arguments. */
    Object[] args();

    /**
     * Replaces an argument of the call. Only at {@link At#INVOKE}.
     *
     * @throws IllegalArgumentException if the value does not fit the parameter type
     */
    void setArg(int index, Object value);

    /**
     * Does not make the call. Only at {@link At#INVOKE}. The surrounding code sees
     * {@code null}, {@code 0} or {@code false} as the result unless one is set.
     * Remaining INVOKE hooks are skipped; AFTER_INVOKE hooks still run.
     */
    void skip();

    /** Does not make the call and uses {@code result} in its place. Same as {@link #setResult} at INVOKE. */
    default void skip(Object result) {
        setResult(result);
    }

    boolean isSkipped();

    /** At {@link At#AFTER_INVOKE}: what the call returned (null for void). */
    Object getResult();

    /** {@link #getResult()}, cast. */
    @SuppressWarnings("unchecked")
    default <T> T getResultAs() {
        return (T) getResult();
    }

    /**
     * At {@link At#AFTER_INVOKE}: replaces what the call returned. At {@link At#INVOKE}:
     * skips the call and uses this value instead.
     *
     * @throws IllegalStateException    if the called method returns void
     * @throws IllegalArgumentException if the value does not fit the return type
     */
    void setResult(Object value);
}
