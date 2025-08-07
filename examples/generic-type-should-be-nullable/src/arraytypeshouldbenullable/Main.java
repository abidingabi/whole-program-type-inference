package arraytypeshouldbenullable;

import java.util.ArrayList;
import java.util.List;

/*
 * This is almost the minimal example, but now we need to annotate in two places instead of one.
 * This requires unification.
 */
public class Main {
    public static void main(String[] args) {
        List<String> foo = new ArrayList<>();
        foo.add("foo");
        foo.add(null);
    }
}
