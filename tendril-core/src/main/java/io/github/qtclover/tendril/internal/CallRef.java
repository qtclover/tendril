package io.github.qtclover.tendril.internal;

/**
 * An instruction inside a method body that a hook can be attached to.
 *
 * @param hookable false for instructions that are seen but cannot be wrapped, such as
 *                 anything a constructor does before its {@code super(...)} call returns
 */
record CallRef(Kind kind, String owner, String name, String desc, boolean hookable) {

    enum Kind {
        /** A method call. */
        CALL,
        /** A constructor call that is part of a {@code new} expression. */
        NEW
    }

    String describe() {
        String dotted = owner.replace('/', '.');
        return switch (kind) {
            case CALL -> dotted + "." + name + desc;
            case NEW -> "new " + dotted + desc.substring(0, desc.lastIndexOf(')') + 1);
        };
    }

    /** Descriptor of the value the instruction leaves behind: for {@code new}, the new object. */
    String resultDescriptor() {
        return kind == Kind.NEW ? "()L" + owner + ";" : desc;
    }
}
