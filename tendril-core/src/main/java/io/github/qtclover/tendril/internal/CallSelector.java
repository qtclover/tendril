package io.github.qtclover.tendril.internal;

import io.github.qtclover.tendril.InjectionException;

/**
 * Picks call instructions inside a method. Written as {@code name},
 * {@code Owner.name}, or either followed by a full or parameter-only descriptor:
 * {@code PricingService.priceOf(Ljava/lang/String;I)}. {@code new Owner} picks
 * constructor calls.
 */
public final class CallSelector {

    private final String text;
    private final CallRef.Kind kind;
    /** Dotted; a full binary name or just its last part. Null for any owner. */
    private final String owner;
    private final String name;
    private final String descriptor;

    private CallSelector(String text, CallRef.Kind kind, String owner, String name, String descriptor) {
        this.text = text;
        this.kind = kind;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    /** For {@link io.github.qtclover.tendril.At#FIELD_READ} and FIELD_WRITE: {@code name} or {@code Owner.name}. */
    public static CallSelector parseField(String text, boolean write) {
        String trimmed = text == null ? "" : text.trim();
        String head = trimmed.replace('/', '.').replace('#', '.');
        int dot = head.lastIndexOf('.');
        String owner = dot < 0 ? null : head.substring(0, dot);
        String name = head.substring(dot + 1);
        if (name.isEmpty() || (owner != null && owner.isEmpty()) || head.indexOf('(') >= 0) {
            throw new InjectionException("Not a field selector: \"" + text
                    + "\" (expected \"name\" or \"Owner.name\")");
        }
        return new CallSelector(trimmed, write ? CallRef.Kind.FIELD_WRITE : CallRef.Kind.FIELD_READ,
                owner, name, null);
    }

    public static CallSelector parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        String rest = trimmed;
        boolean isNew = rest.startsWith("new ");
        if (isNew) {
            rest = rest.substring(4).trim();
        }
        int paren = rest.indexOf('(');
        String descriptor = paren < 0 ? null : Descriptors.validate(rest.substring(paren));
        String head = (paren < 0 ? rest : rest.substring(0, paren)).replace('/', '.').replace('#', '.');
        String owner;
        String name;
        if (isNew) {
            // "new Foo" or "new Foo(I)"
            owner = head;
            name = "<init>";
        } else {
            int dot = head.lastIndexOf('.');
            owner = dot < 0 ? null : head.substring(0, dot);
            name = head.substring(dot + 1);
            isNew = name.equals("<init>");   // "Foo.<init>" means the same as "new Foo"
        }
        if (name.isEmpty() || (owner != null && owner.isEmpty()) || (isNew && owner == null)
                || (name.startsWith("<") && !isNew)) {
            throw new InjectionException("Not a call selector: \"" + text
                    + "\" (expected \"name\", \"Owner.name\", \"Owner.name(I)V\" or \"new Owner\")");
        }
        return new CallSelector(trimmed, isNew ? CallRef.Kind.NEW : CallRef.Kind.CALL, owner, name, descriptor);
    }

    boolean matches(CallRef call) {
        if (!call.hookable() || kind != call.kind() || !name.equals(call.name())
                || !Descriptors.matches(descriptor, call.desc())) {
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
