package io.github.qtclover.tendril;

/** Where in the target method a hook runs. */
public enum At {
    /** Before the first instruction of the method. */
    HEAD,
    /** Before every normal return. Not run when the method exits by throwing. */
    RETURN,
    /**
     * When an exception is about to leave the method. The hook can look at it, replace
     * it, or swallow it and make the method return normally. Not run for exceptions the
     * method catches itself, nor for exceptions thrown by hooks.
     */
    THROW,
    /**
     * Just before a particular method call inside the target method. The hook can
     * read and change the call's arguments, or skip the call. See {@link InvokedCall}.
     */
    INVOKE,
    /**
     * Just after a particular method call inside the target method returns normally.
     * The hook can read and replace the call's result. See {@link InvokedCall}.
     */
    AFTER_INVOKE,
    /**
     * Just after a particular field is read inside the target method. The hook can
     * see and replace the value that was read. See {@link InvokedCall}.
     */
    FIELD_READ,
    /**
     * Just before a particular field is written inside the target method. The hook
     * can see and change the value, or skip the write. See {@link InvokedCall}.
     */
    FIELD_WRITE
}
