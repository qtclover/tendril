package demo;

/** Prints a line a few times a second until killed, or for a minute at most. */
public final class TargetApp {
    public static void main(String[] args) throws Exception {
        Greeter greeter = new Greeter();
        System.out.println("pid " + ProcessHandle.current().pid());
        for (int i = 0; i < 600; i++) {
            System.out.println("tick " + greeter.greet());
            System.out.flush();
            Thread.sleep(100);
        }
    }
}
