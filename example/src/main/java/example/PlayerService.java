package example;

/** The class being hooked in the demos. It has no idea. */
public class PlayerService {

    private final String name;
    private int health = 100;

    public PlayerService(String name) {
        this.name = name;
    }

    public void damage(int amount) {
        health -= amount;
        System.out.println("  [game] " + name + " takes " + amount + " damage, health " + health);
    }

    public int heal(int amount) {
        health = Math.min(100, health + amount);
        return health;
    }

    public String title() {
        return name + " the Brave";
    }

    public int getHealth() {
        return health;
    }
}
