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

    /** The class to hook, by binary name. Works for classes that are not loaded yet. */
    String targetName() default "";

    /** Name of the method to hook. */
    String method();

    /**
     * Selects one overload: a full descriptor such as {@code (I)V}, or only the
     * parameter part such as {@code (I)}. Empty means every overload with that name.
     */
    String descriptor() default "";

    At at() default At.HEAD;
}
