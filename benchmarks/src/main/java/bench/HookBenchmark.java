package bench;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.Injector;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * What one call costs with each kind of hook. Every scenario has its own copy of the
 * same tiny method, so the hooks of one cannot influence another. The hooks do
 * nothing: this measures Tendril's overhead, not anyone's hook.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 6, time = 1)
@Fork(1)
public class HookBenchmark {

    public static class Target {
        int field = 7;

        public int baseline(int a, int b) {
            return a + b;
        }

        public int head(int a, int b) {
            return a + b;
        }

        public int headReadingAnArgument(int a, int b) {
            return a + b;
        }

        public int headAndReturn(int a, int b) {
            return a + b;
        }

        public int returnReplacingTheResult(int a, int b) {
            return a + b;
        }

        public int throwHookNotTriggered(int a, int b) {
            return a + b;
        }

        public int direct(int a, int b) {
            return a + b;
        }

        public int callBaseline(int a, int b) {
            return helper(a) + b;
        }

        public int callHooked(int a, int b) {
            return helper(a) + b;
        }

        public int fieldBaseline(int a, int b) {
            return a + b + field;
        }

        public int fieldHooked(int a, int b) {
            return a + b + field;
        }

        private int helper(int a) {
            return a * 3;
        }
    }

    /** Direct hooks must be public static methods of a public class. */
    public static final class Direct {
        public static int calls;

        private Direct() {
        }

        public static void onDirect(Target self, int a, int b) {
            calls++;
        }
    }

    private final Target target = new Target();
    private int a = 3;
    private int b = 4;
    private int sink;

    @Setup
    public void install() {
        var into = Injector.into(Target.class);
        into.method("head").atHead(call -> { });
        into.method("headReadingAnArgument").atHead(call -> sink = call.<Integer>arg(0));
        into.method("headAndReturn").atHead(call -> { });
        into.method("headAndReturn").atReturn(call -> { });
        into.method("returnReplacingTheResult").atReturn(call -> call.setResult(1));
        into.method("throwHookNotTriggered").atThrow(call -> { });
        into.method("direct").direct(At.HEAD, Direct.class, "onDirect");
        into.method("callHooked").atInvoke("helper", call -> { });
        into.method("fieldHooked").onFieldRead("field", call -> { });
    }

    @TearDown
    public void remove() {
        Injector.removeAll();
    }

    @Benchmark
    public int baseline() {
        return target.baseline(a, b);
    }

    @Benchmark
    public int head() {
        return target.head(a, b);
    }

    @Benchmark
    public int headReadingAnArgument() {
        return target.headReadingAnArgument(a, b);
    }

    @Benchmark
    public int headAndReturn() {
        return target.headAndReturn(a, b);
    }

    @Benchmark
    public int returnReplacingTheResult() {
        return target.returnReplacingTheResult(a, b);
    }

    @Benchmark
    public int throwHookNotTriggered() {
        return target.throwHookNotTriggered(a, b);
    }

    @Benchmark
    public int direct() {
        return target.direct(a, b);
    }

    @Benchmark
    public int callBaseline() {
        return target.callBaseline(a, b);
    }

    @Benchmark
    public int callHooked() {
        return target.callHooked(a, b);
    }

    @Benchmark
    public int fieldBaseline() {
        return target.fieldBaseline(a, b);
    }

    @Benchmark
    public int fieldHooked() {
        return target.fieldHooked(a, b);
    }
}
