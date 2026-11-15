package example

import io.github.qtclover.tendril.Injector
import io.github.qtclover.tendril.dsl.inject

fun main() {
    val player = PlayerService("Ada")

    println("Before any hooks:")
    player.damage(30)
    println("  title = ${player.title()}")

    Injector.attach()
    println("\nAttached (${Injector.attachMode()}). Installing hooks:")

    val hooks = inject<PlayerService> {
        method("damage") {
            atHead { call ->
                val amount = call.arg<Int>(0)
                when {
                    amount > 100 -> {
                        println("  [hook] $amount damage? cancelled")
                        call.cancel()
                    }
                    amount > 20 -> {
                        println("  [hook] capping $amount damage at 20")
                        call.setArg(0, 20)
                    }
                }
            }
            atReturn { call -> println("  [hook] damage() returned, health ${call.self<PlayerService>().health}") }
        }
        method("title") {
            atReturn { call -> call.result = "${call.result} (hooked)" }
        }
        method("heal") {
            // heal() computes Math.min(100, health + amount): raise the cap for that one call.
            atInvoke("Math.min") { call -> call.invoked().setArg(0, 150) }
        }
    }

    player.damage(250)
    player.damage(60)
    player.damage(5)
    println("  title = ${player.title()}")
    println("  heal(500) = ${player.heal(500)}  (the cap of 100 inside heal() was raised to 150)")

    hooks.remove()
    println("\nHooks removed, original bytecode restored:")
    player.damage(60)
    println("  title = ${player.title()}")
    println("  heal(500) = ${player.heal(500)}")
}
