package io.github.qtclover.tendril;

import io.github.qtclover.tendril.internal.CallSelector;
import io.github.qtclover.tendril.internal.Descriptors;
import io.github.qtclover.tendril.internal.InjectionImpl;
import io.github.qtclover.tendril.internal.Points;
import io.github.qtclover.tendril.internal.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Several hooks installed together. Each affected class is re-transformed once
 * instead of once per hook, and the returned {@link Injection} removes them all.
 * Installation is all-or-nothing.
 */
public final class InjectionBatch {

    private final List<InjectionImpl> pending = new ArrayList<>();

    InjectionBatch() {
    }

    /**
     * @param className  binary name of the target class, e.g. {@code com.example.Foo$Bar}; or a
     *                   pattern: {@code com.example.*} for every class whose name matches, or
     *                   {@code com.example.Shape+} for Shape and everything that extends or
     *                   implements it
     * @param descriptor full or parameter-only JVM descriptor, or null for every overload
     */
    public InjectionBatch add(String className, String methodName, String descriptor, At at, Hook hook) {
        return add(className, methodName, descriptor, at, null, -1, false, hook);
    }

    /**
     * Like {@link #add(String, String, String, At, Hook)}, for {@link At#INVOKE} and
     * {@link At#AFTER_INVOKE}.
     *
     * @param invoke  which calls inside the method: {@code "name"}, {@code "Owner.name"}, or
     *                either followed by a full or parameter-only descriptor; {@code "new Owner"}
     *                for constructor calls; for the field points, {@code "field"} or
     *                {@code "Owner.field"}
     * @param ordinal which of the matching calls in each method, counted from 0; negative for all
     */
    public InjectionBatch add(String className, String methodName, String descriptor, At at,
                              String invoke, int ordinal, Hook hook) {
        return add(className, methodName, descriptor, at, invoke, ordinal, false, hook);
    }

    /**
     * Like {@link #add(String, String, String, At, String, int, Hook)}.
     *
     * @param locals whether the hook may read and change the local variables of the
     *               surrounding method through {@link CallInfo#local}. Needs a class
     *               compiled with debug information.
     */
    public InjectionBatch add(String className, String methodName, String descriptor, At at,
                              String invoke, int ordinal, boolean locals, Hook hook) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(hook, "hook");
        if (methodName.equals("<clinit>")) {
            throw new InjectionException("Static initialisers cannot be hooked: " + className);
        }
        String normalised = descriptor == null || descriptor.isEmpty() ? null : Descriptors.validate(descriptor);
        boolean invokePoint = Points.isInsideMethod(at);
        boolean hasSelector = invoke != null && !invoke.isBlank();
        if (invokePoint != hasSelector) {
            throw new InjectionException(invokePoint
                    ? "A hook at " + at + " must say which call or field it is for, e.g. \"PricingService.priceOf\""
                    : "A call or field selector (\"" + invoke + "\") makes no sense at " + at);
        }
        if (locals && !invokePoint) {
            throw new InjectionException("Local variables can only be captured at points inside a method, not at "
                    + at);
        }
        CallSelector selector = switch (at) {
            case HEAD, RETURN, THROW -> null;
            case INVOKE, AFTER_INVOKE -> CallSelector.parse(invoke);
            case FIELD_READ -> CallSelector.parseField(invoke, false);
            case FIELD_WRITE -> CallSelector.parseField(invoke, true);
        };
        pending.add(new InjectionImpl(className.trim(), methodName, normalised, at,
                selector, ordinal, locals, hook));
        return this;
    }

    public InjectionBatch add(Class<?> target, String methodName, String descriptor, At at, Hook hook) {
        return add(target.getName(), methodName, descriptor, at, hook);
    }

    /** Attaches if necessary and installs everything added so far. */
    public Injection install() {
        Injector.attach();
        List<InjectionImpl> list = List.copyOf(pending);
        pending.clear();
        return Registry.get().install(list);
    }
}
