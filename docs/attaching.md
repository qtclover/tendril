# Attaching

Rewriting classes needs a `java.lang.instrument.Instrumentation` handle, which only a Java
agent gets. There are two ways in.

## Self-attach (the default)

`Injector.attach()`, or simply the first hook you install, loads the agent into the
running JVM. A JVM refuses to attach to itself, so Tendril starts a short-lived child JVM
that attaches to its parent and loads the agent jar unpacked from `tendril-core`. This
takes a few hundred milliseconds, once.

It needs a runtime that has the `jdk.attach` module: a normal JDK does, a trimmed `jlink`
image may not.

**JDK 21 and later print a warning when this happens**
([JEP 451](https://openjdk.org/jeps/451)):

```
WARNING: A Java agent has been loaded dynamically (/tmp/tendril-agent-123.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
```

Start the JVM with `-XX:+EnableDynamicAgentLoading` to say you mean it and silence the
warning. When a future JDK makes the flag mandatory, that flag or `-javaagent` below will
be required. If the JVM runs with `-XX:-EnableDynamicAgentLoading`, Tendril notices before
trying and throws an `InjectionException` that says so.

## The `-javaagent` flag

```bash
java -javaagent:tendril-agent-0.1.0.jar -cp app.jar:tendril-core-0.1.0.jar:asm-9.10.1.jar:asm-commons-9.10.1.jar com.example.Main
```

No child process, no warning, and `Injector.attach()` finds the agent already there.
`Injector.attachMode()` tells you which way was used (`PREMAIN` or `DYNAMIC`).

The agent also accepts a list of hook classes, which are registered before `main` runs.
That hooks an application without changing a line of it:

```bash
java -javaagent:tendril-agent-0.1.0.jar=com.example.Hooks,com.example.MoreHooks ...
```

The hook classes and `tendril-core` must be on the class path. Use `targetName` rather
than `target` in such hooks so that nothing is loaded earlier than it would have been.

## One harmless line of output

Either way, the JVM prints this once if class data sharing is on (it is by default):

```
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
```

It is a consequence of putting the bridge on the bootstrap class path (see [How it works](how-it-works.md)). Pass
`-Xshare:off` to get rid of it.
