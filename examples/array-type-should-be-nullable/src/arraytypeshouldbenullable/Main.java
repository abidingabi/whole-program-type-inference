package arraytypeshouldbenullable;

/*
 * This is almost the minimal example, but now we need to annotate in two places instead of one.
 * This requires unification.
 */
public class Main {
    public static void oneDeep(String[] args) {
        String[] foo = { "foo", null };
    }
}
