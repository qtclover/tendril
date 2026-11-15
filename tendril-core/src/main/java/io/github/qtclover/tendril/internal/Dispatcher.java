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
        if (hooks.length == 0 && site.ret.length == 0) {
            return null;
        }
        Guard guard = GUARD.get();
        if (guard.contains(siteId)) {
            return null;
        }
        CallInfoImpl call = new CallInfoImpl(site, owner, self, args);
        if (hooks.length > 0) {
            run(hooks, call, guard, true);
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
        run(hooks, call, GUARD.get(), false);
        return call.result;
    }

    private static void run(InjectionImpl[] hooks, CallInfoImpl call, Guard guard, boolean stopOnCancel) {
        guard.push(call.site.id);
        try {
            for (InjectionImpl injection : hooks) {
                if (!injection.active) {
                    continue;
                }
                injection.hook.handle(call);
                if (stopOnCancel && call.cancelled) {
                    break;
                }
            }
        } catch (Throwable t) {
            throw CallInfoImpl.<RuntimeException>sneaky(t);
        } finally {
            guard.pop();
        }
    }
}
