package net.dogbuilt.wpi;

import com.github.javaparser.ast.CompilationUnit;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class App {
    public String getGreeting() {
        return "Hello World!";
    }

    private static List<Warning> getWarnings(Path getErrorLinesPath, String checker, Path path) {

        String[] errorLines = {getErrorLinesPath.toString(), checker, path.toString()};
        ProcessBuilder errorLinesPB = new ProcessBuilder(errorLines);

        var warnings = new ArrayList<Warning>();

        try {
            Process process = errorLinesPB.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                var sections = line.split("\t");
                if (sections.length == 2) {
                    Warning w = new Warning(Path.of(sections[0]), Integer.parseInt(sections[1]));
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

    private static void specimin(Path javaPath, Path speciminPath, Path getErrorLinesPath, String checker, Path projectDirectory, Path dst) {
        var src = projectDirectory.resolve("src/");

        /* to check the warnings, we care about the lib directory; hence us not using src */
        var warnings = getWarnings(getErrorLinesPath, checker, projectDirectory);
        System.out.println(warnings.size());

        // locations to run specimin on
        // the duplication between methods and fields here is ugly; we can do better later if needed
        // TODO: we should also deduplicate methods/fields, so we do not run specimin in the same spot twice
        var methods = warnings
                .stream()
                .map(w -> {
                    try {
                        var method = w.getEnclosingMethod();
                        if (method == null)
                            return null;
                        return new AbstractMap.SimpleEntry<>(method, w);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        AbstractMap.SimpleEntry::getKey,
                        AbstractMap.SimpleEntry::getValue,
                        /* since specimin is fairly coarse, we can ignore multiple warnings in the same method */
                        (a, b) -> a));

        var fields = warnings
                .stream()
                .map(w -> {
                    try {
                        var field = w.getEnclosingField();
                        if (field == null)
                            return null;
                        return new AbstractMap.SimpleEntry<>(field, w);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        AbstractMap.SimpleEntry::getKey,
                        AbstractMap.SimpleEntry::getValue,
                        /* since specimin is fairly coarse, we can ignore multiple warnings in the same field */
                        (a, b) -> a));

        /* TODO: deal with warnings not at specimin-able locations */
        List<@Nullable String> speciminMethodOutDirs = methods.keySet().stream().map(method -> {
            var warning = methods.get(method);
            // TODO: this is duplicated in specimin tool. this is bad
            var parameterTypes = method
                    .getParameters()
                    .stream()
                    .map(p -> p.getTypeAsString() + (p.isVarArgs() ? "..." : ""))
                    .collect(Collectors.joining(", "));

            String fullyQualifiedClassName;
            try {
                fullyQualifiedClassName = warning.getFullyQualifiedClassName();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            if (fullyQualifiedClassName == null) {
                System.out.println(warning);
                System.out.println("Could not find FQCN?");
                return null;
            }

            var target = fullyQualifiedClassName + "#" + method.getName() + "(" + parameterTypes + ")";

            System.out.println(target);
            try {
                return SpeciminTool.runSpeciminTool(
                        javaPath,
                        speciminPath,
                        src.toString(),
                        projectDirectory.resolve("lib/").toString(),
                        // TODO: this is really janky and breaks when the argument has a trailing slash.
                        warning.file().relativize(src).toString(),
                        target,
                        SpeciminTool.SpeciminTargetType.METHOD);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).toList();

        List<String> speciminFieldOutDirs = fields.keySet().stream().map(field -> {
            var warning = fields.get(field);

            String fullyQualifiedClassName;
            try {
                fullyQualifiedClassName = warning.getFullyQualifiedClassName();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            if (fullyQualifiedClassName == null) {
                System.out.println(warning);
                System.out.println("Could not find FQCN?");
                return null;
            }

            /* TODO: we make an assumption here that the warning is on the initializer for the first variable in a
             *  declaration. Unfortunately, with just the line number from javac, this is nontrivial to do correctly.
             */
            var target = fullyQualifiedClassName + "#" + field.getVariable(0).getName();

            System.out.println(target);
            try {
                return SpeciminTool.runSpeciminTool(
                        javaPath,
                        speciminPath,
                        src.toString(),
                        projectDirectory.resolve("lib/").toString(),
                        // TODO: this is really janky and breaks when the argument has a trailing slash.
                        warning.file().relativize(src).toString(),
                        target,
                        SpeciminTool.SpeciminTargetType.FIELD
                        );
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void localAnnotate(Path getErrorLinesPath,
                                      String checker, Path speciminOutputsDir, Path dst) throws IOException {
        /* attempt to run a search algorithm on each Specimin output */
        /* TODO: this should be embarrassingly parallel, at least per Specimin output; make it do that if slow */
        for (var speciminOutDir : Files.list(speciminOutputsDir).toList()) {
            var compilationUnits = AnnotatableLocationHelper.getCompilationUnits(speciminOutDir);
            var annotatableLocationCount = AnnotatableLocationHelper.getLocations(compilationUnits).size();

            var srcDir = speciminOutDir;
            /* TODO: Nullable should not be hard-coded */
            SearchAlgorithm searchAlgorithm = new AnnotateOneLocation(annotatableLocationCount, "Nullable");

            /*
             * get the specimin output into src so we can use getWarning on it. this is kind of a hack;
             * ideally this is not necessary
             */
            var temp = Files.createTempDirectory(dst, "specimin-moving-to-src");
            Files.createSymbolicLink(temp.resolve("src"), speciminOutDir);
            var originalWarningCount = getWarnings(getErrorLinesPath, checker, temp).size();

            System.out.println("annotatable locations: " + annotatableLocationCount);

            while (!searchAlgorithm.exhausted()) {
                var cus = compilationUnits.stream().map(CompilationUnit::clone).toList();
                var locations = AnnotatableLocationHelper.getLocations(cus);
                searchAlgorithm.annotate(locations);

                Path tempDir = Files.createTempDirectory(dst, "specimin-annotated");
                System.out.println(tempDir);

                for (CompilationUnit cu : cus) {
                    /* TODO: we don't need to add this to every file, just any one we have changed */
                    /* TODO: do not hardcode the annotation */
                    cu.addImport("org.checkerframework.checker.nullness.qual.Nullable");
                    /* emit modified source code. we emit it under src so getWarnings works */
                    var newPath = tempDir
                            .resolve("src")
                            .resolve(speciminOutDir.relativize(cu.getStorage().get().getPath()));
                    /* this ensures we create any necessary parent directories */
                    Files.createDirectories(newPath.getParent());
                    Files.write(newPath, cu.toString().getBytes());
                }

                /*
                 * NOTE: if the number of warnings is zero, we can probably? stop early.
                 * we might want to keep track of all ideal situations for merging later though
                 */
                System.out.println(speciminOutDir
                        + " -> " + tempDir
                        + " warning count: original: " + originalWarningCount
                        + " new: " + getWarnings(getErrorLinesPath, checker, tempDir).size());
            }
        }
    }

    private static Path readEnvironmentVariable(String name) {
        var pathString = System.getenv(name);
        if (pathString == null) {
            throw new RuntimeException(name + " environment variable must be set.");
        }
        return Path.of(pathString);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Subcommand must be specified");
            return;
        }

        var javaHome = readEnvironmentVariable("JAVA_HOME");
        var javaPath = javaHome.resolve("bin/java");
        var speciminPath = readEnvironmentVariable("SPECIMIN");
        var getErrorLinesPath = Path.of("../../get-error-lines.sh");

        if (args[0].equals("specimin")) {
            if (args.length == 4) {
                specimin(javaPath, speciminPath, getErrorLinesPath, args[1], Path.of(args[2]), Path.of(args[3]));
            } else {
                System.out.println("three arguments expected for specimin subcommand: checker, project directory, out directory");
            }
        }

        if (args[0].equals("localannotate")) {
            if (args.length == 4) {
                localAnnotate(speciminPath, args[1], Path.of(args[2]), Path.of(args[3]));
            } else {
                System.out.println("three arguments expected for localannotate subcommand: checker, directory containing specimin outputs");
            }
        }
    }
}
