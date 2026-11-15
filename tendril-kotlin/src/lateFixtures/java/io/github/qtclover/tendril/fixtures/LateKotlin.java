package io.github.qtclover.tendril.fixtures;

/** Only touched by the DSL test, after hooks for it are registered by name. */
public class LateKotlin {

    public String name() {
        return "late";
    }
}
