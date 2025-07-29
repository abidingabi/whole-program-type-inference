package net.dogbuilt.wpi.searchalgorithms;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.List;

/* annotates each location with a given annotation, one at a time */
public class AnnotateOneLocation implements SearchAlgorithm {
    private int locationsAnnotated = 0;
    private final int locationCount;
    private final String annotation;

    /**
     * Construct an instance of AnnotateOneLocation
     *
     * @param locationCount the total number of annotatable locations
     * @param annotation    what annotation to add
     */
    public AnnotateOneLocation(int locationCount, String annotation) {
        this.locationCount = locationCount;
        this.annotation = annotation;
    }

    @Override
    public void annotate(List<NodeWithAnnotations<? extends Node>> locations) {
        locations.get(locationsAnnotated).addAnnotation(annotation);
        locationsAnnotated++;
    }

    @Override
    public boolean exhausted() {
        return locationsAnnotated == locationCount;
    }
}
