package net.dogbuilt.wpi.searchalgorithms;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class BreadthFirstSearch implements SearchAlgorithm {
    private int maxDepth;
    private String annotation;
    private Queue<HashMap<Integer, String>> queue = new LinkedList<>();
    private int initialAnnotationCount;

    public BreadthFirstSearch(int maxDepth, String annotation) {
        this.maxDepth = maxDepth;
        this.annotation = annotation;
    }

    @Override
    public void annotate(List<NodeWithAnnotations<? extends Node>> locations) {
        if (queue.isEmpty()) {
            queue.add(new HashMap<>()); // TODO: initialize correctly
            initialAnnotationCount = 0; // TODO: set to the number of annotations
        }

        var top = queue.remove();

        for (var index : top.keySet()) {
            locations.get(index).addAnnotation(top.get(index));
        }


        for (int i = 0; i < locations.size(); i++) {
            var newState = (HashMap<Integer, String>) top.clone();
            if (!newState.containsKey(i)) {
                newState.put(i, annotation);
                queue.add(newState);
            }
        }
    }

    @Override
    public boolean exhausted() {
        // TODO: I think this is incorrect if maxDepth is high enough that we exhaust the queue?
        var top = queue.peek();
        return top != null && top.size() - initialAnnotationCount > maxDepth;
    }
}
