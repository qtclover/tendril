# Using Tendril

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
- `cancel()` and `setResult()` act on the hooked method and work at `HEAD` and `RETURN`
  as in the table. At invoke points they throw; use `invoked()` instead (see below).
- Whatever a hook throws comes out of the hooked method, as if the method had thrown it.
- While a hook runs, calls it makes to the method it is hooking go straight to the method
  without running that method's hooks again. So a hook can call its own target safely.

## Hooking a call inside a method

`HEAD` and `RETURN` wrap a whole method. An invoke point wraps one call that the method
makes. Given:

```java
public int checkout(String item, int quantity) {
    int price = pricing.priceOf(item, quantity);
    audit("sold " + item);
    return price + shipping(price);
}
```

```kotlin
inject<Shop> {
    method("checkout") {
        atInvoke("Pricing.priceOf") { call ->
            val invoked = call.invoked()
            if (invoked.arg<Int>(1) > 100) invoked.setArg(1, 100)   // cap the quantity
        }
        afterInvoke("shipping") { it.invoked().result = 0 }          // free shipping
        atInvoke("audit") { it.invoked().skip() }                    // do not log
    }
}
```

```java
Injector.into(Shop.class).method("checkout")
        .atInvoke("Pricing.priceOf", call -> call.invoked().setArg(1, 1));

@Inject(target = Shop.class, method = "checkout", at = At.AFTER_INVOKE, invoke = "shipping")
static void freeShipping(CallInfo call) {
    call.invoked().setResult(0);
}
```

`call` still describes the surrounding method (`checkout`, its `this`, its arguments).
`call.invoked()` is the call inside it:

| Member                  | At `INVOKE` (before the call)                  | At `AFTER_INVOKE`                    |
|-------------------------|------------------------------------------------|--------------------------------------|
| `receiver()`            | the object called on, `null` for a static call | same                                 |
| `arg(i)`, `args()`      | the call's arguments                           | the arguments the call was made with |
| `setArg(i, value)`      | the call is made with the new value            | not allowed                          |
| `skip()`                | the call is not made; yields `null`/`0`/`false`| not allowed                          |
| `setResult(v)` / `skip(v)` | the call is not made; yields `v`            | replaces what the call returned      |
| `getResult()`           | what a previous hook set, else `null`          | what the call returned               |
| `owner()`, `name()`, `descriptor()` | which method is being called       | same                                 |

Which calls a hook applies to:

| You write                         | It matches                                             |
|-----------------------------------|--------------------------------------------------------|
| `"priceOf"`                       | every call to a method named `priceOf`                 |
| `"Pricing.priceOf"`               | only those on `Pricing` (simple or full class name)    |
| `"priceOf(Ljava/lang/String;I)"`  | only that overload                                     |
| `ordinal = 1`                     | of the matching calls, only the second                 |

If nothing in the method matches, installing fails and the error lists the calls the
method does make. A skip stops the remaining `INVOKE` hooks; `AFTER_INVOKE` hooks still
run, with the substituted value. See [Limits](limits.md) for what cannot be matched.
