package io.github.qtclover.tendril.internal;

import java.util.List;

/**
 * An instruction inside a method body that a hook can be attached to.
 *
 * @param locals   the named local variables in scope at the instruction, or null if the
 *                 class was compiled without that debug information
 * @param hookable false for instructions that are seen but cannot be wrapped, such as
 *                 anything a constructor does before its {@code super(...)} call returns
 */
record CallRef(Kind kind, String owner, String name, String desc, boolean hookable, List<LocalRef> locals) {

    enum Kind {
        /** A method call. */
        CALL,
        /** A constructor call that is part of a {@code new} expression. */
        NEW,
        /** A field read. Described as a call with no arguments that returns the value: {@code ()T}. */
        FIELD_READ,
        /** A field write. Described as a call with the new value as its only argument: {@code (T)V}. */
        FIELD_WRITE
    }

    String describe() {
        String dotted = owner.replace('/', '.');
        return switch (kind) {
            case CALL -> dotted + "." + name + desc;
            case NEW -> "new " + dotted + desc.substring(0, desc.lastIndexOf(')') + 1);
            case FIELD_READ, FIELD_WRITE -> dotted + "." + name;
        };
    }

    /** Descriptor of the value the instruction leaves behind: for {@code new}, the new object. */
    String resultDescriptor() {
        return kind == Kind.NEW ? "()L" + owner + ";" : desc;
    }
}
