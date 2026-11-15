package io.github.qtclover.tendril;

/** Thrown when attaching fails or a hook cannot be installed. */
public class InjectionException extends RuntimeException {

    public InjectionException(String message) {
        super(message);
    }

    public InjectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
