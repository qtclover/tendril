package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.InjectionException;
import org.objectweb.asm.Type;

/** Helpers for JVM method descriptors. Internal. */
public final class Descriptors {

    private Descriptors() {
    }

    /** {@code (int, String)} becomes {@code "(ILjava/lang/String;)"}. */
    public static String parameters(Class<?>... types) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> type : types) {
            sb.append(Type.getDescriptor(type));
        }
        return sb.append(')').toString();
    }

    /** Accepts a full descriptor or just its parameter part; rejects anything else. */
    public static String validate(String descriptor) {
        boolean parametersOnly = descriptor.endsWith(")");
        try {
            if (!descriptor.startsWith("(")) {
                throw new IllegalArgumentException();
            }
            Type.getArgumentTypes(parametersOnly ? descriptor + "V" : descriptor);
            if (!parametersOnly) {
                Type.getReturnType(descriptor).getSize();
            }
        } catch (RuntimeException e) {
            throw new InjectionException("Not a method descriptor: \"" + descriptor
                    + "\" (expected something like \"(ILjava/lang/String;)V\" or \"(I)\")");
        }
        return descriptor;
    }

    static boolean matches(String selector, String actual) {
        if (selector == null) {
            return true;
        }
        return selector.endsWith(")") ? actual.startsWith(selector) : actual.equals(selector);
    }
}
