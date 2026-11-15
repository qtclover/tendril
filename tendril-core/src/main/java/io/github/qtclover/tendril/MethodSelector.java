package io.github.qtclover.tendril;

/** Second step of the fluent API: a method has been chosen, now install a hook. */
public final class MethodSelector {

    private final String className;
    private final String methodName;
    private final String descriptor;
    private final boolean locals;

    MethodSelector(String className, String methodName, String descriptor) {
        this(className, methodName, descriptor, false);
    }

    private MethodSelector(String className, String methodName, String descriptor, boolean locals) {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.locals = locals;
    }

    /**
     * Makes the invoke and field hooks installed from here on capture the method's
     * local variables, so they can use {@link CallInfo#local} and {@link CallInfo#setLocal}.
     * Needs a class compiled with debug information ({@code javac -g}, the default in
     * Gradle and Maven).
     */
    public MethodSelector withLocals() {
        return new MethodSelector(className, methodName, descriptor, true);
    }

    /** Runs {@code hook} before the method body. */
    public Injection atHead(Hook hook) {
        return at(At.HEAD, hook);
    }

    /** Runs {@code hook} before every normal return. */
    public Injection atReturn(Hook hook) {
        return at(At.RETURN, hook);
    }

    /** Runs {@code hook} when an exception is about to leave the method. */
    public Injection atThrow(Hook hook) {
        return at(At.THROW, hook);
    }

    /**
     * Runs {@code hook} just before every call to {@code call} inside the method.
     * The hook gets at the call through {@link CallInfo#invoked()}.
     *
     * @param call {@code "name"}, {@code "Owner.name"}, or either followed by a descriptor
     */
    public Injection atInvoke(String call, Hook hook) {
        return atInvoke(call, -1, hook);
    }

    /** Like {@link #atInvoke(String, Hook)}, for only the {@code ordinal}-th matching call (from 0). */
    public Injection atInvoke(String call, int ordinal, Hook hook) {
        return Injector.batch().add(className, methodName, descriptor, At.INVOKE, call, ordinal, locals, hook).install();
    }

    /** Runs {@code hook} just after every call to {@code call} inside the method returns. */
    public Injection afterInvoke(String call, Hook hook) {
        return afterInvoke(call, -1, hook);
    }

    /** Like {@link #afterInvoke(String, Hook)}, for only the {@code ordinal}-th matching call (from 0). */
    public Injection afterInvoke(String call, int ordinal, Hook hook) {
        return Injector.batch().add(className, methodName, descriptor, At.AFTER_INVOKE, call, ordinal, locals, hook)
                .install();
    }

    /**
     * Runs {@code hook} just after the method reads {@code field} ({@code "name"} or
     * {@code "Owner.name"}). {@code call.invoked().getResult()} is the value read and
     * {@code setResult} replaces it.
     */
    public Injection onFieldRead(String field, Hook hook) {
        return Injector.batch().add(className, methodName, descriptor, At.FIELD_READ, field, -1, locals, hook).install();
    }

    /**
     * Runs {@code hook} just before the method writes {@code field}.
     * {@code call.invoked().arg(0)} is the value about to be written; {@code setArg(0, v)}
     * changes it and {@code skip()} drops the write.
     */
    public Injection onFieldWrite(String field, Hook hook) {
        return Injector.batch().add(className, methodName, descriptor, At.FIELD_WRITE, field, -1, locals, hook).install();
    }

    /** For {@link At#HEAD}, {@link At#RETURN} and {@link At#THROW}; invoke points need a call, see {@link #atInvoke}. */
    public Injection at(At at, Hook hook) {
        return Injector.batch().add(className, methodName, descriptor, at, hook).install();
    }
}
