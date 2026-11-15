package io.github.qtclover.tendril;

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
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(hook, "hook");
        if (methodName.equals("<init>") || methodName.equals("<clinit>")) {
            throw new InjectionException("Constructors and static initialisers cannot be hooked (yet): "
                    + className + "." + methodName);
        }
        String normalised = descriptor == null || descriptor.isEmpty() ? null : Descriptors.validate(descriptor);
        pending.add(new InjectionImpl(className.replace('.', '/'), methodName, normalised, at, hook));
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
