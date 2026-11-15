package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.CallInfo;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodType;

final class CallInfoImpl implements CallInfo {

    final Site site;
    private final Class<?> owner;
    private final Object self;
    /** The very array the woven code reads the arguments back from. */
    private final Object[] args;

    At at = At.HEAD;
    boolean cancelled;
    Object result;

    CallInfoImpl(Site site, Class<?> owner, Object self, Object[] args) {
        this.site = site;
        this.owner = owner;
        this.self = self;
        this.args = args;
    }

    @Override
    public At at() {
        return at;
    }

    @Override
    public Class<?> owner() {
        return owner;
    }

    @Override
    public String methodName() {
        return site.name;
    }

    @Override
    public String descriptor() {
        return site.desc;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T self() {
        return (T) self;
    }

    @Override
    public int argCount() {
        return args.length;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T arg(int index) {
        checkIndex(index);
        return (T) args[index];
    }

    @Override
    public Object[] args() {
        return args.clone();
    }

    @Override
    public void setArg(int index, Object value) {
        checkIndex(index);
        Type type = Type.getArgumentTypes(site.desc)[index];
        args[index] = fit(value, type, "argument " + index, index);
    }

    @Override
    public Object getResult() {
        return result;
    }

    @Override
    public void setResult(Object value) {
        Type type = Type.getReturnType(site.desc);
        if (type.getSort() == Type.VOID) {
            throw new IllegalStateException(site.name + site.desc + " returns void, so there is no result to set"
                    + (at == At.HEAD ? "; use cancel() to skip the method" : ""));
        }
        result = fit(value, type, "the return value", -1);
        if (at == At.HEAD) {
            cancelled = true;
        }
    }

    @Override
    public void cancel() {
        if (at != At.HEAD) {
            throw new IllegalStateException("cancel() only works at HEAD; " + site.name
                    + " has already run. Use setResult() to change what it returns.");
        }
        if (!cancelled) {
            cancelled = true;
            result = zero(Type.getReturnType(site.desc));
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= args.length) {
            throw new IndexOutOfBoundsException("Argument " + index + " requested, but " + site.name + site.desc
                    + " has " + args.length);
        }
    }

    /**
     * Checks that {@code value} can be stored where a {@code type} is expected and
     * converts between numeric boxes. Failing here gives a readable error in the
     * hook, instead of a ClassCastException from inside the hooked method.
     */
    private Object fit(Object value, Type type, String what, int argIndex) {
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
                Class<?> expected = resolve(argIndex);
                if (expected == null || expected.isInstance(value)) {
                    return value;
                }
                break;
        }
        throw new IllegalArgumentException("Cannot use " + (value == null ? "null" : value.getClass().getName())
                + " as " + what + " of " + site.name + site.desc + ": expected " + type.getClassName());
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

    /** The real class of a reference parameter (or of the return type for -1); null if it cannot be determined. */
    private Class<?> resolve(int argIndex) {
        if (owner == null) {
            return null;
        }
        try {
            MethodType type = MethodType.fromMethodDescriptorString(site.desc, owner.getClassLoader());
            return argIndex < 0 ? type.returnType() : type.parameterType(argIndex);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static Object zero(Type type) {
        switch (type.getSort()) {
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

    @SuppressWarnings("unchecked")
    static <E extends Throwable> E sneaky(Throwable t) throws E {
        throw (E) t;
    }
}
