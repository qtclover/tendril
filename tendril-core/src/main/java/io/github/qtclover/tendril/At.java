package io.github.qtclover.tendril;

/** Where in the target method a hook runs. */
public enum At {
    /** Before the first instruction of the method. */
    HEAD,
    /** Before every normal return. Not run when the method exits by throwing. */
    RETURN,
    /**
     * Just before a particular method call inside the target method. The hook can
     * read and change the call's arguments, or skip the call. See {@link InvokedCall}.
     */
    INVOKE,
    /**
     * Just after a particular method call inside the target method returns normally.
     * The hook can read and replace the call's result. See {@link InvokedCall}.
     */
    AFTER_INVOKE
}
