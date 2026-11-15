package io.github.qtclover.tendril;

import io.github.qtclover.tendril.internal.Descriptors;

/** First step of the fluent API: a class has been chosen, now pick a method. */
public final class ClassSelector {

    private final String className;

    ClassSelector(String className) {
        this.className = className;
    }

    /** Every method called {@code name}, whatever its parameters. */
    public MethodSelector method(String name) {
        return new MethodSelector(className, name, null);
    }

    /**
     * The overload of {@code name} with exactly these parameter types.
     * For a method with no parameters use {@link #methodDescriptor methodDescriptor(name, "()")}.
     */
    public MethodSelector method(String name, Class<?> firstParameter, Class<?>... moreParameters) {
        Class<?>[] all = new Class<?>[moreParameters.length + 1];
        all[0] = firstParameter;
        System.arraycopy(moreParameters, 0, all, 1, moreParameters.length);
        return new MethodSelector(className, name, Descriptors.parameters(all));
    }

    /**
     * The overload of {@code name} matching a JVM descriptor: either complete
     * ({@code "(I)V"}) or only the parameter part ({@code "(I)"}, {@code "()"}).
     */
    public MethodSelector methodDescriptor(String name, String descriptor) {
        return new MethodSelector(className, name, descriptor);
    }
}
