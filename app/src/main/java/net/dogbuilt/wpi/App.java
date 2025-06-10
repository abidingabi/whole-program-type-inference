package net.dogbuilt.wpi;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class App {
    public String getGreeting() {
        return "Hello World!";
    }

    private static List<Warning> getWarnings(String checker, String path) {

        String[] errorLines = {"/home/abi/school/kellogg-research/whole-program-type-inference/get-error-lines.sh", checker, path};
        ProcessBuilder errorLinesPB = new ProcessBuilder(errorLines);

        var warnings = new ArrayList<Warning>();

        try {
            Process process = errorLinesPB.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                var sections = line.split("\t");
                if (sections.length == 2) {
                    Warning w = new Warning(sections[0], Integer.parseInt(sections[1]));
                    warnings.add(w);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("Exited with code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
        }

        return warnings;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.out.println("two arguments expected: checker, project directory");
            return;
        }

        var src = args[1] + "/src/";

        /* to check the warnings, we care about the lib directory; hence us not using src */
        var warnings = getWarnings(args[0], args[1]);
        System.out.println(warnings.size());

        // locations to run specimin on
        // the duplication between methods and fields here is ugly; we can do better later if needed
        var methods = warnings
                .stream()
                .map(w -> {
                    try {
                        return w.getEnclosingMethod().map(m -> new AbstractMap.SimpleEntry<>(m, w));
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(
                        AbstractMap.SimpleEntry::getKey,
                        AbstractMap.SimpleEntry::getValue,
                        /* since specimin is fairly coarse, we can ignore multiple warnings in the same field */
                        (a, b) -> a));

        var fields = warnings
                .stream()
                .map(w -> {
                    try {
                        return w.getEnclosingField().map(f -> new AbstractMap.SimpleEntry<>(f, w));
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(
                        AbstractMap.SimpleEntry::getKey,
                        AbstractMap.SimpleEntry::getValue,
                        /* since specimin is fairly coarse, we can ignore multiple warnings in the same field */
                        (a, b) -> a));

        /* TODO: deal with warnings not at specimin-able locations */
        List<Optional<String>> speciminMethodOutDirs = methods.keySet().stream().map(method -> {
            var warning = methods.get(method);
            // TODO: this is duplicated in specimin tool. this is bad
            var parameterTypes = method
                    .getParameters()
                    .stream()
                    .map(p -> p.getTypeAsString() + (p.isVarArgs() ? "..." : ""))
                    .collect(Collectors.joining(", "));

            Optional<String> fullyQualifiedClassName;
            try {
                fullyQualifiedClassName = warning.getFullyQualifiedClassName();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            if (fullyQualifiedClassName.isEmpty()) {
                System.out.println(warning);
                System.out.println("Could not find FQCN?");
                return Optional.<String>empty();
            }

            var target = fullyQualifiedClassName.get() + "#" + method.getName() + "(" + parameterTypes + ")";

            System.out.println(target);
            try {
                return Optional.of(SpeciminTool.runSpeciminTool(
                        src,
                        args[1] + "/lib/",
                        // TODO: this is really janky and breaks when the argument has a trailing slash.
                        warning.file().substring(src.length()),
                        target,
                        SpeciminTool.SpeciminTargetType.METHOD));
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).toList();

        List<Optional<String>> speciminFieldOutDirs = fields.keySet().stream().map(field -> {
            var warning = fields.get(field);

            Optional<String> fullyQualifiedClassName = null;
            try {
                fullyQualifiedClassName = warning.getFullyQualifiedClassName();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            if (fullyQualifiedClassName.isEmpty()) {
                System.out.println(warning);
                System.out.println("Could not find FQCN?");
                return Optional.<String>empty();
            }

            /* TODO: we make an assumption here that the warning is on the initializer for the first variable in a
             *  declaration. Unfortunately, with just the line number from javac, this is nontrivial to do correctly.
             */
            var target = fullyQualifiedClassName.get() + "#" + field.getVariable(0).getName();

            System.out.println(target);
            try {
                return Optional.of(SpeciminTool.runSpeciminTool(
                        src,
                        args[1] + "/lib/",
                        // TODO: this is really janky and breaks when the argument has a trailing slash.
                        warning.file().substring(src.length()),
                        target,
                        SpeciminTool.SpeciminTargetType.FIELD));
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).toList();

        var speciminOutDirs = new ArrayList<String>();
        for (var methodDir : speciminMethodOutDirs)
            methodDir.ifPresent(speciminOutDirs::add);
        for (var fieldDir : speciminFieldOutDirs)
            fieldDir.ifPresent(speciminOutDirs::add);

        var fieldsAndMethodsForDirs = speciminOutDirs.stream().map(directoryPath -> {
            try {
                return SpeciminTool.minimizedFilesFieldsAndMethods(directoryPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet()).stream().toList();

        /* check for overlap */
        var conflictDeclarations = new HashSet<String>();
        var declarations = new HashSet<String>();
        /* we should probably be doing something more clever than a cartesian product... */

        /* map from a specimin outputs to the set of specimin outputs that overlap with it */
        HashMap<Set<String>, Set<Set<String>>> partitions = new HashMap<>();
        /* the number of declarations for each specimin output is the diagonal */
        System.out.println("Collision matrix");
        for (var fieldsAndMethods1 : fieldsAndMethodsForDirs) {
            if (!partitions.containsKey(fieldsAndMethods1)) {
                /* we cannot use Set.of because that returns an immutable set */
                var contents = new HashSet<Set<String>>();
                contents.add(fieldsAndMethods1);
                partitions.put(fieldsAndMethods1, contents);
            }
            for (var fieldsAndMethods2 : fieldsAndMethodsForDirs) {
                if (!partitions.containsKey(fieldsAndMethods2)) {
                    var contents = new HashSet<Set<String>>();
                    contents.add(fieldsAndMethods2);
                    partitions.put(fieldsAndMethods2, contents);
                }

                if (fieldsAndMethods1 == fieldsAndMethods2) {
                    System.out.print(fieldsAndMethods1.size());
                    System.out.print("\t");
                    continue;
                }
                var intersection = new ArrayList<>(fieldsAndMethods1);
                intersection.retainAll(fieldsAndMethods2);
                if (intersection.isEmpty()) {
                    System.out.print(0);
                    System.out.print("\t");
                } else {
                    var partition1 = partitions.get(fieldsAndMethods1);
                    var partition2 = partitions.get(fieldsAndMethods2);
                    partition1.addAll(partition2);
                    /*
                     * partition 1 is now the union.
                     * I dislike the mutable state here, but otherwise we do a bunch of needless copying.
                     */
                    partitions.put(fieldsAndMethods2, partition1);

                    conflictDeclarations.addAll(intersection);
                    System.out.print(intersection.size());
                    System.out.print("\t");
                }
            }
            declarations.addAll(fieldsAndMethods1);
            System.out.println();
            System.out.flush();
        }

        /* note the order they are outputted here has /nothing/ to do with the order in the collision matrix */
        var uniquePartitions = new HashSet<>(partitions.values());
        System.out.println("Partition count");
        System.out.println(uniquePartitions.size());
        System.out.println("Partition sizes");
        for (var partition : uniquePartitions) {
            System.out.println(partition.size());
        }

        System.out.println("Conflict declarations: " + conflictDeclarations.size());
        System.out.println("Total declarations: " + declarations.size());
        System.out.println("Ratio: " + ((float) conflictDeclarations.size()) / declarations.size());
    }
}
