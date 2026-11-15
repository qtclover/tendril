package io.github.qtclover.tendril.fixtures;

import io.github.qtclover.tendril.At;
import io.github.qtclover.tendril.Inject;

import java.util.ArrayList;
import java.util.List;

/** Direct hooks must be public static methods of a public class. */
public final class DirectHooks {

    public static final List<String> seen = new ArrayList<>();

    private DirectHooks() {
    }

    public static void onDamage(PlayerService self, int amount) {
        seen.add("damage " + amount + " at health " + self.health());
    }

    public static void afterAdd(Object self, int a, int b, int result) {
        seen.add(a + "+" + b + "=" + result);
    }

    public static void afterMix(PlayerService self, long a, double b, int c, String d, long result) {
        seen.add("mix " + a + " " + b + " " + c + " " + d + " -> " + result);
    }

    public static void onTwice(int x) {
        seen.add("twice " + x);
    }

    public static void describeString(PlayerService self, String s) {
        seen.add("describe string " + s);
    }

    public static void afterGreet(PlayerService self, String name, String result) {
        seen.add("greet -> " + result);
    }

    public static void onWidget(Widgets.Widget self, int size) {
        seen.add("widget " + size + " tag " + self.tag);
    }

    public static int returnsSomething(PlayerService self, int amount) {
        return 0;
    }

    static void notPublic(PlayerService self, int amount) {
    }

    public static void wrongShape(PlayerService self, String notAnInt) {
    }

    /** For the annotation form. */
    public static final class Annotated {
        private Annotated() {
        }

        @Inject(target = PlayerService.class, method = "damage", direct = true)
        public static void onDamage(PlayerService self, int amount) {
            seen.add("annotated damage " + amount);
        }

        @Inject(target = PlayerService.class, method = "isAlive", at = At.RETURN, direct = true)
        public static void afterIsAlive(PlayerService self, boolean alive) {
            seen.add("alive " + alive);
        }
    }
}
