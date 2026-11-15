package io.github.qtclover.tendril.internal;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which classes a pattern hook applies to: {@code com.example.*} matches by
 * name, {@code com.example.Shape+} matches Shape and everything that extends or
 * implements it.
 *
 * <p>{@link #matches(String, ClassLoader, byte[])} runs inside the class file
 * transformer for every class the JVM loads, so it stays cheap and never loads a class.
 */
public final class TypeMatcher {

    private final String text;
    /** Internal name of the supertype, or null for a name pattern. */
    private final String supertype;
    /** Dotted name pattern with '*' wildcards, or null for a supertype match. */
    private final String pattern;
    /** Internal name to "is a subtype" for classes already examined. */
    private final Map<String, Boolean> known = new ConcurrentHashMap<>();

    private TypeMatcher(String text, String supertype, String pattern) {
        this.text = text;
        this.supertype = supertype;
        this.pattern = pattern;
    }

    /** Returns a matcher if {@code className} is a pattern, or null if it names one class. */
    public static TypeMatcher parse(String className) {
        if (className.endsWith("+")) {
            String name = className.substring(0, className.length() - 1).trim();
            return new TypeMatcher(className, name.replace('.', '/'), null);
        }
        if (className.indexOf('*') >= 0) {
            return new TypeMatcher(className, null, className.replace('/', '.'));
        }
        return null;
    }

    /** For a class that is being loaded right now: only its bytes exist, not its Class. */
    boolean matches(String internalName, ClassLoader loader, byte[] classFile) {
        if (pattern != null) {
            return wildcard(pattern, 0, internalName.replace('/', '.'), 0);
        }
        return isSubtype(internalName, loader, classFile);
    }

    /** For a class that is already loaded. */
    boolean matches(Class<?> type) {
        if (pattern != null) {
            return wildcard(pattern, 0, type.getName(), 0);
        }
        return isSubtype(type);
    }

    private boolean isSubtype(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (type.getName().replace('.', '/').equals(supertype)) {
            return true;
        }
        for (Class<?> each : type.getInterfaces()) {
            if (isSubtype(each)) {
                return true;
            }
        }
        return isSubtype(type.getSuperclass());
    }

    private boolean isSubtype(String internalName, ClassLoader loader, byte[] classFile) {
        if (internalName == null || internalName.equals("java/lang/Object")) {
            return false;
        }
        if (internalName.equals(supertype)) {
            return true;
        }
        Boolean cached = known.get(internalName);
        if (cached != null) {
            return cached;
        }
        boolean result = false;
        ClassReader reader = read(internalName, loader, classFile);
        if (reader != null) {
            for (String each : reader.getInterfaces()) {
                if (isSubtype(each, loader, null)) {
                    result = true;
                    break;
                }
            }
            result = result || isSubtype(reader.getSuperName(), loader, null);
        }
        known.put(internalName, result);
        return result;
    }

    private static ClassReader read(String internalName, ClassLoader loader, byte[] classFile) {
        try {
            if (classFile != null) {
                return new ClassReader(classFile);
            }
            String resource = internalName + ".class";
            try (InputStream in = loader == null
                    ? ClassLoader.getSystemResourceAsStream(resource)
                    : loader.getResourceAsStream(resource)) {
                return in == null ? null : new ClassReader(in);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** '*' matches any run of characters, dots included. */
    private static boolean wildcard(String pattern, int p, String name, int n) {
        while (p < pattern.length()) {
            char c = pattern.charAt(p);
            if (c == '*') {
                for (int skip = n; skip <= name.length(); skip++) {
                    if (wildcard(pattern, p + 1, name, skip)) {
                        return true;
                    }
                }
                return false;
            }
            if (n >= name.length() || name.charAt(n) != c) {
                return false;
            }
            p++;
            n++;
        }
        return n == name.length();
    }

    @Override
    public String toString() {
        return text;
    }
}
