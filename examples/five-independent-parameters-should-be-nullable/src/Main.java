package fiveindependentparametersshouldbenullable;

/* Here we test that Specimin properly splits up the program, allowing us to consider warnings independently. */
public class Main {
    private static void parameterShouldBeNullable1(String foo) {
        return;
    };
    private static void parameterShouldBeNullable2(String foo) {
        return;
    };
    private static void parameterShouldBeNullable3(String foo) {
        return;
    };
    private static void parameterShouldBeNullable4(String foo) {
        return;
    };
    private static void parameterShouldBeNullable5(String foo) {
        return;
    };

    /*
     * We intentionally split the calls into different methods, as Specimin operates on the precision of methods and
     * would otherwise not break up the program at all.
     */
    private static void call1() {
        parameterShouldBeNullable1(null);
    }
    private static void call2() {
        parameterShouldBeNullable2(null);
    }
    private static void call3() {
        parameterShouldBeNullable3(null);
    }
    private static void call4() {
        parameterShouldBeNullable4(null);
    }
    private static void call5() {
        parameterShouldBeNullable5(null);
    }

    public static void main() {
        call1();
        call2();
        call3();
        call4();
        call5();
    }
}
