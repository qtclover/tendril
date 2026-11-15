package io.github.qtclover.tendril.internal;

/**
 * One hooked method. The woven bytecode carries only {@link #id}; everything else
 * is looked up here at call time, which is what lets hooks be added and removed
 * without touching the bytecode again.
 */
final class Site {

    static final InjectionImpl[] NONE = new InjectionImpl[0];

    final int id;
    final String owner;
    final String name;
    final String desc;

    volatile InjectionImpl[] head = NONE;
    volatile InjectionImpl[] ret = NONE;
    /** Number of hooks on calls inside this method. */
    volatile int invokeHooks;

    Site(int id, String owner, String name, String desc) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    boolean hasHooks() {
        return head.length > 0 || ret.length > 0 || invokeHooks > 0;
    }
}
