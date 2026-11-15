# Limits

What Tendril cannot do, or does in a way you should know about.

**Scope of v0.1**

- Hooks go at the head of a method and before its returns. There is no redirecting of one
  particular call inside a method, no field access hooks, no hooks on exceptional exit.
- Constructors and static initialisers cannot be hooked. Nor can `abstract` and `native`
  methods: they have no body.
- Hooks can observe and steer a method. They cannot add methods, fields or interfaces to a
  class; the JVM does not allow that for loaded classes (see [How it works](how-it-works.md)).

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
  flag. See [Attaching](attaching.md).
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
