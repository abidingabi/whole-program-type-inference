package net.dogbuilt.wpi;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.List;

public interface SearchAlgorithm {
    /*
     * annotates a set of locations. we can call this repeatedly until exhausted is true and get unique results.
     *
     * we mutate the list, and so we should pass in a list from a fresh compilation unit each time
     */
    void annotate(List<NodeWithAnnotations<? extends Node>> locations);

    /* returns true if we decide to give up, either because of a heuristic or because we exhausted the search space */
    boolean exhausted();
}
