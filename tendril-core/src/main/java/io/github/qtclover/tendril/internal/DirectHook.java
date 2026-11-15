package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.InjectionException;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * A static method called straight from the hooked method, with the hooked method's
 * own receiver and arguments: no dispatcher, no boxing, no allocation.
 *
 * @param owner            internal name of the class declaring the hook method
 * @param targetDescriptor descriptor of the method the hook was matched to
 */
public record DirectHook(String owner, String name, String descriptor, String targetDescriptor) {

    String key() {
        return owner + "." + name + descriptor;
    }

    /**
     * Finds the one method of {@code target} that {@code hook}'s parameters fit, and
     * checks everything that would otherwise only fail inside the rewritten class.
     */
    public static DirectHook resolve(Class<?> target, String methodName, At at, Method hook) {
        String label = hook.getDeclaringClass().getName() + "." + hook.getName();
        if (at != At.HEAD && at != At.RETURN) {
            throw new InjectionException("Direct hooks exist for HEAD and RETURN only, not " + at + ": " + label);
        }
        if (!Modifier.isStatic(hook.getModifiers()) || hook.getReturnType() != void.class) {
            throw new InjectionException("A direct hook must be a static void method: " + label);
        }
        for (Class<?> c = hook.getDeclaringClass(); c != null; c = c.getEnclosingClass()) {
            if (!Modifier.isPublic(c.getModifiers())) {
                throw new InjectionException("A direct hook is called from inside " + target.getName()
                        + ", so it must be a public method of a public class: " + label);
            }
        }
        if (!Modifier.isPublic(hook.getModifiers())) {
            throw new InjectionException("A direct hook must be public: " + label);
        }
        try {
            if (Class.forName(hook.getDeclaringClass().getName(), false, target.getClassLoader())
                    != hook.getDeclaringClass()) {
                throw new ClassNotFoundException();
            }
        } catch (ClassNotFoundException | LinkageError e) {
            throw new InjectionException(target.getName() + " cannot see " + hook.getDeclaringClass().getName()
                    + " from its class loader, so it cannot call a direct hook there. "
                    + "Use an ordinary hook, which goes through the bootstrap bridge.");
        }

        List<Executable> candidates = new ArrayList<>();
        if (methodName.equals("<init>")) {
            candidates.addAll(List.of(target.getDeclaredConstructors()));
        } else {
            for (Method method : target.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && !Modifier.isAbstract(method.getModifiers())
                        && !Modifier.isNative(method.getModifiers())) {
                    candidates.add(method);
                }
            }
        }
        List<String> fitting = new ArrayList<>();
        List<String> expectedShapes = new ArrayList<>();
        for (Executable candidate : candidates) {
            List<Class<?>> expected = new ArrayList<>();
            if (!Modifier.isStatic(candidate.getModifiers())) {
                expected.add(target);
            }
            expected.addAll(List.of(candidate.getParameterTypes()));
            Class<?> returns = candidate instanceof Method m ? m.getReturnType() : void.class;
            if (at == At.RETURN && returns != void.class) {
                expected.add(returns);
            }
            expectedShapes.add(shape(expected));
            if (fits(hook.getParameterTypes(), expected)) {
                fitting.add(candidate instanceof Method m
                        ? Type.getMethodDescriptor(m)
                        : Type.getConstructorDescriptor((Constructor<?>) candidate));
            }
        }
        if (fitting.size() != 1) {
            throw new InjectionException("The parameters of direct hook " + label + " "
                    + shape(List.of(hook.getParameterTypes())) + " fit "
                    + (fitting.isEmpty() ? "no" : "more than one") + " method " + target.getName() + "."
                    + methodName + ". A direct hook takes the receiver (unless the method is static), then the "
                    + "method's parameters, then for RETURN its result. Expected one of: " + expectedShapes);
        }
        return new DirectHook(Type.getInternalName(hook.getDeclaringClass()), hook.getName(),
                Type.getMethodDescriptor(hook), fitting.get(0));
    }

    private static boolean fits(Class<?>[] actual, List<Class<?>> expected) {
        if (actual.length != expected.size()) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            Class<?> want = expected.get(i);
            // Primitives must match exactly: there is no conversion in a plain call.
            if (want.isPrimitive() || actual[i].isPrimitive()
                    ? actual[i] != want
                    : !actual[i].isAssignableFrom(want)) {
                return false;
            }
        }
        return true;
    }

    private static String shape(List<Class<?>> types) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> type : types) {
            sb.append(sb.length() > 1 ? ", " : "").append(type.getSimpleName());
        }
        return sb.append(')').toString();
    }
}
