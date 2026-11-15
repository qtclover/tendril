package io.github.qtclover.tendril.fixtures;

import java.util.ArrayList;
import java.util.List;

/** Methods that call other methods, for the invoke-point tests. */
public class Shop {

    public interface Pricing {
        int priceOf(String item, int quantity);
    }

    public static class Catalog implements Pricing {
        @Override
        public int priceOf(String item, int quantity) {
            return item.length() * 10 * quantity;
        }
    }

    private final Pricing pricing = new Catalog();
    public final List<String> log = new ArrayList<>();

    /** An interface call, a private void call and a private call with a result. */
    public int checkout(String item, int quantity) {
        int price = pricing.priceOf(item, quantity);
        audit("sold " + item);
        return price + shipping(price);
    }

    private void audit(String message) {
        log.add(message);
    }

    private int shipping(int price) {
        return price > 100 ? 0 : 5;
    }

    public static long scale(long value, double factor) {
        return (long) (value * factor);
    }

    /** The same static method called twice, with two-slot arguments. */
    public long scaled(long value) {
        return scale(value, 1.5) + scale(value, 2.0);
    }

    /** A call whose result is a constructor argument: an uninitialised object is on the stack around it. */
    public String wrap(String text) {
        return new StringBuilder(decorate(text)).append("!").toString();
    }

    private String decorate(String text) {
        return "[" + text + "]";
    }

    /** A call inside a loop and a try/catch. */
    public int total(int[] values) {
        int sum = 0;
        for (int value : values) {
            try {
                sum += checked(value);
            } catch (IllegalArgumentException e) {
                sum -= 1;
            }
        }
        return sum;
    }

    private int checked(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative");
        }
        return value;
    }

    /** A call inside a synchronized block and a try/finally. */
    public int locked(int price) {
        synchronized (this) {
            try {
                return shipping(price);
            } finally {
                log.add("unlocked");
            }
        }
    }
}
