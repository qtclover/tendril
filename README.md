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

## What a hook can do

- Run at the **head** of a method or before every **return**.
- **Cancel** the call, with or without a return value.
- Read and **change arguments**, replace the **return value**, reach `this`.
- Target classes that are **already loaded** or that load later, including JDK classes.
- Be **removed** at any time, which restores the exact original bytecode.

## Install

Not on Maven Central yet. Build it into your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

```kotlin
repositories { mavenLocal(); mavenCentral() }

dependencies {
    implementation("io.github.qtclover:tendril-kotlin:0.1.0")   // Kotlin DSL + core
    // Java only: implementation("io.github.qtclover:tendril-core:0.1.0")
}
```

Needs Java 17 or newer.

## Good to know

- Tendril loads its agent into the running JVM on first use. JDK 21+ prints a warning
  about that unless you pass `-XX:+EnableDynamicAgentLoading` or use
  `-javaagent:tendril-agent.jar` instead.
- Hooks go at method head and return only. No constructors, no hooking a single call
  inside a method, no field hooks yet.
- A hooked call boxes its arguments, so keep hooks out of hot inner loops.
- This is v0.1. For production, use Byte Buddy.

## Docs

- [Using Tendril](docs/api.md): the Kotlin DSL, annotations, fluent Java API, picking overloads.
- [Attaching](docs/attaching.md): self-attach, `-javaagent`, the JDK 21+ warning.
- [How it works](docs/how-it-works.md): the bytecode, the bootstrap bridge, undo, the verifier.
- [Limits](docs/limits.md): everything it cannot do.
- [Comparison](docs/comparison.md) with Byte Buddy, Mixin, Javassist and InjectAPI.

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
