package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.At;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything Tendril knows about one target class (by name). All state is guarded by {@code this}. */
final class ClassPlan {

    final String internalName;

    private final List<InjectionImpl> injections = new ArrayList<>();
    /** Keyed by method name + descriptor. Sites are never removed, only emptied. */
    private final Map<String, Site> sites = new HashMap<>();
    /** Hookable methods of the class as last seen by the transformer; null until then. */
    private Set<String> methods;
    /** Methods that carry woven code in the currently defined class. */
    private Set<String> woven = Set.of();
    /** Bumped on every change, so the transformer can tell its snapshot went stale. */
    private int version;
    /** Set by the transformer when weaving fails; the JVM swallows exceptions thrown there. */
    volatile Throwable lastError;

    ClassPlan(String internalName) {
        this.internalName = internalName;
    }

    synchronized void add(Collection<InjectionImpl> added) {
        injections.addAll(added);
        rebuild();
    }

    synchronized void remove(Collection<InjectionImpl> removed) {
        injections.removeAll(removed);
        rebuild();
    }

    synchronized List<InjectionImpl> clear() {
        List<InjectionImpl> all = new ArrayList<>(injections);
        injections.clear();
        rebuild();
        return all;
    }

    synchronized int version() {
        return version;
    }

    synchronized boolean isEmpty() {
        return injections.isEmpty();
    }

    /** True if the class as currently defined lacks woven code it should have, or has code it no longer needs. */
    synchronized boolean outOfDate() {
        if (methods == null) {
            return !injections.isEmpty();
        }
        Set<String> wanted = new HashSet<>();
        for (String key : methods) {
            if (wantsHooks(key)) {
                wanted.add(key);
            }
        }
        return !wanted.equals(woven);
    }

    /** Whether {@code injection} matches a method of the class. Null if the class has not been seen yet. */
    synchronized Boolean matchesAnything(InjectionImpl injection) {
        if (methods == null) {
            return null;
        }
        for (String key : methods) {
            int split = key.indexOf('(');
            if (injection.matches(key.substring(0, split), key.substring(split))) {
                return true;
            }
        }
        return false;
    }

    synchronized Set<String> knownMethods() {
        return methods == null ? Set.of() : Set.copyOf(methods);
    }

    /**
     * Called by the transformer with the hookable methods of the class it is about to
     * define. Returns the site for each method that needs weaving, and records that
     * decision as the new state of the class.
     */
    synchronized Map<String, Site> planWeave(Set<String> hookableMethods, Registry registry) {
        methods = hookableMethods;
        Map<String, Site> result = new LinkedHashMap<>();
        for (String key : hookableMethods) {
            if (!wantsHooks(key)) {
                continue;
            }
            Site site = sites.get(key);
            if (site == null) {
                int split = key.indexOf('(');
                site = registry.newSite(internalName, key.substring(0, split), key.substring(split));
                sites.put(key, site);
                fill(site);
            }
            result.put(key, site);
        }
        woven = Set.copyOf(result.keySet());
        return result;
    }

    synchronized void weaveFailed() {
        woven = Set.of();
    }

    private boolean wantsHooks(String key) {
        int split = key.indexOf('(');
        String name = key.substring(0, split);
        String desc = key.substring(split);
        for (InjectionImpl injection : injections) {
            if (injection.matches(name, desc)) {
                return true;
            }
        }
        return false;
    }

    private void rebuild() {
        version++;
        for (Site site : sites.values()) {
            fill(site);
        }
    }

    private void fill(Site site) {
        List<InjectionImpl> head = new ArrayList<>();
        List<InjectionImpl> ret = new ArrayList<>();
        for (InjectionImpl injection : injections) {
            if (injection.matches(site.name, site.desc)) {
                (injection.at == At.HEAD ? head : ret).add(injection);
            }
        }
        site.head = head.isEmpty() ? Site.NONE : head.toArray(Site.NONE);
        site.ret = ret.isEmpty() ? Site.NONE : ret.toArray(Site.NONE);
    }
}
