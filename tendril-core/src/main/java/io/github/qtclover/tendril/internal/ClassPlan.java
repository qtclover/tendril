package io.github.qtclover.tendril.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Everything Tendril knows about one target class (by name). All state is guarded by {@code this}. */
final class ClassPlan {

    /** What to weave into one method: its site, and the calls inside it to wrap, by call index. */
    record Weave(Site site, Map<Integer, InvokePoint> points) {
    }

    final String internalName;

    private final List<InjectionImpl> injections = new ArrayList<>();
    /** Keyed by method name + descriptor. Sites and points are never removed, only emptied. */
    private final Map<String, Site> sites = new HashMap<>();
    /** Keyed by method key + '#' + call index. */
    private final Map<String, InvokePoint> points = new HashMap<>();
    /** Hookable methods of the class and the calls each makes, as last seen by the transformer; null until then. */
    private Map<String, List<CallRef>> methods;
    /** Method keys and point keys that carry woven code in the currently defined class. */
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

    /** True if the class as currently defined lacks woven code it should have, or has code it no longer needs. */
    synchronized boolean outOfDate() {
        if (methods == null) {
            return !injections.isEmpty();
        }
        return !wanted().equals(woven);
    }

    /** Whether {@code injection} has anywhere to go in the class. Null if the class has not been seen yet. */
    synchronized Boolean matchesAnything(InjectionImpl injection) {
        if (methods == null) {
            return null;
        }
        for (String key : methods.keySet()) {
            if (applies(injection, key)) {
                return true;
            }
        }
        return false;
    }

    /** Active injections that have nowhere to go in the class as last seen. */
    synchronized List<InjectionImpl> unmatched() {
        List<InjectionImpl> result = new ArrayList<>();
        for (InjectionImpl injection : injections) {
            if (Boolean.FALSE.equals(matchesAnything(injection))) {
                result.add(injection);
            }
        }
        return result;
    }

    /** Why {@code injection} matched nothing, with the alternatives that do exist. */
    synchronized String explainNoMatch(InjectionImpl injection) {
        Set<String> calls = new TreeSet<>();
        boolean methodFound = false;
        for (Map.Entry<String, List<CallRef>> method : methods.entrySet()) {
            if (matchesMethod(injection, method.getKey())) {
                methodFound = true;
                for (CallRef call : method.getValue()) {
                    if (!call.name().startsWith("<")) {
                        calls.add(call.describe());
                    }
                }
            }
        }
        if (methodFound && injection.invoke != null) {
            return "No call matching \"" + injection.invoke + "\""
                    + (injection.ordinal >= 0 ? " with ordinal " + injection.ordinal : "")
                    + " in " + injection.describe() + ". Calls made there: " + calls;
        }
        return "No hookable method matches " + injection.describe()
                + ". Only methods declared in the class itself, with a body, can be hooked. "
                + "Candidates: " + new TreeSet<>(methods.keySet());
    }

    /**
     * Called by the transformer with the hookable methods of the class it is about to
     * define. Returns what to weave into each method that needs it, and records that
     * decision as the new state of the class.
     */
    synchronized Map<String, Weave> planWeave(Map<String, List<CallRef>> hookableMethods, Registry registry) {
        methods = hookableMethods;
        Map<String, Weave> result = new LinkedHashMap<>();
        Set<String> nowWoven = new HashSet<>();
        for (Map.Entry<String, List<CallRef>> method : hookableMethods.entrySet()) {
            String key = method.getKey();
            Set<Integer> calls = wantedCalls(key);
            if (calls.isEmpty() && !wantsMethodHooks(key)) {
                continue;
            }
            Site site = sites.get(key);
            if (site == null) {
                int split = key.indexOf('(');
                site = registry.newSite(internalName, key.substring(0, split), key.substring(split));
                sites.put(key, site);
            }
            fill(site);
            nowWoven.add(key);
            Map<Integer, InvokePoint> wrapped = new HashMap<>();
            for (int index : calls) {
                String pointKey = key + "#" + index;
                InvokePoint point = points.get(pointKey);
                if (point == null) {
                    point = registry.newPoint(site, index, method.getValue().get(index));
                    points.put(pointKey, point);
                }
                fill(point);
                wrapped.put(index, point);
                nowWoven.add(pointKey);
            }
            result.put(key, new Weave(site, wrapped));
        }
        woven = nowWoven;
        return result;
    }

