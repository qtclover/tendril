package io.github.qtclover.tendril.internal;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ClassWriter} that answers "what is the common superclass of A and B?"
 * without loading either.
 *
 * <p>ASM needs that answer to compute stack map frames, and its default
 * implementation calls {@code Class.forName}. Inside a class file transformer that
 * is a trap: one of the two may be the very class being defined, and asking for it
 * again fails with a {@code LinkageError} or deadlocks. So the hierarchy is read
 * from {@code .class} resources instead, which never defines anything.
 */
final class HierarchyClassWriter extends ClassWriter {

    private static final String OBJECT = "java/lang/Object";

    private record Info(String superName, String[] interfaces, boolean isInterface) {
    }

    private final ClassLoader loader;
    private final Map<String, Info> cache = new HashMap<>();

    HierarchyClassWriter(ClassReader self, int flags, ClassLoader loader) {
        super(self, flags);
        this.loader = loader;
        // The class being transformed may have no resource at all (generated classes).
        cache.put(self.getClassName(), infoOf(self));
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        if (type1.equals(type2)) {
            return type1;
        }
        if (OBJECT.equals(type1) || OBJECT.equals(type2)) {
            return OBJECT;
        }
        Info info1 = info(type1);
        Info info2 = info(type2);
        if (info1 == null || info2 == null) {
            // Unknown hierarchy: Object is always a correct, if imprecise, answer.
            return OBJECT;
        }
        if (isAssignableFrom(type1, info1, type2)) {
            return type1;
        }
        if (isAssignableFrom(type2, info2, type1)) {
            return type2;
        }
        if (info1.isInterface() || info2.isInterface()) {
            return OBJECT;
        }
        String current = type1;
        while (true) {
            Info info = info(current);
            if (info == null || info.superName() == null) {
                return OBJECT;
            }
            current = info.superName();
            Info superInfo = info(current);
            if (superInfo == null) {
                return OBJECT;
            }
            if (isAssignableFrom(current, superInfo, type2)) {
                return current;
            }
        }
    }

    /** True if a value of type {@code from} can be assigned to a variable of type {@code to}. */
    private boolean isAssignableFrom(String to, Info toInfo, String from) {
        if (to.equals(from)) {
            return true;
        }
        return toInfo.isInterface()
                ? implementsInterface(from, to, new HashSet<>())
                : extendsClass(from, to);
    }

    private boolean extendsClass(String type, String ancestor) {
        String current = type;
        while (current != null) {
            if (current.equals(ancestor)) {
                return true;
            }
            Info info = info(current);
            current = info == null ? null : info.superName();
        }
        return false;
    }

    private boolean implementsInterface(String type, String iface, Set<String> seen) {
        if (type == null || !seen.add(type)) {
            return false;
        }
        if (type.equals(iface)) {
            return true;
        }
        Info info = info(type);
        if (info == null) {
            return false;
        }
        for (String each : info.interfaces()) {
            if (implementsInterface(each, iface, seen)) {
                return true;
            }
        }
        return implementsInterface(info.superName(), iface, seen);
    }

    private Info info(String internalName) {
        if (cache.containsKey(internalName)) {
            return cache.get(internalName);
        }
        Info info = null;
        if (internalName.startsWith("[")) {
            info = new Info(OBJECT, new String[] {"java/lang/Cloneable", "java/io/Serializable"}, false);
        } else {
            String resource = internalName + ".class";
            try (InputStream in = loader == null
                    ? ClassLoader.getSystemResourceAsStream(resource)
                    : loader.getResourceAsStream(resource)) {
                if (in != null) {
                    info = infoOf(new ClassReader(in));
                }
            } catch (IOException | RuntimeException ignored) {
                // Treated as unknown.
            }
        }
        cache.put(internalName, info);
        return info;
    }

    private static Info infoOf(ClassReader reader) {
        return new Info(reader.getSuperName(), reader.getInterfaces(),
                (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0);
    }
}
