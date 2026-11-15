package io.github.qtclover.tendril;

/** Second step of the fluent API: a method has been chosen, now install a hook. */
public final class MethodSelector {

    private final String className;
    private final String methodName;
    private final String descriptor;

    MethodSelector(String className, String methodName, String descriptor) {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
    }

    /** Runs {@code hook} before the method body. */
    public Injection atHead(Hook hook) {
        return at(At.HEAD, hook);
    }

    /** Runs {@code hook} before every normal return. */
    public Injection atReturn(Hook hook) {
        return at(At.RETURN, hook);
    }

    public Injection at(At at, Hook hook) {
        return Injector.batch().add(className, methodName, descriptor, at, hook).install();
    }
}
