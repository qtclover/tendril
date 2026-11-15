package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.Hook;

/** One hook bound to one method selector. Internal. */
public final class InjectionImpl {

    final String classInternalName;
    final String methodName;
    /** Full descriptor, parameter-only descriptor, or null for any overload. */
    final String descriptor;
    final At at;
    /** Which calls inside the method; null unless {@link #at} is an invoke point. */
    final CallSelector invoke;
    /** Which of the matching calls, counted from 0 within each method; negative for all. */
    final int ordinal;
    final Hook hook;
    volatile boolean active = true;

    public InjectionImpl(String classInternalName, String methodName, String descriptor, At at,
                         CallSelector invoke, int ordinal, Hook hook) {
        this.classInternalName = classInternalName;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.at = at;
        this.invoke = invoke;
        this.ordinal = ordinal;
        this.hook = hook;
    }

    boolean matches(String name, String desc) {
        return methodName.equals(name) && Descriptors.matches(descriptor, desc);
    }

    String describe() {
        return classInternalName.replace('/', '.') + "." + methodName + (descriptor == null ? "" : descriptor);
    }
}
