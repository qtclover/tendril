package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.CallInfo;
import io.github.qtclover.tendril.InvokedCall;

final class CallInfoImpl implements CallInfo {

    final Site site;
    private final Class<?> owner;
    private final Object self;
    /** The very array the woven code reads the arguments back from. */
    private final Object[] args;

    At at = At.HEAD;
    boolean cancelled;
    Object result;
    InvokedCallImpl invoked;
    Throwable thrown;
    boolean swallowed;
    /** Set when a hook threw: that exception is the hook's business, not something for throw hooks. */
    boolean hookFailed;

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
        args[index] = Values.fit(value, site.desc, index, owner, "argument " + index, site.name + site.desc);
    }

    @Override
    public Object getResult() {
        return result;
    }

    @Override
    public void setResult(Object value) {
        if (isInvokePoint()) {
            throw new IllegalStateException("At an invoke point the surrounding method " + site.name
                    + " cannot be made to return. To change the call itself use invoked().setResult()");
        }
        if (Values.returnsVoid(site.desc)) {
            throw new IllegalStateException(site.name + site.desc + " returns void, so there is no result to set"
                    + (at == At.HEAD ? "; use cancel() to skip the method" : ""));
        }
        result = Values.fit(value, site.desc, -1, owner, "the return value", site.name + site.desc);
        if (at == At.HEAD) {
            cancelled = true;
        } else if (at == At.THROW) {
            swallowed = true;
        }
    }

    @Override
    public Throwable getThrown() {
        return thrown;
    }

    @Override
    public void setThrown(Throwable throwable) {
        requireThrow("setThrown()");
        if (throwable == null) {
            throw new NullPointerException("throwable; use swallow() to drop the exception");
        }
        thrown = throwable;
        swallowed = false;
    }

    @Override
    public void swallow() {
        requireThrow("swallow()");
        if (site.name.equals("<init>")) {
            throw new IllegalStateException("An exception from a constructor cannot be swallowed: "
                    + "the object would be left half built");
        }
        if (!swallowed) {
            swallowed = true;
            result = Values.zero(site.desc);
        }
    }

    private void requireThrow(String what) {
        if (at != At.THROW) {
            throw new IllegalStateException(what + " only works in a hook at THROW; this hook runs at " + at);
        }
    }

    @Override
    public void cancel() {
        if (isInvokePoint()) {
            throw new IllegalStateException("cancel() only works at HEAD. Leaving " + site.name
                    + " from the middle would skip its finally blocks; use invoked().skip() to skip just the call");
        }
        if (at == At.THROW) {
            throw new IllegalStateException("cancel() only works at HEAD. To stop the exception from "
                    + site.name + " use swallow() or setResult()");
        }
        if (at != At.HEAD) {
            throw new IllegalStateException("cancel() only works at HEAD; " + site.name
                    + " has already run. Use setResult() to change what it returns.");
        }
        if (site.name.equals("<init>")) {
            throw new IllegalStateException("A constructor cannot be cancelled: the object would be left "
                    + "half built");
        }
        if (!cancelled) {
            cancelled = true;
            result = Values.zero(site.desc);
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public InvokedCall invoked() {
        if (!isInvokePoint()) {
            throw new IllegalStateException("invoked() is only available in hooks at INVOKE or AFTER_INVOKE; "
                    + "this hook runs at " + at);
        }
        return invoked;
    }

    private boolean isInvokePoint() {
        return Points.isInsideMethod(at);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= args.length) {
            throw new IndexOutOfBoundsException("Argument " + index + " requested, but " + site.name + site.desc
                    + " has " + args.length);
        }
    }
}
