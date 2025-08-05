package oneparametershouldbenullable;

/* This is basically the minimal example of a program with a type error */
public class Main {
    private static void parameterShouldBeNullable(String foo) {
        return;
    };

    public static void main(String[] args) {
        parameterShouldBeNullable(null);
    }
}
