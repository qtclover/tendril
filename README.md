# Tendril

Inject code into the methods of a running JVM, from Kotlin or Java, and take it out again.

```kotlin
Injector.attach()

val hooks = inject<PlayerService> {
    method("damage") {
        atHead { call -> if (call.arg<Int>(0) > 100) call.cancel() }
        atReturn { call -> println("health is now ${call.self<PlayerService>().health}") }
    }
}

hooks.remove()   // PlayerService has its original bytecode back
```

```java
@Inject(target = PlayerService.class, method = "damage", at = At.HEAD)
static void onDamage(CallInfo call) {
    int amount = call.arg(0);
    if (amount > 100) call.cancel();
}
```

No `-javaagent` flag is required, no mod loader, no build plugin. Tendril is written
directly on [ASM](https://asm.ow2.io/); it does not use Byte Buddy.

- **Head and return hooks** on any method that has a body, static or instance.
- **Control from the hook**: cancel the call, replace the return value, read and change
  arguments, reach `this`.
- **Works on classes that are already loaded** and on classes that load later.
- **Undo**: removing a hook restores the exact original bytecode.
- **Three front ends over one core**: a Kotlin DSL, Mixin-style annotations, and a Java
  fluent API.
- **JDK classes too**: `java.time.Year`, `java.util.StringJoiner` and friends can be hooked
  like your own (with [some exceptions](#limits)).

Status: v0.1, a first version. Read [Limits](#limits) before relying on it.

## Contents

- [Getting it](#getting-it)
- [Kotlin DSL](#kotlin-dsl)
- [Java](#java)
- [What a hook can do](#what-a-hook-can-do)
- [Attaching](#attaching)
- [How it works](#how-it-works)
- [Limits](#limits)
- [Compared with Byte Buddy, Mixin and Javassist](#compared-with-byte-buddy-mixin-and-javassist)
- [Building](#building)

## Getting it

Tendril is not on Maven Central yet. Build it and install it to your local Maven
repository:

```bash
./gradlew publishToMavenLocal
```

```kotlin
repositories { mavenLocal(); mavenCentral() }

dependencies {
    implementation("io.github.qtclover:tendril-kotlin:0.1.0")   // Kotlin DSL, brings in core
    // or, for Java only:
    implementation("io.github.qtclover:tendril-core:0.1.0")
}
```

| Module           | Contents                                                              |
|------------------|-----------------------------------------------------------------------|
| `tendril-core`   | The engine, the Java fluent API and `@Inject`. Depends on ASM only.   |
| `tendril-kotlin` | The `inject { }` DSL.                                                 |
| `tendril-agent`  | The agent jar for `-javaagent`. Already embedded in `tendril-core`; you only need the file itself if you want to use the flag. |

Requires Java 17 or newer at runtime.

## Kotlin DSL

```kotlin
import io.github.qtclover.tendril.dsl.inject

val hooks = inject<PlayerService> {
    method("greet") {
        atHead { call -> call.setArg(0, call.arg<String>(0).uppercase()) }
        atReturn { call -> call.result = "${call.result}!" }
    }

    // Pick one overload by parameter types...
    method("describe", Int::class) { atHead { it.cancel("hooked") } }
    // ...or by JVM descriptor (the parameter part alone is enough).
    method("describe", "()") { atHead { it.cancel("hooked") } }
}
```

A block is installed as one unit: if any part of it is wrong (say, a method that does not
exist) nothing is installed and an `InjectionException` tells you why. The returned
`Injection` removes everything in the block. It is `AutoCloseable`, so `use { }` works.

`inject("com.example.NotLoadedYet") { ... }` takes a class name instead. The class is not
loaded by that call; the hooks are woven in when the class first loads.

Kotlin-specific things to know:

- A `companion object`'s functions live in `Outer$Companion`. Hook
  `inject(Outer.Companion::class) { ... }`, not `Outer`.
- Top-level functions live in the file class, e.g. `inject("com.example.UtilsKt") { ... }`.
- `inline` functions are copied into their callers, so there is no method to hook.
- `suspend` functions have an extra `Continuation` parameter and return through a state
  machine; a return hook fires on every suspension, not once per logical call.

## Java

Annotations, for hooks that live in a class of their own:

```java
final class Hooks {
    @Inject(target = PlayerService.class, method = "damage", at = At.HEAD)
    static void onDamage(CallInfo call) {
        int amount = call.arg(0);
        if (amount > 100) call.cancel();
    }

    @Inject(targetName = "com.example.LoadedLater", method = "describe", descriptor = "(I)", at = At.RETURN)
    static void onDescribe(CallInfo call) {
        call.setResult(call.getResult() + " (hooked)");
    }
}

Injection hooks = Injector.register(Hooks.class);   // static methods
Injection more  = Injector.register(new MyHooks()); // instance methods too
hooks.remove();
```

A hook method takes one `CallInfo` or nothing, and may be private.

The fluent API, for one-off hooks:

```java
try (Injection hook = Injector.into(PlayerService.class)
        .method("heal", int.class)
        .atHead(call -> call.setArg(0, call.<Integer>arg(0) * 2))) {
    player.heal(3);   // heals 6
}
player.heal(3);       // heals 3
```

`Injector.batch()` installs several hooks as one unit, like a DSL block does.

### Selecting methods

| You write                                  | It matches                                  |
|--------------------------------------------|---------------------------------------------|
| `method("damage")`                         | every overload named `damage`               |
| `method("damage", int.class)` / `Int::class` | the overload taking exactly one `int`     |
| descriptor `"(I)"`                         | the same, by parameter descriptor           |
| descriptor `"(I)V"`                        | the same, return type included              |
| descriptor `"()"`                          | the overload with no parameters             |

Only methods declared in the target class itself match. To hook an inherited method, hook
the class that declares it; every subclass that does not override it is affected.

## What a hook can do

`CallInfo` is the same object in all three APIs.

| Member                  | At `HEAD`                                         | At `RETURN`                           |
|-------------------------|---------------------------------------------------|---------------------------------------|
| `self()`                | the receiver, `null` for static methods           | same                                  |
| `arg(i)`, `args()`      | the arguments, primitives boxed                   | the arguments as they were at entry   |
| `setArg(i, value)`      | the method body runs with the new value           | only visible to later hooks           |
| `cancel()`              | skips the body; returns `null` / `0` / `false`    | not allowed                           |
| `setResult(v)` / `cancel(v)` | skips the body and returns `v`               | replaces the value being returned     |
| `getResult()`           | what a previous hook set, else `null`             | the value about to be returned        |
| `owner()`, `methodName()`, `descriptor()` | which method this is            | same                                  |

- Hooks on the same method run in the order they were installed.
- A cancel stops the remaining head hooks, and return hooks do not run for a cancelled call.
- `RETURN` runs before every normal return. It does not run when the method throws.
- Values are checked against the real parameter and return types, so a mistake fails in
  your hook with a readable message instead of as a `ClassCastException` somewhere inside
  the target. Numbers are converted (`setArg(0, 5)` is fine for a `long` parameter).
- Whatever a hook throws comes out of the hooked method, as if the method had thrown it.
- While a hook runs, calls it makes to the method it is hooking go straight to the method
  without running that method's hooks again. So a hook can call its own target safely.

## Attaching

Rewriting classes needs a `java.lang.instrument.Instrumentation` handle, which only a Java
agent gets. There are two ways in.

### Self-attach (the default)

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

### The `-javaagent` flag

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

### One harmless line of output

Either way, the JVM prints this once if class data sharing is on (it is by default):

```
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
```

It is a consequence of putting the bridge on the bootstrap class path (see below). Pass
`-Xshare:off` to get rid of it.

## How it works

### 1. Re-transformation can only change method bodies

The JVM lets an agent replace the bytecode of a class that is already loaded, but only
within strict limits: no adding, removing or renaming methods or fields, no changing
signatures or the class hierarchy. Method bodies are all that may change.

So Tendril cannot copy your hook into the target class or give the class a list of
callbacks. Instead, a hooked method gets a few instructions that call one static method on
a fixed class, passing a number that identifies the method:

```java
int damage(int amount) {
    Object[] args = { amount };
    Object ctx = Bridge.enter(17, PlayerService.class, this, args);
    if (Bridge.cancelled(ctx)) return (int) Bridge.result(ctx);
    amount = (int) args[0];                 // a hook may have changed it

    // ... the original body, unchanged, except that every
    //     "return x"  has become  "return (int) Bridge.exit(ctx, x)"
}
```

`17` is a *site id*. Which hooks belong to site 17 is looked up in a table at call time.
Adding a second hook to an already hooked method therefore changes a table, not bytecode,
and the class is only re-transformed when a method gains its first hook or loses its last.

The rewriting is done with ASM's `AdviceAdapter`: head code is emitted before the first
instruction, exit code before each `xRETURN`. Arguments and return values are boxed for the
call and unboxed after it, with the right instructions for each of the eight primitive
types, for `void`, and for `long`/`double` taking two local variable slots.

### 2. The dispatcher has to be visible from the hooked class

`PlayerService` can only call `Bridge` if its class loader can find `Bridge`. For an
application class that is easy. For `java.util.StringJoiner` it is not: JDK classes are
defined by the bootstrap class loader, which sees nothing on your class path.

The agent jar therefore appends itself to the bootstrap class path
(`Instrumentation.appendToBootstrapClassLoaderSearch`). `Bridge` is then defined by the
bootstrap loader, and since every well-behaved class loader delegates to it, every class
in the JVM can call it. For classes in named modules such as `java.base`, the JVM adds
the necessary read edge to the bootstrap loader's unnamed module when an agent transforms
them.

`Bridge` is deliberately tiny and depends on nothing. It holds a single `Handler`
reference and forwards to it; the real dispatcher, the hook table and ASM live in
`tendril-core` in the ordinary application class loader. To make sure there is only ever
one `Bridge`, its class file is not on the class path at all: the agent jar is embedded in
`tendril-core` as a resource, and `tendril-core` compiles against it without depending on
it at run time.

### 3. Undo is "transform again, change nothing"

When a class is re-transformed, the JVM hands transformers the *original* class file, not
the output of the previous transformation. So to restore a class Tendril empties its plan
and asks for a re-transformation; its transformer returns "no change" and the JVM
reinstalls the original bytes. Nothing has to be remembered or reversed. The test suite
checks this by comparing the bytes the JVM holds before injection and after removal.

### 4. Getting past the verifier

Since Java 6 every method carries *stack map frames*: a description of the types on the
stack and in local variables at each branch target. Inserting a branch (the cancel check)
makes the existing frames wrong.

Tendril lets ASM recompute them (`COMPUTE_FRAMES`). To merge two branches ASM needs to
know the common superclass of two types, and by default it finds out with
`Class.forName`. Inside a class file transformer that is a trap: one of the two may be the
class being defined right now, and asking for it again ends in a `LinkageError` or a
deadlock. Tendril's `ClassWriter` reads the class hierarchy from `.class` resources
instead, which never defines a class.

Methods that are not hooked are copied byte for byte, frames included. Class files older
than Java 6 have no frames and get none; class files older than Java 5 also cannot hold a
class constant, so `CallInfo.owner()` is `null` for them.

### 5. Hooks that call what they hook

A hook on `StringJoiner.toString()` that prints something will, somewhere inside
`println`, quite possibly call `StringJoiner.toString()`. The dispatcher keeps a
per-thread list of the sites whose hooks are currently running and lets a nested call to
one of them through without hooks. The dispatcher's own code path avoids lambdas, string
concatenation and collections for the same reason: it may be running inside any method.

## Limits

What Tendril cannot do, or does in a way you should know about.

**Scope of v0.1**

- Hooks go at the head of a method and before its returns. There is no redirecting of one
  particular call inside a method, no field access hooks, no hooks on exceptional exit.
- Constructors and static initialisers cannot be hooked. Nor can `abstract` and `native`
  methods: they have no body.
- Hooks can observe and steer a method. They cannot add methods, fields or interfaces to a
  class; the JVM does not allow that for loaded classes (see above).

**Matching**

- Classes are matched by name. If two class loaders each define `com.example.Foo`, both
  are hooked.
- Only methods declared in the target class match, not inherited ones.
- A hook registered by name for a class that is not loaded cannot be checked. If the
  method name turns out to be wrong, Tendril prints a `[tendril] warning` when the class
  loads; it cannot throw at you, because your call returned long ago.
- There is a small window during class loading: if a class is in the middle of being
  defined on one thread at the moment another thread registers a hook for it by name, the
  hook can be missed for that class. Register hooks before the code that triggers loading.

**JDK classes**

- They work, but a hook on a popular JDK method runs for every caller in the JVM,
  including the JDK itself. Keep such hooks cheap and filter early.
- `Object`, `Class`, `Thread`, `ThreadLocal`, and everything in `java.lang.ref` and
  `java.lang.invoke` are refused: the dispatcher runs through them, so a hook there would
  call itself without end. Other low-level JDK internals are not refused but may misbehave
  for the same reason.
- JIT intrinsics (many `String` and `Math` methods) may bypass the bytecode entirely.

**Attaching**

- Self-attach needs the `jdk.attach` module, starts a child JVM, and since JDK 21 prints a
  warning unless `-XX:+EnableDynamicAgentLoading` is given. A future JDK will require the
  flag. See [Attaching](#attaching).
- A class loader that does not delegate to the bootstrap loader for unknown packages (a
  strict OSGi configuration, for example) cannot see the bridge, and classes it defines
  will fail with `NoClassDefFoundError` when hooked.
- One copy of Tendril per JVM. A second copy in another class loader is detected and
  refused.

**Cost and behaviour**

- A hooked call allocates an `Object[]` and a small context object and boxes primitive
  arguments. That is fine for most code and wrong for a hot inner loop.
- An exception thrown by a hook propagates out of the hooked method. A return hook's
  exception can be caught by the method's own enclosing `try`/`finally`.
- If the superclass of a type cannot be found as a `.class` resource (classes generated at
  run time by another tool), frame computation falls back to `Object`, which in rare cases
  makes the verifier reject the rewritten method. For a class that is already loaded,
  Tendril reports that as an `InjectionException` and leaves the class untouched; for a
  class hooked before it loads, the class itself fails to load with a `VerifyError`.
- ASM is a normal, unshaded dependency. If your application needs a different ASM version,
  the two can conflict.

**Tested on**

- OpenJDK 25 (HotSpot) on Linux, with both self-attach and `-javaagent`. The CI workflow
  also runs JDK 17 and 21. Other JVMs (OpenJ9, GraalVM native image) are untested; native
  image cannot work at all, as it has no bytecode at run time.

## Compared with Byte Buddy, Mixin and Javassist

|                         | Tendril                    | Byte Buddy                  | Mixin (SpongePowered)          | Javassist                    |
|-------------------------|----------------------------|-----------------------------|--------------------------------|------------------------------|
| Level                   | hooks on existing methods  | general code generation     | class patching for mods        | source-level bytecode editing |
| Injection points        | head, return               | anything, incl. custom ASM  | head, return, invoke, field, constant, ... | anywhere, as Java source strings |
| Already-loaded classes  | yes                        | yes (`Advice` + agent)      | no, load time only             | yes, via `Instrumentation` or HotSwap |
| Undo at run time        | yes, `Injection.remove()`  | yes, `ResettableClassFileTransformer` | no                  | manual                       |
| Needs                   | nothing, or `-javaagent`   | `byte-buddy-agent` or `-javaagent` | a launcher/mod loader that hosts it | you supply the `Instrumentation` |
| Kotlin DSL              | yes                        | no                          | no                             | no                           |
| Size of the API         | a handful of types         | large                       | large                          | medium                       |
| Maturity                | new                        | very mature, widely used    | mature in its ecosystem        | mature                       |

- **Byte Buddy** does everything Tendril does and far more, and is what you should use in
  production. Its `Advice` inlines your code into the target instead of calling out to a
  dispatcher, which is faster. The price is a larger API and more concepts to learn before
  the first hook works. Tendril is deliberately not built on it: the point of this project
  is the bytecode work underneath.
- **Mixin** is the model for Tendril's annotations and for `CallInfo`. It is much more
  expressive (`@Redirect`, `@ModifyArg`, `@Accessor`, shadow members) because it merges
  whole classes at load time, when adding members is still allowed. That is also its
  limit: it needs a host that controls class loading, and it cannot touch a class that is
  already loaded or take a change back.
- **Javassist** lets you write the injected code as a string of Java source
  (`insertBefore("{ System.out.println($1); }")`). That is convenient and compiler-free,
  but the string is not checked by your compiler or IDE, and attaching, re-transforming and
  restoring are left to you.

## Building

```bash
./gradlew build
```

That compiles everything to Java 17 bytecode and runs the tests three ways: with
self-attach, with `-javaagent`, and with a hook class named in the agent arguments.

```bash
./gradlew :example:run
```

```bash
./gradlew :example:runJava
```

```bash
./gradlew :example:runWithAgent
```

The three examples show the Kotlin DSL, the Java annotations, and an application that
never mentions Tendril being hooked from the command line.

## Licence

[MIT](LICENSE)
