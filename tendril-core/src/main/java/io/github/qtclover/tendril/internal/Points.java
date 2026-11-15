package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.At;

/** Which injection points sit on an instruction inside a method, rather than on the method as a whole. */
public final class Points {

    private Points() {
    }

    public static boolean isInsideMethod(At at) {
        return switch (at) {
            case HEAD, RETURN, THROW -> false;
            case INVOKE, AFTER_INVOKE, FIELD_READ, FIELD_WRITE -> true;
        };
    }

    /** For points inside a method: whether hooks run before the instruction (true) or after it. */
    static boolean runsBefore(At at) {
        return switch (at) {
            case INVOKE, FIELD_WRITE -> true;
            case AFTER_INVOKE, FIELD_READ -> false;
            case HEAD, RETURN, THROW -> throw new IllegalStateException("not a point inside a method: " + at);
        };
    }
}
