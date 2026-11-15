package io.github.qtclover.tendril.fixtures.zoo;

public interface Animal {
    String sound();

    default String describe() {
        return "says " + sound();
    }
}
