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
java -javaagent:tendril-agent-0.2.0.jar -cp app.jar:tendril-core-0.2.0.jar:asm-9.10.1.jar:asm-commons-9.10.1.jar:asm-tree-9.10.1.jar com.example.Main
```

No child process, no warning, and `Injector.attach()` finds the agent already there.
`Injector.attachMode()` tells you which way was used (`PREMAIN` or `DYNAMIC`).

The agent also accepts a list of hook classes, which are registered before `main` runs.
That hooks an application without changing a line of it:

```bash
java -javaagent:tendril-agent-0.2.0.jar=com.example.Hooks,com.example.MoreHooks ...
```

The arguments can also be `key=value` pairs separated by semicolons:
`hooks=com.example.Hooks;classpath=/path/hooks.jar`. `classpath` adds jars to the system
class path first, for hook classes that are not part of the application.

The hook classes and `tendril-core` must be on the class path, unless you use the
standalone jar below. Use `targetName` rather
than `target` in such hooks so that nothing is loaded earlier than it would have been.

## One harmless line of output

Either way, the JVM prints this once if class data sharing is on (it is by default):

```
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
```

It is a consequence of putting the bridge on the bootstrap class path (see [How it works](how-it-works.md)). Pass
`-Xshare:off` to get rid of it.

## The standalone jar

`tendril-agent-all` contains the agent, the core and a private, relocated copy of ASM.
With it nothing else is needed on the class path, and the application's own ASM version
does not matter:

```bash
java -javaagent:tendril-agent-all-0.2.0.jar="hooks=com.example.Hooks;classpath=hooks.jar" -jar app.jar
```

Hook classes are compiled against `tendril-core` as usual (`compileOnly` is enough).
Only the bridge goes on the bootstrap class path; the rest stays in the application
class loader.

## Attaching to another JVM

The same jar is a command line tool:

```bash
java -jar tendril-agent-all-0.2.0.jar list
```

```bash
java -jar tendril-agent-all-0.2.0.jar attach 12345 --hooks com.example.Hooks --classpath hooks.jar
```

```bash
java -jar tendril-agent-all-0.2.0.jar detach 12345
```

`attach` loads the agent into the JVM with that process id and registers the hook
classes there; `detach` removes every Tendril hook from it and restores the bytecode.
The target must run as the same user on a JDK that allows dynamic agent loading, and it
prints the JDK 21+ warning described above unless started with
`-XX:+EnableDynamicAgentLoading`. Hooks registered this way should name their targets
with `targetName`, since the hook jar is loaded by the system class loader and may not
be able to see classes that the application loads through its own loaders.

