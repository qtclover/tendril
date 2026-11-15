package io.github.qtclover.tendril.fixtures;

public final class Shapes {

    private Shapes() {
    }

    public interface Shape {
        double area();

        default String label() {
            return "shape with area " + area();
        }
    }

    public abstract static class Base implements Shape {
        public String name() {
            return "base";
        }

        public abstract int sides();
    }

    public static class Square extends Base {
        private final double side;

        public Square(double side) {
            this.side = side;
        }

        @Override
        public double area() {
            return side * side;
        }

        @Override
        public int sides() {
            return 4;
        }
    }
}
