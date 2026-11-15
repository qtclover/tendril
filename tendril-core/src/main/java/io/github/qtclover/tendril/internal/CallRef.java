package io.github.qtclover.tendril.internal;

/** A method call instruction found in a method body. */
record CallRef(String owner, String name, String desc) {

    String describe() {
        return owner.replace('/', '.') + "." + name + desc;
    }
}
