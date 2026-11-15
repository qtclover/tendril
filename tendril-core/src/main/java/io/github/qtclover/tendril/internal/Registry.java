package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.Injection;
import io.github.qtclover.tendril.InjectionException;
import org.objectweb.asm.ClassReader;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the hook state and is the single {@link ClassFileTransformer} Tendril registers.
 *
 * <p>Undo falls out of how re-transformation works: the JVM always hands a
 * transformer the <em>original</em> class file, never its own previous output. So
 * restoring a class is just re-transforming it while its plan is empty.
 */
public final class Registry implements ClassFileTransformer {

    private static final String OWN_PACKAGE = "io/github/qtclover/tendril/";

    private static volatile Registry instance;

    private final Instrumentation instrumentation;
    private final Map<String, ClassPlan> plans = new ConcurrentHashMap<>();
    private volatile Site[] sites = new Site[0];

    private Registry(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    public static Registry get() {
        Registry registry = instance;
        if (registry == null) {
            throw new IllegalStateException("Tendril is not attached");
        }
        return registry;
    }

    static synchronized Registry start(Instrumentation instrumentation) {
        if (instance == null) {
            Registry registry = new Registry(instrumentation);
            Dispatcher.install(registry);
            instrumentation.addTransformer(registry, true);
            instance = registry;
        }
        return instance;
    }

    // ---- call-time lookup -------------------------------------------------

    Site site(int id) {
        Site[] table = sites;
        return id >= 0 && id < table.length ? table[id] : null;
    }

    synchronized Site newSite(String owner, String name, String desc) {
        Site[] table = Arrays.copyOf(sites, sites.length + 1);
        Site site = new Site(table.length - 1, owner, name, desc);
        table[site.id] = site;
        sites = table;
        return site;
    }

    // ---- install / remove -------------------------------------------------

    public Injection install(List<InjectionImpl> injections) {
        Map<String, List<InjectionImpl>> byClass = new LinkedHashMap<>();
        for (InjectionImpl injection : injections) {
            checkAllowed(injection.classInternalName);
            byClass.computeIfAbsent(injection.classInternalName, k -> new ArrayList<>()).add(injection);
        }
        Map<String, List<Class<?>>> loaded = loadedClasses(byClass.keySet());
        for (Map.Entry<String, List<Class<?>>> entry : loaded.entrySet()) {
            for (Class<?> type : entry.getValue()) {
                if (!instrumentation.isModifiableClass(type)) {
                    throw new InjectionException("The JVM does not allow " + type.getName() + " to be modified");
                }
            }
        }

        List<String> done = new ArrayList<>();
        try {
            for (Map.Entry<String, List<InjectionImpl>> entry : byClass.entrySet()) {
                String name = entry.getKey();
                ClassPlan plan = plans.computeIfAbsent(name, ClassPlan::new);
                plan.add(entry.getValue());
                done.add(name);
                List<Class<?>> classes = loaded.get(name);
                if (classes == null) {
                    // Not loaded yet. The transformer weaves it when it arrives.
                    continue;
                }
                if (plan.outOfDate()) {
                    Throwable failure = retransform(plan, classes);
                    if (failure != null) {
                        throw new InjectionException("Could not weave hooks into " + name.replace('/', '.')
                                + ": " + failure, failure);
                    }
                }
                for (InjectionImpl injection : entry.getValue()) {
                    if (Boolean.FALSE.equals(plan.matchesAnything(injection))) {
                        throw new InjectionException("No hookable method matches " + injection.describe()
                                + ". Only methods declared in the class itself, with a body, can be hooked. "
                                + "Candidates: " + new TreeSet<>(plan.knownMethods()));
                    }
                }
            }
        } catch (RuntimeException | Error e) {
            // All or nothing: take back whatever part of the batch went in.
            for (String name : done) {
                ClassPlan plan = plans.get(name);
                plan.remove(byClass.get(name));
                List<Class<?>> classes = loaded.get(name);
                if (classes != null && plan.outOfDate()) {
                    retransform(plan, classes);
                }
            }
            for (InjectionImpl injection : injections) {
                injection.active = false;
            }
            throw e;
        }
        return new Handle(injections);
    }

    void remove(List<InjectionImpl> injections) {
        Map<String, List<InjectionImpl>> byClass = new LinkedHashMap<>();
        for (InjectionImpl injection : injections) {
            injection.active = false;
            byClass.computeIfAbsent(injection.classInternalName, k -> new ArrayList<>()).add(injection);
        }
        // From here on the hooks no longer fire, whatever happens below.
        for (Map.Entry<String, List<InjectionImpl>> entry : byClass.entrySet()) {
            ClassPlan plan = plans.get(entry.getKey());
            if (plan != null) {
                plan.remove(entry.getValue());
            }
        }
        restore(byClass.keySet());
    }

    public void removeAll() {
        for (ClassPlan plan : plans.values()) {
            for (InjectionImpl injection : plan.clear()) {
                injection.active = false;
            }
        }
        restore(plans.keySet());
    }

    /** Re-transforms the named classes that still carry woven code they no longer need. */
    private void restore(Set<String> names) {
        Map<String, List<Class<?>>> loaded = loadedClasses(names);
        Throwable firstFailure = null;
        String failedClass = null;
        for (Map.Entry<String, List<Class<?>>> entry : loaded.entrySet()) {
            ClassPlan plan = plans.get(entry.getKey());
            if (plan != null && plan.outOfDate()) {
                Throwable failure = retransform(plan, entry.getValue());
                if (failure != null && firstFailure == null) {
                    firstFailure = failure;
                    failedClass = entry.getKey();
                }
            }
        }
        if (firstFailure != null) {
            throw new InjectionException("Hooks were removed, but restoring the bytecode of "
                    + failedClass.replace('/', '.') + " failed: " + firstFailure, firstFailure);
        }
    }

    private Throwable retransform(ClassPlan plan, List<Class<?>> classes) {
        plan.lastError = null;
        try {
            instrumentation.retransformClasses(classes.toArray(new Class<?>[0]));
        } catch (Throwable t) {
            plan.weaveFailed();
            return t;
        }
        // The JVM swallows whatever a transformer throws and silently keeps the old
        // bytes, so the transformer reports failures through the plan instead.
        return plan.lastError;
    }

    private Map<String, List<Class<?>>> loadedClasses(Set<String> internalNames) {
        Map<String, List<Class<?>>> result = new LinkedHashMap<>();
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            String name = type.getName();
            if (name.charAt(0) == '[') {
                continue;
            }
            String internal = name.replace('.', '/');
            if (internalNames.contains(internal)) {
                result.computeIfAbsent(internal, k -> new ArrayList<>()).add(type);
            }
        }
        return result;
    }