    synchronized void weaveFailed() {
        woven = Set.of();
    }

    // ---- matching ----------------------------------------------------------

    private static boolean matchesMethod(InjectionImpl injection, String key) {
        int split = key.indexOf('(');
        return injection.matches(key.substring(0, split), key.substring(split));
    }

    /** Indices of the calls in a method that an invoke injection selects. */
    private Set<Integer> callIndices(InjectionImpl injection, String key) {
        if (injection.invoke == null || !matchesMethod(injection, key)) {
            return Set.of();
        }
        Set<Integer> result = new HashSet<>();
        List<CallRef> calls = methods.get(key);
        if (calls == null) {
            return Set.of();
        }
        int seen = 0;
        for (int i = 0; i < calls.size(); i++) {
            if (injection.invoke.matches(calls.get(i))) {
                if (injection.ordinal < 0 || injection.ordinal == seen) {
                    result.add(i);
                }
                seen++;
            }
        }
        return result;
    }

    private boolean applies(InjectionImpl injection, String key) {
        return injection.invoke == null ? matchesMethod(injection, key) : !callIndices(injection, key).isEmpty();
    }

    private boolean wantsMethodHooks(String key) {
        for (InjectionImpl injection : injections) {
            if (injection.invoke == null && matchesMethod(injection, key)) {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> wantedCalls(String key) {
        Set<Integer> result = new HashSet<>();
        for (InjectionImpl injection : injections) {
            result.addAll(callIndices(injection, key));
        }
        return result;
    }

    private Set<String> wanted() {
        Set<String> result = new HashSet<>();
        for (String key : methods.keySet()) {
            Set<Integer> calls = wantedCalls(key);
            if (!calls.isEmpty() || wantsMethodHooks(key)) {
                result.add(key);
            }
            for (int index : calls) {
                result.add(key + "#" + index);
            }
        }
        return result;
    }

    // ---- hook tables -------------------------------------------------------

    private void rebuild() {
        version++;
        if (methods == null) {
            return;
        }
        for (Site site : sites.values()) {
            fill(site);
        }
        for (InvokePoint point : points.values()) {
            fill(point);
        }
    }

    private void fill(Site site) {
        String key = site.name + site.desc;
        List<InjectionImpl> head = new ArrayList<>();
        List<InjectionImpl> ret = new ArrayList<>();
        List<InjectionImpl> thr = new ArrayList<>();
        int invokeHooks = 0;
        for (InjectionImpl injection : injections) {
            if (injection.invoke != null) {
                if (!callIndices(injection, key).isEmpty()) {
                    invokeHooks++;
                }
            } else if (matchesMethod(injection, key)) {
                switch (injection.at) {
                    case HEAD -> head.add(injection);
                    case RETURN -> ret.add(injection);
                    case THROW -> thr.add(injection);
                    default -> throw new IllegalStateException("not a method-level point: " + injection.at);
                }
            }
        }
        site.head = head.toArray(Site.NONE);
        site.ret = ret.toArray(Site.NONE);
        site.thr = thr.toArray(Site.NONE);
        site.invokeHooks = invokeHooks;
    }

    private void fill(InvokePoint point) {
        String key = point.site.name + point.site.desc;
        List<InjectionImpl> before = new ArrayList<>();
        List<InjectionImpl> after = new ArrayList<>();
        for (InjectionImpl injection : injections) {
            if (callIndices(injection, key).contains(point.index)) {
                (Points.runsBefore(injection.at) ? before : after).add(injection);
            }
        }
        point.before = before.toArray(Site.NONE);
        point.after = after.toArray(Site.NONE);
    }
}
