package io.github.qtclover.tendril.fixtures;

/** Constructors and {@code new} expressions for ConstructorTest. */
public class Widgets {

    public static int built;

    public static class Base {
        public final String tag;

        public Base(String tag) {
            this.tag = tag;
        }
    }

    public static class Widget extends Base {
        public int size;
        public String label = "plain";

        /** A call inside the super(...) arguments. */
        public Widget(int size) {
            super(describe(size));
            this.size = size;
            built++;
        }

        /** Delegates to the other constructor. */
        public Widget(String label) {
            this(label.length());
            this.label = label;
        }

        public Widget(boolean fail) {
            super("flag");
            if (fail) {
                throw new IllegalStateException("boom");
            }
        }

        static String describe(int size) {
            return "size " + size;
        }
    }

    public static class Box {
        public final Widget widget;
        public final String name;

        public Box(Widget widget, String name) {
            this.widget = widget;
            this.name = name;
        }
    }

    public Widget make(int size) {
        return new Widget(size);
    }

    /** One new expression as the argument of another. */
    public Box nested(int size) {
        return new Box(new Widget(size), "box");
    }

    /** Branches between the new and its constructor call. */
    public Box choice(boolean big) {
        return new Box(new Widget(big ? 100 : 1), big ? "big" : "small");
    }

    /** The new object is thrown away. */
    public void discard() {
        new Widget(3);
    }
}
