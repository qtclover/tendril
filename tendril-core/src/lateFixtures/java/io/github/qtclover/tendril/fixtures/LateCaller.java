package io.github.qtclover.tendril.fixtures;

/** Only touched by InvokeTest, after an invoke hook for it is registered by name. */
public class LateCaller {

    public String shout(String text) {
        return text.toUpperCase() + "!";
    }
}
