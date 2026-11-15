# Compared with other libraries

|                         | Tendril                    | Byte Buddy                  | Mixin (SpongePowered)          | Javassist                    |
|-------------------------|----------------------------|-----------------------------|--------------------------------|------------------------------|
| Level                   | hooks on existing methods  | general code generation     | class patching for mods        | source-level bytecode editing |
| Injection points        | head, return, invoke       | anything, incl. custom ASM  | head, return, invoke, field, constant, ... | anywhere, as Java source strings |
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
- **[InjectAPI](https://github.com/Sunderw3k/InjectAPI)** is the closest in spirit: a small
  Kotlin library on ASM for hooking at run time. It works one level lower. You write the
  agent yourself, name methods by descriptor, and capture arguments by opcode and local
  variable slot. Both offer head, return and invoke points. Tendril attaches itself,
  addresses arguments by index, and can undo a hook.
