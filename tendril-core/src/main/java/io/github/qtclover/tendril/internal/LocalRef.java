package io.github.qtclover.tendril.internal;

/** A local variable in scope at some instruction, as recorded in the class file's debug information. */
record LocalRef(String name, String desc, int slot) {
}
