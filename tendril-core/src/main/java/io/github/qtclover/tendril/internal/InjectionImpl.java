package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.Hook;

import java.util.HashMap;
import java.util.Map;

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
    /** Whether the hook wants the local variables in scope at its point. */
    final boolean locals;
    final Hook hook;
    /** For a direct hook: the static method to call. Then {@link #hook} is null. */
    final DirectHook direct;
    /** For a pattern hook: which classes it applies to. Null for a hook on one named class. */
    final TypeMatcher scope;
    /** For the copy of a pattern hook made for one matching class: the pattern hook. */
    final InjectionImpl template;
    /** For a pattern hook: its copies, by internal class name. Guarded by itself. */
    final Map<String, InjectionImpl> copies = new HashMap<>();
    volatile boolean active = true;

    public InjectionImpl(String className, String methodName, String descriptor, At at,
                         CallSelector invoke, int ordinal, boolean locals, Hook hook) {
        this(className.replace('.', '/'), TypeMatcher.parse(className), null, methodName, descriptor, at,
                invoke, ordinal, locals, hook);
    }

    /** A direct hook on one method of one class. */
    public InjectionImpl(String className, String methodName, At at, DirectHook direct) {
        this(className.replace('.', '/'), null, null, methodName, direct.targetDescriptor(), at, null, -1,
                false, null, direct);
    }

    private InjectionImpl(String classInternalName, TypeMatcher scope, InjectionImpl template, String methodName,
                          String descriptor, At at, CallSelector invoke, int ordinal, boolean locals, Hook hook) {
        this(classInternalName, scope, template, methodName, descriptor, at, invoke, ordinal, locals, hook, null);
    }

    private InjectionImpl(String classInternalName, TypeMatcher scope, InjectionImpl template, String methodName,
                          String descriptor, At at, CallSelector invoke, int ordinal, boolean locals, Hook hook,
                          DirectHook direct) {
        this.direct = direct;
        this.scope = scope;
        this.template = template;
        this.classInternalName = classInternalName;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.at = at;
        this.invoke = invoke;
        this.ordinal = ordinal;
        this.locals = locals;
        this.hook = hook;
    }

    /** The copy of this pattern hook for one class, created on first request. Null if one already exists. */
    InjectionImpl copyFor(String internalName) {
        synchronized (copies) {
            if (!active || copies.containsKey(internalName)) {
                return null;
            }
            InjectionImpl copy = new InjectionImpl(internalName, null, this, methodName, descriptor, at,
                    invoke, ordinal, locals, hook);
            copies.put(internalName, copy);
            return copy;
        }
    }

    boolean matches(String name, String desc) {
        return methodName.equals(name) && Descriptors.matches(descriptor, desc);
    }

    String describe() {
        return classInternalName.replace('/', '.') + "." + methodName + (descriptor == null ? "" : descriptor);
    }
}
