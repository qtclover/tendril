package io.github.qtclover.tendril;

import io.github.qtclover.tendril.internal.CallSelector;
import io.github.qtclover.tendril.internal.Descriptors;
import io.github.qtclover.tendril.internal.InjectionImpl;
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
     * @param className  binary name of the target class, e.g. {@code com.example.Foo$Bar}
     * @param descriptor full or parameter-only JVM descriptor, or null for every overload
     */
    public InjectionBatch add(String className, String methodName, String descriptor, At at, Hook hook) {
        return add(className, methodName, descriptor, at, null, -1, hook);
    }

    /**
     * Like {@link #add(String, String, String, At, Hook)}, for {@link At#INVOKE} and
     * {@link At#AFTER_INVOKE}.
     *
     * @param invoke  which calls inside the method: {@code "name"}, {@code "Owner.name"}, or
     *                either followed by a full or parameter-only descriptor
     * @param ordinal which of the matching calls in each method, counted from 0; negative for all
     */
    public InjectionBatch add(String className, String methodName, String descriptor, At at,
                              String invoke, int ordinal, Hook hook) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(hook, "hook");
        if (methodName.equals("<init>") || methodName.equals("<clinit>")) {
            throw new InjectionException("Constructors and static initialisers cannot be hooked (yet): "
                    + className + "." + methodName);
        }
        String normalised = descriptor == null || descriptor.isEmpty() ? null : Descriptors.validate(descriptor);
        boolean invokePoint = at == At.INVOKE || at == At.AFTER_INVOKE;
        boolean hasSelector = invoke != null && !invoke.isBlank();
        if (invokePoint != hasSelector) {
            throw new InjectionException(invokePoint
                    ? "A hook at " + at + " must say which call it is for, e.g. \"PricingService.priceOf\""
                    : "A call selector (\"" + invoke + "\") only makes sense at INVOKE or AFTER_INVOKE, not " + at);
        }
        CallSelector selector = invokePoint ? CallSelector.parse(invoke) : null;
        pending.add(new InjectionImpl(className.replace('.', '/'), methodName, normalised, at,
                selector, ordinal, hook));
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
