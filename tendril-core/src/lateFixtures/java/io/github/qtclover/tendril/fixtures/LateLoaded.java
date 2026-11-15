package io.github.qtclover.tendril.fixtures;

/** Only ever touched by LateLoadTest, and only after hooks for it are registered. */
public class LateLoaded {

    public String name() {
        return "late";
    }

    public int number() {
        return 7;
    }
}
