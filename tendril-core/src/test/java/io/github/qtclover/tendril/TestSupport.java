package io.github.qtclover.tendril;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicReference;

final class TestSupport {

    private TestSupport() {
    }

    /**
     * The bytes the JVM currently runs for {@code type}, as Tendril left them.
     * A probe transformer registered after Tendril's sees Tendril's output.
     */
    static byte[] currentBytes(Class<?> type) throws Exception {
        Instrumentation instrumentation = Injector.instrumentation();
        AtomicReference<byte[]> seen = new AtomicReference<>();
        ClassFileTransformer probe = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> redefined,
                                    ProtectionDomain domain, byte[] bytes) {
                if (redefined == type) {
                    seen.set(bytes.clone());
                }
                return null;
            }
        };
        instrumentation.addTransformer(probe, true);
        try {
            instrumentation.retransformClasses(type);
        } finally {
            instrumentation.removeTransformer(probe);
        }
        return seen.get();
    }

    static boolean isLoaded(String className) {
        for (Class<?> type : Injector.instrumentation().getAllLoadedClasses()) {
            if (type.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }
}
