package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.InjectionException;

/**
 * Picks call instructions inside a method. Written as {@code name},
 * {@code Owner.name}, or either followed by a full or parameter-only descriptor:
 * {@code PricingService.priceOf(Ljava/lang/String;I)}.
 */
public final class CallSelector {

    private final String text;
    /** Dotted; a full binary name or just its last part. Null for any owner. */
    private final String owner;
    private final String name;
    private final String descriptor;

    private CallSelector(String text, String owner, String name, String descriptor) {
        this.text = text;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    public static CallSelector parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        int paren = trimmed.indexOf('(');
        String descriptor = paren < 0 ? null : Descriptors.validate(trimmed.substring(paren));
        String head = (paren < 0 ? trimmed : trimmed.substring(0, paren)).replace('/', '.').replace('#', '.');
        int dot = head.lastIndexOf('.');
        String owner = dot < 0 ? null : head.substring(0, dot);
        String name = head.substring(dot + 1);
        if (name.isEmpty() || (owner != null && owner.isEmpty())) {
            throw new InjectionException("Not a call selector: \"" + text
                    + "\" (expected \"name\", \"Owner.name\" or \"Owner.name(I)V\")");
        }
        if (name.startsWith("<")) {
            throw new InjectionException("Constructor calls cannot be hooked (yet): \"" + text + "\"");
        }
        return new CallSelector(trimmed, owner, name, descriptor);
    }

    boolean matches(CallRef call) {
        if (!name.equals(call.name()) || !Descriptors.matches(descriptor, call.desc())) {
            return false;
        }
        if (owner == null) {
            return true;
        }
        String actual = call.owner().replace('/', '.');
        return actual.equals(owner) || actual.endsWith("." + owner) || actual.endsWith("$" + owner);
    }

    @Override
    public String toString() {
        return text;
    }
}
