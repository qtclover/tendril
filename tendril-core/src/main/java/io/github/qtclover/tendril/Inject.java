package io.github.qtclover.tendril;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a hook. Install with {@link Injector#register(Class)} (static
 * methods) or {@link Injector#register(Object)} (static and instance methods).
 *
 * <pre>{@code
 * @Inject(target = PlayerService.class, method = "damage", at = At.HEAD)
 * static void onDamage(CallInfo call) { ... }
 * }</pre>
 *
 * <p>The hook method takes a single {@link CallInfo}, or no parameters at all.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Inject {

    /** The class to hook. Using this loads the class; see {@link #targetName()} to avoid that. */
    Class<?> target() default void.class;

    /**
     * The class to hook, by binary name. Works for classes that are not loaded yet.
     * Also accepts patterns: {@code "com.example.*"} for every class whose name
     * matches, {@code "com.example.Shape+"} for Shape and all its subtypes.
     */
    String targetName() default "";

    /** Name of the method to hook. */
    String method();

    /**
     * Selects one overload: a full descriptor such as {@code (I)V}, or only the
     * parameter part such as {@code (I)}. Empty means every overload with that name.
     */
    String descriptor() default "";

    /** With {@link #target()}: also hook every class that extends or implements it. */
    boolean subtypes() default false;

    At at() default At.HEAD;

    /**
     * For {@link At#INVOKE} and {@link At#AFTER_INVOKE}: the call inside the method to
     * hook, as {@code "name"}, {@code "Owner.name"}, or either followed by a descriptor,
     * or {@code "new Owner"} for a constructor call. For {@link At#FIELD_READ} and
     * {@link At#FIELD_WRITE}: the field, as {@code "name"} or {@code "Owner.name"}.
     */
    String invoke() default "";

    /** With {@link #invoke()}: which of the matching calls, counted from 0. Negative means all. */
    int ordinal() default -1;

    /**
     * For points inside a method: capture the method's local variables, for
     * {@link CallInfo#local} and {@link CallInfo#setLocal}.
     */
    boolean locals() default false;
}
