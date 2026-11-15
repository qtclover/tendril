package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.agent.Bridge;

/**
 * The receiving end of the woven calls: finds the hooks for a site and runs them.
 *
 * <p>This code can run inside any hooked method, including JDK ones, so it is kept
 * deliberately plain: no lambdas, no string concatenation, no collections.
 */
final class Dispatcher implements Bridge.Handler {

    /**
     * Sites whose hooks are currently running on this thread. A hook that calls the
     * method it is hooking (directly or not) would otherwise recurse forever, so a
     * nested call to the same site simply runs the method without its hooks.
     */
    private static final class Guard {
        private int[] active = new int[8];
        private int depth;

        boolean contains(int id) {
            for (int i = 0; i < depth; i++) {
                if (active[i] == id) {
                    return true;
                }
            }
            return false;
        }

        void push(int id) {
            if (depth == active.length) {
                active = java.util.Arrays.copyOf(active, depth * 2);
            }
            active[depth++] = id;
        }

        void pop() {
            depth--;
        }
    }

    private static final class GuardLocal extends ThreadLocal<Guard> {
        @Override
        protected Guard initialValue() {
            return new Guard();
        }
    }

    private static final GuardLocal GUARD = new GuardLocal();

    private final Registry registry;

    private Dispatcher(Registry registry) {
        this.registry = registry;
    }

    static void install(Registry registry) {
        Bridge.Handler existing = Bridge.handler;
        if (existing != null) {
            throw new IllegalStateException("Another copy of Tendril (loaded by "
                    + existing.getClass().getClassLoader() + ") is already active in this JVM");
        }
        GUARD.get(); // Initialise on a safe thread, outside any hooked method.
        Bridge.handler = new Dispatcher(registry);
    }

    @Override
    public Object enter(int siteId, Class<?> owner, Object self, Object[] args) {
        Site site = registry.site(siteId);
        if (site == null) {
            return null;
        }
        InjectionImpl[] hooks = site.head;
        if (hooks.length == 0 && !site.hasHooks()) {
            return null;
        }
        Guard guard = GUARD.get();
        if (guard.contains(siteId)) {
            return null;
        }
        CallInfoImpl call = new CallInfoImpl(site, owner, self, args);
        if (hooks.length > 0) {
            run(hooks, call, guard, STOP_ON_CANCEL);
        }
        return call;
    }

    @Override
    public boolean cancelled(Object context) {
        return ((CallInfoImpl) context).cancelled;
    }

    @Override
    public Object result(Object context) {
        return ((CallInfoImpl) context).result;
    }

    @Override
    public Object exit(Object context, Object result) {
        CallInfoImpl call = (CallInfoImpl) context;
        InjectionImpl[] hooks = call.site.ret;
        if (hooks.length == 0) {
            return result;
        }
        call.at = At.RETURN;
        call.result = result;
        run(hooks, call, GUARD.get(), RUN_ALL);
        return call.result;
    }

    @Override
    public Throwable thrown(Object context, Throwable throwable) {
        CallInfoImpl call = (CallInfoImpl) context;
        InjectionImpl[] hooks = call.site.thr;
        if (hooks.length == 0 || call.hookFailed) {
            return throwable;
        }
        call.at = At.THROW;
        call.thrown = throwable;
        run(hooks, call, GUARD.get(), RUN_ALL);
        return call.swallowed ? null : call.thrown;
    }

    @Override
    public Object beforeCall(Object context, int pointId, Object receiver, Object[] args) {
        InvokePoint point = registry.point(pointId);
        if (point == null) {
            return null;
        }
        InjectionImpl[] hooks = point.before;
        if (hooks.length == 0 && point.after.length == 0) {
            return null;
        }
        CallInfoImpl call = (CallInfoImpl) context;
        InvokedCallImpl invoked = new InvokedCallImpl(point, call.owner(), receiver, args);
        if (hooks.length > 0) {
            call.at = At.INVOKE;
            call.invoked = invoked;
            run(hooks, call, GUARD.get(), STOP_ON_SKIP);
        }
        invoked.call = call;
        return invoked;
    }

    @Override
    public boolean skipped(Object callContext) {
        return ((InvokedCallImpl) callContext).skipped;
    }

    @Override
    public Object callResult(Object callContext) {
        return ((InvokedCallImpl) callContext).result;
    }

    @Override
    public Object afterCall(Object callContext, Object result) {
        InvokedCallImpl invoked = (InvokedCallImpl) callContext;
        InjectionImpl[] hooks = invoked.point.after;
        if (hooks.length == 0) {
            return result;
        }
        CallInfoImpl call = invoked.call;
        invoked.after = true;
        invoked.result = result;
        call.at = At.AFTER_INVOKE;
        call.invoked = invoked;
        run(hooks, call, GUARD.get(), RUN_ALL);
        return invoked.result;
    }

    private static final int RUN_ALL = 0;
    private static final int STOP_ON_CANCEL = 1;
    private static final int STOP_ON_SKIP = 2;

    private static void run(InjectionImpl[] hooks, CallInfoImpl call, Guard guard, int mode) {
        guard.push(call.site.id);
        try {
            for (InjectionImpl injection : hooks) {
                if (!injection.active) {
                    continue;
                }
                injection.hook.handle(call);
                if (mode == STOP_ON_CANCEL && call.cancelled) {
                    break;
                }
                if (mode == STOP_ON_SKIP && call.invoked.skipped) {
                    break;
                }
            }
        } catch (Throwable t) {
            call.hookFailed = true;
            throw Values.<RuntimeException>sneaky(t);
        } finally {
            guard.pop();
        }
    }
}
