package io.github.qtclover.tendril;

/**
 * Handle to one or more installed hooks.
 *
 * <p>{@link #remove()} stops the hooks firing and, once a method has no hooks
 * left, puts its original bytecode back.
 */
public interface Injection extends AutoCloseable {

    /** Uninstalls the hooks. Safe to call more than once. */
    void remove();

    /** True until {@link #remove()} is called. */
    boolean isActive();

    /** Same as {@link #remove()}, so an injection can be scoped with try-with-resources. */
    @Override
    default void close() {
        remove();
    }
}