    private static void checkAllowed(String internalName) {
        if (isOwnClass(internalName)) {
            throw new InjectionException("Tendril cannot hook its own classes: " + internalName.replace('/', '.'));
        }
        if (isDispatchCritical(internalName)) {
            throw new InjectionException(internalName.replace('/', '.') + " cannot be hooked: "
                    + "Tendril's own dispatch code runs through it, so a hook there would call itself forever");
        }
    }

    private static boolean isOwnClass(String name) {
        if (!name.startsWith(OWN_PACKAGE)) {
            return false;
        }
        String rest = name.substring(OWN_PACKAGE.length());
        return rest.indexOf('/') < 0 || rest.startsWith("agent/") || rest.startsWith("internal/")
                || rest.startsWith("dsl/");
    }

    private static boolean isDispatchCritical(String name) {
        return name.equals("java/lang/Object")
                || name.equals("java/lang/Class")
                || name.equals("java/lang/Thread")
                || name.startsWith("java/lang/ThreadLocal")
                || name.startsWith("java/lang/ref/")
                || name.startsWith("java/lang/invoke/");
    }

    // ---- ClassFileTransformer ----------------------------------------------

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        ClassPlan plan = plans.get(className);
        if (plan == null) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            Set<String> methods = Weaver.hookableMethods(reader);
            while (true) {
                int version = plan.version();
                Map<String, Site> sitesToWeave = plan.planWeave(methods, this);
                // Returning null means "leave the class as it is", which for a
                // re-transformation means the original bytes: that is the undo.
                byte[] result = sitesToWeave.isEmpty() ? null : Weaver.weave(reader, loader, sitesToWeave);
                if (plan.version() == version) {
                    if (classBeingRedefined == null) {
                        // First load: install() could not check the method names
                        // back then, so a typo would otherwise go unnoticed.
                        for (InjectionImpl injection : plan.unmatched()) {
                            System.err.println("[tendril] warning: no hookable method matches "
                                    + injection.describe() + "; that hook will never run");
                        }
                    }
                    return result;
                }
                // A hook was added or removed while we were weaving; go again.
            }
        } catch (Throwable t) {
            plan.weaveFailed();
            plan.lastError = t;
            if (classBeingRedefined == null) {
                // First load: nobody is waiting for the outcome, so say it out loud.
                System.err.println("[tendril] could not weave hooks into " + className.replace('/', '.')
                        + "; the class is left unchanged: " + t);
            }
            return null;
        }
    }

    private final class Handle implements Injection {

        private final List<InjectionImpl> injections;
        private boolean removed;

        Handle(List<InjectionImpl> injections) {
            this.injections = injections;
        }

        @Override
        public synchronized void remove() {
            if (!removed) {
                removed = true;
                Registry.this.remove(injections);
            }
        }

        @Override
        public synchronized boolean isActive() {
            if (removed) {
                return false;
            }
            for (InjectionImpl injection : injections) {
                if (injection.active) {
                    return true;
                }
            }
            return false;
        }
    }
}
