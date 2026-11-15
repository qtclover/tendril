package io.github.qtclover.tendril;

/**
 * Code that runs inside a hooked method.
 *
 * <p>Anything a hook throws propagates out of the hooked method as if the method
 * itself had thrown it.
 */
@FunctionalInterface
public interface Hook {
    void handle(CallInfo call) throws Throwable;
}
