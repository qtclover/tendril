# Tendril

Hook the methods of a running JVM from Kotlin or Java, and unhook them again.
No `-javaagent` flag, no mod loader, no build plugin.

```kotlin
val hooks = inject<PlayerService> {
    method("damage") {
        atHead { call -> if (call.arg<Int>(0) > 100) call.cancel() }
        atReturn { call -> println("health is now ${call.self<PlayerService>().health}") }
    }
}

hooks.remove()   // PlayerService has its original bytecode back
```

The same in Java, Mixin style:

```java
final class Hooks {
    @Inject(target = PlayerService.class, method = "damage", at = At.HEAD)
    static void onDamage(CallInfo call) {
        int amount = call.arg(0);
        if (amount > 100) call.cancel();
    }
}

Injection hooks = Injector.register(Hooks.class);
hooks.remove();
```

Hooking one call inside a method instead of the whole method:

```kotlin
inject<Shop> {
    method("checkout") {
        // int price = pricing.priceOf(item, quantity);
        atInvoke("Pricing.priceOf") { it.invoked().setArg(1, 1) }        // change an argument
        afterInvoke("Pricing.priceOf") { it.invoked().result = 0 }       // or replace the result
    }
}
```

## What a hook can do

- Run at the **head** of a method, before every **return**, or when it **throws**.
- **Cancel** the call, replace the **return value**, swallow or replace the exception.
- Read and **change arguments**, reach `this`.
- Wrap one **call inside** the method: change its arguments, skip it, replace its result.
  The same for `new` expressions and for **field reads and writes**.
- Read and change the method's **local variables** at such a point.
- Target **constructors**, classes that are **already loaded** or load later, JDK classes,
  every **subtype** of a class (`"com.example.Shape+"`) or every class matching a
  **pattern** (`"com.example.*"`).
- Be **removed** at any time, which restores the exact original bytecode.

For methods called millions of times a second there are **direct hooks**: a plain static
call with the real arguments, no boxing. See the [benchmarks](docs/benchmarks.md).

## Install

Not on Maven Central yet. Build it into your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

```kotlin
repositories { mavenLocal(); mavenCentral() }

dependencies {
    implementation("io.github.qtclover:tendril-kotlin:0.2.0")   // Kotlin DSL + core
    // Java only: implementation("io.github.qtclover:tendril-core:0.2.0")
}
```

Needs Java 17 or newer.

## Hooking an application you cannot change

`tendril-agent-all` is one self-contained jar. It works as a `-javaagent`:

```bash
java -javaagent:tendril-agent-all-0.2.0.jar="hooks=com.example.Hooks;classpath=hooks.jar" -jar app.jar
```

and as a tool that hooks a JVM which is already running, and unhooks it again:

```bash
java -jar tendril-agent-all-0.2.0.jar attach 12345 --hooks com.example.Hooks --classpath hooks.jar
```

```bash
java -jar tendril-agent-all-0.2.0.jar detach 12345
```

## Good to know

- Tendril loads its agent into the running JVM on first use. JDK 21+ prints a warning
  about that unless you pass `-XX:+EnableDynamicAgentLoading` or use `-javaagent`.
- An ordinary hook costs about 7 to 26 ns per call, a direct hook well under 1 ns.
- No static initialisers, no `invokedynamic` call sites, and a method can only be
  cancelled at its head. The [limits](docs/limits.md) page has the full list.
- This is v0.2 and young. For production, use Byte Buddy.

## Docs

- [Using Tendril](docs/api.md): every kind of hook, in Kotlin and Java.
- [Attaching](docs/attaching.md): self-attach, `-javaagent`, the standalone jar, attach by pid.
- [How it works](docs/how-it-works.md): the bytecode, the bootstrap bridge, undo, the verifier.
- [Limits](docs/limits.md): everything it cannot do.
- [Benchmarks](docs/benchmarks.md): what a hooked call costs.
- [Comparison](docs/comparison.md) with Byte Buddy, Mixin, Javassist and InjectAPI.
- [Releasing](docs/releasing.md): how a version gets to Maven Central.

## Try it

```bash
./gradlew :example:run
```

```bash
./gradlew build
```

The first runs the Kotlin demo in [`example/`](example); the second builds everything and
runs the tests.

## Licence

[MIT](LICENSE)
