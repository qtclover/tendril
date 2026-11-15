package io.github.qtclover.tendril.fixtures;

/** A plain class for the tests to hook. Nothing in here knows about Tendril. */
public class PlayerService {

    private int health = 100;

    public void damage(int amount) {
        health -= amount;
    }

    public int health() {
        return health;
    }

    public String greet(String name) {
        return "Hello, " + name;
    }

    public int add(int a, int b) {
        return a + b;
    }

    /** Wide arguments: long and double take two local variable slots each. */
    public long mix(long a, double b, int c, String d) {
        return a + (long) b + c + d.length();
    }

    public double half(double value) {
        return value / 2;
    }

    public long big() {
        return 1L << 40;
    }

    public float ratio() {
        return 0.5f;
    }

    public boolean isAlive() {
        return health > 0;
    }

    public char initial(String name) {
        return name.charAt(0);
    }

    public byte lowByte(int value) {
        return (byte) value;
    }

    public short lowShort(int value) {
        return (short) value;
    }

    public int[] range(int n) {
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = i;
        }
        return result;
    }

    public static int twice(int x) {
        return x * 2;
    }

    public String describe() {
        return "nothing";
    }

    public String describe(int x) {
        return "int " + x;
    }

    public String describe(String s) {
        return "string " + s;
    }

    /** Several return instructions, a loop, a switch and a try/finally. */
    public String classify(int n) {
        if (n < 0) {
            return "negative";
        }
        try {
            switch (n) {
                case 0:
                    return "zero";
                case 1:
                    return "one";
                default:
                    break;
            }
            int sum = 0;
            for (int i = 0; i < n; i++) {
                sum += i;
            }
            return sum > 10 ? "big" : "small";
        } finally {
            health++;
        }
    }

    public int factorial(int n) {
        return n <= 1 ? 1 : n * factorial(n - 1);
    }

    public synchronized int guarded(int x) {
        return x + 1;
    }

    public int parse(String text) {
        return Integer.parseInt(text);
    }

    public Object pick(boolean first, Integer a, String b) {
        // Two branches with different types merging: makes ASM ask for a common superclass.
        Comparable<?> value = first ? a : b;
        return value;
    }
}
