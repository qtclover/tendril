package io.github.qtclover.tendril.internal;

import org.objectweb.asm.Type;

import java.lang.invoke.MethodType;

/** Checking and converting the values hooks hand back. */
final class Values {

    private Values() {
    }

    /**
     * Checks that {@code value} can be stored where parameter {@code index} of
     * {@code descriptor} (or its return type, for index -1) is expected, converting
     * between numeric boxes. Failing here gives a readable error in the hook,
     * instead of a ClassCastException from inside the hooked method.
     *
     * @param context a class whose loader can resolve the types in the descriptor, or null
     */
    static Object fit(Object value, String descriptor, int index, Class<?> context, String what, String method) {
        Type type = index < 0 ? Type.getReturnType(descriptor) : Type.getArgumentTypes(descriptor)[index];
        switch (type.getSort()) {
            case Type.BOOLEAN:
                if (value instanceof Boolean) {
                    return value;
                }
                break;
            case Type.CHAR:
                if (value instanceof Character) {
                    return value;
                }
                break;
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
            case Type.LONG:
            case Type.FLOAT:
            case Type.DOUBLE:
                if (value instanceof Number number) {
                    return convert(number, type.getSort());
                }
                break;
            default:
                if (value == null) {
                    return null;
                }
                Class<?> expected = resolve(descriptor, index, context);
                if (expected == null || expected.isInstance(value)) {
                    return value;
                }
                break;
        }
        throw new IllegalArgumentException("Cannot use " + (value == null ? "null" : value.getClass().getName())
                + " as " + what + " of " + method + ": expected " + type.getClassName());
    }

    private static Object convert(Number n, int sort) {
        switch (sort) {
            case Type.BYTE:
                return n.byteValue();
            case Type.SHORT:
                return n.shortValue();
            case Type.INT:
                return n.intValue();
            case Type.LONG:
                return n.longValue();
            case Type.FLOAT:
                return n.floatValue();
            default:
                return n.doubleValue();
        }
    }

    private static Class<?> resolve(String descriptor, int index, Class<?> context) {
        if (context == null) {
            return null;
        }
        try {
            MethodType type = MethodType.fromMethodDescriptorString(descriptor, context.getClassLoader());
            return index < 0 ? type.returnType() : type.parameterType(index);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** What a skipped or cancelled call returns when no value was given. */
    static Object zero(String descriptor) {
        switch (Type.getReturnType(descriptor).getSort()) {
            case Type.BOOLEAN:
                return Boolean.FALSE;
            case Type.CHAR:
                return (char) 0;
            case Type.BYTE:
                return (byte) 0;
            case Type.SHORT:
                return (short) 0;
            case Type.INT:
                return 0;
            case Type.LONG:
                return 0L;
            case Type.FLOAT:
                return 0f;
            case Type.DOUBLE:
                return 0d;
            default:
                return null;
        }
    }

    static boolean returnsVoid(String descriptor) {
        return Type.getReturnType(descriptor).getSort() == Type.VOID;
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> E sneaky(Throwable t) throws E {
        throw (E) t;
    }
}
