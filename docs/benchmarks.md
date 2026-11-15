# Benchmarks

What a call costs once it is hooked. The hooks do nothing, so this is Tendril's own
overhead. Each scenario has its own copy of the same method, `int add(int a, int b)`.

| Scenario                                    | Time per call | 
|---------------------------------------------|--------------:|
| No hook (baseline)                          |        0.5 ns |
| Direct hook at head                         |        2.9 ns |
| Throw hook, no exception thrown             |        4.8 ns |
| Head hook                                   |        7.0 ns |
| Head hook and return hook                   |       14.0 ns |
| Head hook that reads an argument            |       17.7 ns |
| Return hook that replaces the result        |       26.5 ns |
| Hook on a call inside the method            |       24.4 ns |
| Hook on a field read inside the method      |       22.8 ns |

Measured with JMH 1.37 (average time, 4 warm-up and 6 measurement iterations of one
second, one fork) on an AMD Ryzen 9 5900X, OpenJDK 25.0.5, Linux. The error reported by
JMH was below 7% for every row. Raw numbers are whatever your machine says; the ratios
are the point.

## Reading the numbers

- **The baseline is unrealistically cheap.** `a + b` is inlined into the benchmark loop and
  costs half a nanosecond. Any real method does more, and the hook's cost is added to it,
  not multiplied.
- **An ordinary hook costs roughly 7 to 25 ns.** That buys a static call into the
  bootstrap bridge, an interface call into the dispatcher, a table lookup, a
  thread-local read for the re-entrancy guard, an `Object[]` for the arguments and a
  `CallInfo`. Reading an argument or setting a result adds boxing and a type check.
- **A point inside a method costs more than one on the method**, because the method
  gets its head code as well and the call or field access gets its own argument array
  and context object.
- **A direct hook costs about 2.5 ns over the baseline**: it is a plain static call with
  the real arguments, which the JIT can inline. (The benchmark's direct hook increments a
  static counter so that it cannot be optimised away entirely.) Use these when a method
  is called millions of times a second; see [Using Tendril](api.md).
- **A throw hook that never fires** still pays for the head code, since that is what
  creates the context the hook would need.

For scale: 20 ns is 50 million hooked calls per second on one core. A hook on a request
handler, a tick method or a database call is free in practice. A hook on `String.charAt`
is not.

## Running them

```bash
./gradlew :benchmarks:jmh
```

Results are printed and written to `benchmarks/build/jmh.json`. JMH options go in
`-PjmhArgs`, for example `-PjmhArgs="-f 3 direct"`. The benchmarks are not part of
`./gradlew build`.
