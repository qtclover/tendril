package example;

/**
 * An application that does not use Tendril at all. Run through the
 * "runWithAgent" task it is hooked anyway, by {@link AgentHooks}, from the command line:
 * {@code java -javaagent:tendril-agent.jar=example.AgentHooks ...}
 */
public final class PlainApp {

    public static void main(String[] args) {
        PlayerService player = new PlayerService("Linus");
        player.damage(250);
        player.damage(10);
        System.out.println("  title = " + player.title());
    }
}
