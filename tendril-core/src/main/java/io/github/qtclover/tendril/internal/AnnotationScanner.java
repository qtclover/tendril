package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.CallInfo;
import io.github.qtclover.tendril.Hook;
import io.github.qtclover.tendril.Inject;
import io.github.qtclover.tendril.InjectionBatch;
import io.github.qtclover.tendril.InjectionException;
import io.github.qtclover.tendril.Injector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Turns {@link Inject}-annotated methods into hooks. Internal. */
public final class AnnotationScanner {

    private AnnotationScanner() {
    }

    /** @param instance receiver for instance methods, or null to accept static methods only */
    public static InjectionBatch scan(Class<?> type, Object instance) {
        InjectionBatch batch = Injector.batch();
        int found = 0;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                Inject inject = method.getAnnotation(Inject.class);
                if (inject == null) {
                    continue;
                }
                found++;
                batch.add(targetName(inject, method), inject.method(), inject.descriptor(), inject.at(),
                        toHook(method, instance));
            }
        }
        if (found == 0) {
            throw new InjectionException(type.getName() + " has no @Inject methods");
        }
        return batch;
    }

    private static String targetName(Inject inject, Method method) {
        boolean byClass = inject.target() != void.class;
        boolean byName = !inject.targetName().isEmpty();
        if (byClass == byName) {
            throw new InjectionException("@Inject on " + describe(method)
                    + " must set exactly one of target and targetName");
        }
        return byClass ? inject.target().getName() : inject.targetName();
    }

    private static Hook toHook(Method method, Object instance) {
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        if (!isStatic && instance == null) {
            throw new InjectionException("@Inject method " + describe(method) + " is not static. "
                    + "Make it static, or pass an instance to Injector.register(Object)");
        }
        Class<?>[] parameters = method.getParameterTypes();
        boolean takesCall = parameters.length == 1 && parameters[0] == CallInfo.class;
        if (!takesCall && parameters.length != 0) {
            throw new InjectionException("@Inject method " + describe(method)
                    + " must take a single CallInfo, or no parameters");
        }
        MethodHandle handle;
        try {
            method.setAccessible(true);
            handle = MethodHandles.lookup().unreflect(method);
        } catch (RuntimeException | IllegalAccessException e) {
            throw new InjectionException("Cannot access @Inject method " + describe(method)
                    + "; if it is in a named module, open its package to Tendril", e);
        }
        MethodHandle bound = isStatic ? handle : handle.bindTo(instance);
        return takesCall ? new CallHook(bound) : new PlainHook(bound);
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    private record CallHook(MethodHandle handle) implements Hook {
        @Override
        public void handle(CallInfo call) throws Throwable {
            handle.invoke(call);
        }
    }

    private record PlainHook(MethodHandle handle) implements Hook {
        @Override
        public void handle(CallInfo call) throws Throwable {
            handle.invoke();
        }
    }
}
