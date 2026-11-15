package example;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.CallInfo;
import io.github.qtclover.tendril.Inject;
import io.github.qtclover.tendril.Injection;
import io.github.qtclover.tendril.Injector;

public final class JavaDemo {

    /** Hooks are plain static methods; the annotation says where they go. */
    static final class Hooks {

        @Inject(target = PlayerService.class, method = "damage", at = At.HEAD)
        static void onDamage(CallInfo call) {
            int amount = call.arg(0);
            if (amount > 100) {
                System.out.println("  [hook] " + amount + " damage? cancelled");
                call.cancel();
            }
        }

        @Inject(target = PlayerService.class, method = "title", at = At.RETURN)
        static void onTitle(CallInfo call) {
            call.setResult(call.getResult() + " (hooked)");
        }
    }

    public static void main(String[] args) {
        PlayerService player = new PlayerService("Grace");

        System.out.println("Annotation API:");
        Injection hooks = Injector.register(Hooks.class);
        player.damage(250);
        player.damage(10);
        System.out.println("  title = " + player.title());

        System.out.println("\nFluent API, scoped with try-with-resources:");
        try (Injection heal = Injector.into(PlayerService.class)
                .method("heal", int.class)
                .atHead(call -> call.setArg(0, call.<Integer>arg(0) * 2))) {
            System.out.println("  heal(3) with the hook = " + player.heal(3) + " (active: " + heal.isActive() + ")");
        }
        System.out.println("  heal(3) without it    = " + player.heal(3));

        hooks.remove();
        System.out.println("\nAll hooks removed:");
        player.damage(250);
        System.out.println("  title = " + player.title());
    }
}
