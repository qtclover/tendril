package io.github.qtclover.tendril;

/** How Tendril obtained its {@link java.lang.instrument.Instrumentation} handle. */
public enum AttachMode {
    /** The JVM was started with {@code -javaagent:tendril-agent.jar}. */
    PREMAIN,
    /** The agent was loaded into the running JVM by {@link Injector#attach()}. */
    DYNAMIC
}
