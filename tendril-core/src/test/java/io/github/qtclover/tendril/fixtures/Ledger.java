package io.github.qtclover.tendril.fixtures;

/** A method with local variables, for LocalsTest. */
public class Ledger {

    public String last;
    private long rate = 10;

    public long settle(long base, int count) {
        long total = base;
        String note = "start";
        for (int i = 0; i < count; i++) {
            total += fee(i);
            note = note + i;
        }
        record(note);
        return total;
    }

    private long fee(int step) {
        return step * rate;
    }

    private void record(String note) {
        last = note;
    }
}
