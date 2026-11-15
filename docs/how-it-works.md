# How it works

## 1. Re-transformation can only change method bodies

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

## 2. The dispatcher has to be visible from the hooked class

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

## 3. Undo is "transform again, change nothing"

When a class is re-transformed, the JVM hands transformers the *original* class file, not
the output of the previous transformation. So to restore a class Tendril empties its plan
and asks for a re-transformation; its transformer returns "no change" and the JVM
reinstalls the original bytes. Nothing has to be remembered or reversed. The test suite
checks this by comparing the bytes the JVM holds before injection and after removal.

## 4. Getting past the verifier

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

## 5. Hooks that call what they hook

A hook on `StringJoiner.toString()` that prints something will, somewhere inside
`println`, quite possibly call `StringJoiner.toString()`. The dispatcher keeps a
per-thread list of the sites whose hooks are currently running and lets a nested call to
one of them through without hooks. The dispatcher's own code path avoids lambdas, string
concatenation and collections for the same reason: it may be running inside any method.
