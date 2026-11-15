package io.github.qtclover.tendril;

/** Where in the target method a hook runs. */
public enum At {
    /** Before the first instruction of the method. */
    HEAD,
    /** Before every normal return. Not run when the method exits by throwing. */
    RETURN
}
