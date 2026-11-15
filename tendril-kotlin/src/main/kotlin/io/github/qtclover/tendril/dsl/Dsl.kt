package io.github.qtclover.tendril.dsl

import io.github.qtclover.tendril.At
import io.github.qtclover.tendril.CallInfo
import io.github.qtclover.tendril.Hook
import io.github.qtclover.tendril.Injection
import io.github.qtclover.tendril.InjectionBatch
import io.github.qtclover.tendril.Injector
import kotlin.reflect.KClass

@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class TendrilDsl

/**
 * Hooks methods of [T], which is loaded if it is not already.
 *
 * ```
 * val hooks = inject<PlayerService> {
 *     method("damage") {
 *         atHead { call -> if (call.arg<Int>(0) > 100) call.cancel() }
 *         atReturn { call -> println("health is now ${call.self<PlayerService>().health}") }
 *     }
 * }
 * hooks.remove()
 * ```
 *
 * Everything in the block is installed together, attaching to the JVM first if
 * that has not happened yet. The returned [Injection] removes all of it again.
 */
public inline fun <reified T : Any> inject(noinline block: ClassScope.() -> Unit): Injection =
    inject(T::class.java.name, block)

/** Like [inject], for a class you have as a [KClass]. */
public fun inject(target: KClass<*>, block: ClassScope.() -> Unit): Injection =
    inject(target.java.name, block)

/**
 * Hooks [T] and every class that extends or implements it, loaded already or later.
 * A class that does not declare the method itself is left alone, as are JDK classes.
 */
public inline fun <reified T : Any> injectSubtypes(noinline block: ClassScope.() -> Unit): Injection =
    inject(T::class.java.name + "+", block)

/**
 * Like [inject], by binary class name (`com.example.Outer$Inner`). The class is not
 * loaded; if it is not in the JVM yet, the hooks are woven in when it arrives.
 *
 * The name may be a pattern: `"com.example.*"` hooks every class whose name matches,
 * `"com.example.Shape+"` hooks Shape and all its subtypes.
 */
public fun inject(className: String, block: ClassScope.() -> Unit): Injection {
    val batch = Injector.batch()
    ClassScope(className, batch).block()
    return batch.install()
}

@TendrilDsl
public class ClassScope internal constructor(
    private val className: String,
    private val batch: InjectionBatch,
) {
    /** Every method called [name], whatever its parameters. */
    public fun method(name: String, block: MethodScope.() -> Unit) {
        MethodScope(className, name, null, batch).block()
    }

    /**
     * One overload of [name], picked by its parameter types: `method("damage", Int::class)`.
     * `Int::class` and friends mean the primitive types; for a boxed `java.lang.Integer`
     * parameter, or a method with no parameters, use the descriptor overload.
     */
    public fun method(name: String, vararg parameters: KClass<*>, block: MethodScope.() -> Unit) {
        val descriptor = parameters.joinToString("", "(", ")") { it.java.descriptorString() }
        MethodScope(className, name, descriptor, batch).block()
    }

    /**
     * The constructors of the class: all of them, or the one with exactly these
     * [parameters]. Head hooks run right after `super(...)` returns. For the
     * constructor without parameters use `method("<init>", "()")`.
     */
    public fun constructor(vararg parameters: KClass<*>, block: MethodScope.() -> Unit) {
        val descriptor = if (parameters.isEmpty()) null
        else parameters.joinToString("", "(", ")") { it.java.descriptorString() }
        MethodScope(className, "<init>", descriptor, batch).block()
    }

    /**
     * One overload of [name], picked by JVM descriptor: complete (`"(I)V"`) or just the
     * parameter part (`"(I)"`, `"()"`).
     */
    public fun method(name: String, descriptor: String, block: MethodScope.() -> Unit) {
        MethodScope(className, name, descriptor, batch).block()
    }
}

@TendrilDsl
public class MethodScope internal constructor(
    private val className: String,
    private val methodName: String,
    private val descriptor: String?,
    private val batch: InjectionBatch,
) {
    /** Runs [hook] before the method body. */
    public fun atHead(hook: (CallInfo) -> Unit) {
        batch.add(className, methodName, descriptor, At.HEAD, Hook(hook))
    }

    /** Runs [hook] before every normal return. */
    public fun atReturn(hook: (CallInfo) -> Unit) {
        batch.add(className, methodName, descriptor, At.RETURN, Hook(hook))
    }

    /**
     * Runs [hook] when an exception is about to leave the method. The hook can read
     * `thrown`, replace it, or call `swallow()` / set `result` to return normally.
     */
    public fun atThrow(hook: (CallInfo) -> Unit) {
        batch.add(className, methodName, descriptor, At.THROW, Hook(hook))
    }

    /**
     * Runs [hook] just before a call inside the method. [call] is `"name"`,
     * `"Owner.name"`, or either followed by a descriptor. The hook reaches the call
     * through `invoked()` and can change its arguments or skip it:
     *
     * ```
     * atInvoke("PricingService.priceOf") { it.invoked().setArg(1, 1) }
     * ```
     *
     * @param ordinal which of the matching calls, counted from 0; every one by default
     * @param locals  capture the method's local variables, for `local("name")` and `setLocal`
     */
    public fun atInvoke(call: String, ordinal: Int = -1, locals: Boolean = false, hook: (CallInfo) -> Unit) {
        batch.add(className, methodName, descriptor, At.INVOKE, call, ordinal, locals, Hook(hook))
    }

    /** Runs [hook] just after a call inside the method returns; it can read and replace the result. */
    public fun afterInvoke(call: String, ordinal: Int = -1, locals: Boolean = false, hook: (CallInfo) -> Unit) {
        batch.add(className, methodName, descriptor, At.AFTER_INVOKE, call, ordinal, locals, Hook(hook))
    }

    /**
     * Runs [hook] just after the method reads [field] (`"name"` or `"Owner.name"`).
     * `invoked().result` is the value read; assign it to substitute another.
     */
    public fun onFieldRead(field: String, locals: Boolean = false, hook: (CallInfo) -> Unit) {
        batch.add(className, methodName, descriptor, At.FIELD_READ, field, -1, locals, Hook(hook))
    }

    /**
     * Runs [hook] just before the method writes [field]. `invoked().arg(0)` is the
     * value about to be written; `setArg(0, v)` changes it and `skip()` drops the write.
     */
    public fun onFieldWrite(field: String, locals: Boolean = false, hook: (CallInfo) -> Unit) {
        batch.add(className, methodName, descriptor, At.FIELD_WRITE, field, -1, locals, Hook(hook))
    }
}

/** The return value, cast: `call.resultAs<String>()`. */
public inline fun <reified T> CallInfo.resultAs(): T = result as T
