package net.dogbuilt.wpi;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class AnnotatableLocationHelper {
    /*
     * locate all locations where we should annotate types in a given list of compilation units
     */
    public static List<NodeWithAnnotations<? extends Node>> getLocations(List<CompilationUnit> compilationUnits)
            throws FileNotFoundException {

        var results = new ArrayList<NodeWithAnnotations<? extends Node>>();
        for (var cu : compilationUnits) {
            results.addAll(getLocationsCu(cu));
        }

        return results;
    }
    /*
     * locate all locations where we should annotate types in a given compilation unit
     *
     * - fields
     * - method parameters
     * - return types
     * - generic parameters
     * TODO: handle array declarations properly?
     *  e.g. @Nullable (Object[])
     *  is not the same as (@Nullable Object)[]
     *  is not the same as @Nullable((@Nullable Object)[]).
     *  At least morally; I am not sure if I got the relevant syntax correct.
     *  This will be ugly but necessary.
     */
    private static List<NodeWithAnnotations<? extends Node>> getLocationsCu(CompilationUnit cu)
            throws FileNotFoundException {
        var annotatableLocations = new ArrayList<NodeWithAnnotations<? extends Node>>();

        annotatableLocations.addAll(cu.findAll(FieldDeclaration.class));
        /*
         * getType on a method /should/ return something that implements NodeWithAnnotations;
         * instead we are forced to cast. I think? this is safe.
         */
        annotatableLocations.addAll(cu.findAll(MethodDeclaration.class)
                .stream()
                .map(MethodDeclaration::getType)
                .map(n -> (NodeWithAnnotations<Node>) n).toList());
        annotatableLocations.addAll(cu.findAll(Parameter.class));
        annotatableLocations.addAll(cu.findAll(TypeParameter.class));

        return annotatableLocations;
    }

    static List<CompilationUnit> getCompilationUnits(String baseDirectory) throws IOException {
        var paths = Files
                .walk(Paths.get(baseDirectory))
                .filter(Files::isRegularFile)
                .toList();
        var results = new ArrayList<CompilationUnit>();
        for (var path : paths) {
            results.add(StaticJavaParser.parse(path));
        }

        return results;
    }
}
