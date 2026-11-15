package dsltest

import io.github.qtclover.tendril.InjectionException
import io.github.qtclover.tendril.Injector
import io.github.qtclover.tendril.dsl.inject
import io.github.qtclover.tendril.dsl.resultAs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerService {
    var health = 100
        private set

    fun damage(amount: Int) {
        health -= amount
    }

    fun greet(name: String): String = "Hello, $name"

    fun describe(x: Int): String = "int $x"
    fun describe(s: String): String = "string $s"
    fun describe(): String = "nothing"

    fun scale(factor: Double, times: Long): Double = factor * times

    companion object {
        fun twice(x: Int): Int = x * 2
    }
}

class Greeter(private val player: PlayerService) {
    fun welcome(name: String): String = player.greet(name) + " " + player.describe(1) + " " + player.describe(2)
}

class Report {
    var created = 0

    init {
        created = 1
    }

    fun build(items: List<String>): String {
        val out = StringBuilder()
        var length = 0
        // forEach is inline: the lambda body, and its variables, are part of build().
        items.forEach { item ->
            length += item.length
            out.append(tag(item))
        }
        return "$length:$out"
    }

    private fun tag(text: String): String {
        if (text.isEmpty()) throw IllegalArgumentException("empty")
        return "<$text>"
    }
}

object Counter {
    fun next(current: Int): Int = current + 1
}

class DslTest {

    private val player = PlayerService()

    @AfterTest
    fun cleanUp() {
        Injector.removeAll()
    }

    @Test
    fun `the shape from the README works`() {
        val seen = mutableListOf<Any?>()
        Injector.attach()

        val hooks = inject<PlayerService> {
            method("damage") {
                atHead { call -> if (call.arg<Int>(0) > 100) call.cancel() }
                atReturn { call -> seen += "returned ${call.result}" }
            }
        }

        player.damage(500)
        assertEquals(100, player.health)
        player.damage(30)
        assertEquals(70, player.health)
        assertEquals(listOf<Any?>("returned null"), seen)

        hooks.remove()
        assertFalse(hooks.isActive)
        player.damage(500)
        assertEquals(-430, player.health)
    }

    @Test
    fun `arguments and results can be changed`() {
        inject<PlayerService> {
            method("greet") {
                atHead { call -> call.setArg(0, call.arg<String>(0).uppercase()) }
                atReturn { call -> call.result = call.resultAs<String>() + "!" }
            }
            method("scale") {
                atHead { call -> call.setArg(1, 10) } // an Int where a Long is expected
                atReturn { call -> call.result = call.resultAs<Double>() + 0.5 }
            }
        }

        assertEquals("Hello, ADA!", player.greet("Ada"))
        assertEquals(20.5, player.scale(2.0, 3))
    }

    @Test
    fun `self gives access to the receiver`() {
        inject<PlayerService> {
            method("greet") {
                atReturn { call -> call.result = "health ${call.self<PlayerService>().health}" }
            }
        }

        player.damage(1)
        assertEquals("health 99", player.greet("Ada"))
    }

    @Test
    fun `overloads are selected by parameter types or descriptor`() {
        inject<PlayerService> {
            method("describe", Int::class) { atHead { it.cancel("hooked int") } }
            method("describe", "()") { atHead { it.cancel("hooked none") } }
        }

        assertEquals("hooked int", player.describe(1))
        assertEquals("hooked none", player.describe())
        assertEquals("string s", player.describe("s"))
    }

    @Test
    fun `companion objects and objects can be hooked`() {
        // A companion's functions live in the nested class PlayerService$Companion,
        // so that is the class to hook, not PlayerService itself.
        inject(PlayerService.Companion::class) {
            method("twice") { atHead { it.setArg(0, 50) } }
        }
        inject<Counter> {
            method("next") { atReturn { it.result = -1 } }
        }

        assertEquals(100, PlayerService.twice(1))
        assertEquals(-1, Counter.next(1))
    }

    @Test
    fun `a class can be hooked by name before it is loaded`() {
        val name = "io.github.qtclover.tendril.fixtures.LateKotlin"
        fun loaded() = Injector.instrumentation().allLoadedClasses.any { it.name == name }
        assertFalse(loaded())

        val hooks = inject(name) {
            method("name") { atReturn { it.result = "hooked ${it.result}" } }
        }
        assertFalse(loaded())

        val late = Class.forName(name).getDeclaredConstructor().newInstance()
        assertTrue(loaded())
        assertEquals("hooked late", late.javaClass.getMethod("name").invoke(late))

        hooks.close()
        assertEquals("late", late.javaClass.getMethod("name").invoke(late))
    }

    @Test
    fun `calls inside a method can be hooked`() {
        val greeter = Greeter(player)
        assertEquals("Hello, Ada int 1 int 2", greeter.welcome("Ada"))

        val hooks = inject<Greeter> {
            method("welcome") {
                atInvoke("PlayerService.greet") { it.invoked().setArg(0, "Grace") }
                atInvoke("describe", ordinal = 1) { it.invoked().skip("skipped") }
                afterInvoke("describe", ordinal = 0) { it.invoked().result = "${it.invoked().result}!" }
            }
        }
        assertEquals("Hello, Grace int 1! skipped", greeter.welcome("Ada"))

        hooks.remove()
        assertEquals("Hello, Ada int 1 int 2", greeter.welcome("Ada"))
    }

    @Test
    fun `throw, constructor, field and local hooks`() {
        assertEquals("3:<a><bc>", Report().build(listOf("a", "bc")))

        val hooks = inject<Report> {
            constructor {
                onFieldWrite("created") { it.invoked().setArg(0, 42) }
            }
            method("build") {
                atInvoke("tag", locals = true) { call ->
                    assertTrue("length" in call.localNames(), call.localNames().toString())
                    assertTrue("item" in call.localNames(), call.localNames().toString())
                    if (call.local<String>("item") == "bc") call.setLocal("length", 100)
                }
            }
            method("tag") {
                atThrow { it.result = "<?>" }
            }
        }

        val report = Report()
        assertEquals(42, report.created)
        assertEquals("100:<a><bc>", report.build(listOf("a", "bc")))
        assertEquals("1:<a><?>", report.build(listOf("a", "")))

        hooks.remove()
        assertEquals("3:<a><bc>", Report().build(listOf("a", "bc")))
        assertFailsWith<IllegalArgumentException> { report.build(listOf("")) }
    }

    @Test
    fun `a block with a mistake installs nothing`() {
        assertFailsWith<InjectionException> {
            inject<PlayerService> {
                method("greet") { atHead { it.cancel("hooked") } }
                method("doesNotExist") { atHead { } }
            }
        }

        assertEquals("Hello, Ada", player.greet("Ada"))
    }
}
