package twoparametersshouldbenullable;

/*
 * This is almost the minimal example, but now we need to annotate in two places instead of one.
 * This requires unification.
 */
public class Main {
    private static void parametersShouldBeNullable(String foo, String bar) {
        return;
    };

    public static void main(String[] args) {
        parametersShouldBeNullable(null, null);
    }
}
