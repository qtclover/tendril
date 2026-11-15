package io.github.qtclover.tendril.fixtures;

/** Field reads and writes for FieldTest. */
public class Account {

    public static int opened;
    public static final int LIMIT = 1000;   // a compile-time constant: reads of it are inlined

    private long balance;
    private String owner;
    private final int id;

    public Account(String owner) {
        this.owner = owner;
        this.id = ++opened;
    }

    public void deposit(long amount) {
        balance = balance + amount;
    }

    public long balance() {
        return balance;
    }

    public String describe() {
        return owner + " has " + balance;
    }

    public void rename(String newOwner) {
        owner = newOwner;
    }

    public int limit() {
        return LIMIT;
    }

    public static int count() {
        return opened;
    }

    public static void reset() {
        opened = 0;
    }

    public int id() {
        return id;
    }
}
