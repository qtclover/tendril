package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.InvokedCall;

final class InvokedCallImpl implements InvokedCall {

    final InvokePoint point;
    /** A class whose loader can resolve the callee's parameter types: the class making the call. */
    private final Class<?> caller;
    private final Object receiver;
    /** The very array the woven code reads the call's arguments back from. */
    private final Object[] args;

    /** The surrounding method's call; set once the before-hooks have run. */
    CallInfoImpl call;
    boolean after;
    boolean skipped;
    Object result;

    InvokedCallImpl(InvokePoint point, Class<?> caller, Object receiver, Object[] args) {
        this.point = point;
        this.caller = caller;
        this.receiver = receiver;
        this.args = args;
    }

    @Override
    public String owner() {
        return point.call.owner().replace('/', '.');
    }

    @Override
    public String name() {
        return point.call.name();
    }

    @Override
    public String descriptor() {
        return point.call.desc();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T receiver() {
        return (T) receiver;
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
        if (after) {
            throw new IllegalStateException("The call to " + name() + " has already been made; "
                    + "arguments can only be changed at INVOKE");
        }
        args[index] = Values.fit(value, descriptor(), index, caller, "argument " + index, label());
    }

    @Override
    public void skip() {
        if (after) {
            throw new IllegalStateException("The call to " + name() + " has already been made; "
                    + "skip() only works at INVOKE. Use setResult() to change what it returned.");
        }
        if (point.call.kind() == CallRef.Kind.NEW) {
            throw new IllegalStateException("A constructor call can only be skipped with an object "
                    + "to use instead: skip(replacement)");
        }
        if (!skipped) {
            skipped = true;
            result = Values.zero(descriptor());
        }
    }

    @Override
    public boolean isSkipped() {
        return skipped;
    }

    @Override
    public Object getResult() {
        return result;
    }

    @Override
    public void setResult(Object value) {
        String resultDescriptor = point.call.resultDescriptor();
        if (Values.returnsVoid(resultDescriptor)) {
            throw new IllegalStateException(label() + " returns void, so there is no result to set"
                    + (after ? "" : "; use skip() to skip the call"));
        }
        if (value == null && point.call.kind() == CallRef.Kind.NEW) {
            throw new IllegalArgumentException("A new expression cannot produce null");
        }
        result = Values.fit(value, resultDescriptor, -1, caller, "the result", label());
        if (!after) {
            skipped = true;
        }
    }

    private String label() {
        return name() + descriptor();
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= args.length) {
            throw new IndexOutOfBoundsException("Argument " + index + " requested, but the call to " + label()
                    + " has " + args.length);
        }
    }
}
