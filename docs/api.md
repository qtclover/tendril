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
| `"new Widget"`                    | constructor calls: `new Widget(...)`                   |
| `ordinal = 1`                     | of the matching calls, only the second                 |

If nothing in the method matches, installing fails and the error lists the calls the
method does make. A skip stops the remaining `INVOKE` hooks; `AFTER_INVOKE` hooks still
run, with the substituted value. See [Limits](limits.md) for what cannot be matched.

A `new Widget(a, b)` expression is a call named `<init>` with no receiver whose result
is the new object. `setArg` changes the constructor arguments, an after-hook can replace
the object, and `skip(other)` uses `other` without constructing anything.

## Exceptions

```kotlin
inject<PlayerService> {
    method("parse") {
        atThrow { call ->
            println("parse(${call.arg<String>(0)}) failed: ${call.thrown}")
            call.result = -1          // swallow it and return -1 instead
        }
    }
}
```

A hook at `THROW` runs when an exception is about to leave the method.

| Member            | Effect                                                         |
|-------------------|----------------------------------------------------------------|
| `getThrown()`     | the exception                                                  |
| `setThrown(t)`    | throw `t` instead                                              |
| `swallow()`       | return normally with `null` / `0` / `false`                    |
| `setResult(v)`    | return `v` normally                                            |

It does not run for exceptions the method catches itself, nor for exceptions thrown by
hooks. Return hooks do not run for a method that ended by swallowing.

## Constructors

```kotlin
inject<Widget> {
    constructor(Int::class) {
        atHead { call -> call.setArg(0, 9) }                       // after super(...) has returned
        atReturn { call -> println("built ${call.self<Widget>()}") }
    }
}
```

Java: `Injector.into(Widget.class).constructor(int.class).atHead(...)`, or
`@Inject(target = Widget.class, method = "<init>", ...)`. `constructor()` without
parameter types means every constructor. A head hook runs once `super(...)` or
`this(...)` has returned, so `self()` is a real object, and argument changes affect the
rest of the constructor only. A constructor cannot be cancelled and its exception cannot
be swallowed: either would hand out a half-built object.

## Fields

```kotlin
inject<Account> {
    method("balance") {
        onFieldRead("balance") { it.invoked().result = 0L }        // what the method sees
    }
    method("deposit") {
        onFieldWrite("Account.balance") { call ->
            if (call.invoked().arg<Long>(0) < 0) call.invoked().skip()   // refuse the write
        }
    }
}
```

Field accesses reuse `invoked()`. A read is a call with no arguments whose result is the
value read; the hook runs after the read and `setResult` substitutes a value. A write is
a call with the new value as argument 0; the hook runs before it, `setArg(0, v)` changes
the value and `skip()` drops the write. Java: `.onFieldRead("balance", hook)`, or
`@Inject(..., at = At.FIELD_READ, invoke = "balance")`.

## Local variables

A hook at a point inside a method can ask for the method's local variables:

```kotlin
method("settle") {
    afterInvoke("fee", locals = true) { call ->
        if (call.local<Int>("i") == 3) call.setLocal("note", "third")
    }
}
```

Java: `.method("settle").withLocals().afterInvoke("fee", hook)`, or `locals = true` on
`@Inject`. `localNames()` lists what is in scope. This needs the class to be compiled
with debug information (`javac -g`, the default in Gradle and Maven; Kotlin always
includes it), and it is opt-in because that information is not verified by the JVM.
A value the current statement has already read is not affected: in `total += fee(i)`,
`total` is read before `fee` is called.

## Many classes at once

| Target                         | Hooks                                                  |
|--------------------------------|--------------------------------------------------------|
| `"com.example.Shape+"`         | `Shape` and every class that extends or implements it  |
| `"com.example.*"`              | every class whose name matches; `*` is any characters  |

```kotlin
injectSubtypes<Shape> { method("area") { atReturn { println(it.result) } } }
inject("com.example.service.*") { method("handle") { atHead { println(it.methodName()) } } }
```

Java: `Injector.intoSubtypes(Shape.class)`, `Injector.intoMatching("com.example.*")`, or
`@Inject(target = Shape.class, subtypes = true, ...)`. Both apply to classes already
loaded and to classes as they load. A matching class that does not declare the method
itself is left alone, and JDK classes never match a pattern.

## Direct hooks

An ordinary hook receives a `CallInfo`, which means boxing and a trip through the
dispatcher. A direct hook is a public static method whose parameters are the receiver
(unless the target is static), the target's parameters and, at `RETURN`, its result:

```java
public final class Metrics {
    @Inject(target = PlayerService.class, method = "damage", direct = true)
    public static void onDamage(PlayerService self, int amount) { ... }

    @Inject(target = PlayerService.class, method = "add", at = At.RETURN, direct = true)
    public static void afterAdd(PlayerService self, int a, int b, int result) { ... }
}
```

It is called straight from the target method. A method that has only direct hooks never
touches the dispatcher, which makes them more than ten times cheaper than an ordinary
hook (see [benchmarks](benchmarks.md)). In exchange it can only observe, exists
for `HEAD` and `RETURN` only, needs a loaded target class given as `target = X.class`,
and the hook class must be public and visible from the target's class loader, which
rules out JDK classes. The overload hooked follows from the parameter types. The fluent
form is `.method("damage").direct(At.HEAD, Metrics.class, "onDamage")`.

