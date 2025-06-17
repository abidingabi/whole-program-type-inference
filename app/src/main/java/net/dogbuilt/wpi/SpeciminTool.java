/*
 modified copy of https://github.com/njit-jerse/ASHE_Automated-Software-Hardening-for-Entrypoints/blob/4b8db1add46c7ce0a8ea49309b7e5099fa4ff9cf/src/main/java/edu/njit/jerse/ashe/services/SpeciminTool.java,
 and is under the appropriate license.
 */

package net.dogbuilt.wpi;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A utility class to manage and run Specimin - a specification minimizer tool.
 *
 * <p>Specimin preserves a target method and all other specifications that are required to compile
 * it.
 *
 * <p>The SpeciminTool class interfaces with Specimin, providing functionalities to:
 *
 * <ul>
 *   <li>Run the Specimin tool with specified parameters.
 *   <li>Modify the package declaration of the minimized Java file.
 *   <li>Delete minimized directories if needed.
 * </ul>
 */
public final class SpeciminTool {
    /**
     * Private constructor to prevent instantiation.
     *
     * <p>This class is a utility class and is not meant to be instantiated. All methods are static
     * and can be accessed without creating an instance. Making the constructor private ensures that
     * this class cannot be instantiated from outside the class and helps to prevent misuse.
     */
    private SpeciminTool() {
        throw new AssertionError("Cannot instantiate SpeciminTool");
    }

    public enum SpeciminTargetType {
        METHOD,
        FIELD
    }

    /**
     * Executes and manages the Specimin tool using the specified paths and targets.
     *
     * @param root       The root directory for the tool.
     * @param targetFile File to be targeted by the tool.
     * @param target     field/method to be targeted by the tool.
     * @param type       whether we are targeting a field or method.
     * @param dst       where to store output
     * @throws IOException          If there's an error executing the command or writing the minimized file.
     * @throws InterruptedException If the process execution is interrupted.
     */
    public static void runSpeciminTool(Path root, Path classPath, Path targetFile, String target, SpeciminTargetType type, Path dst)
            throws IOException, InterruptedException {

        /* TODO: make this not hard-coded */
        String speciminPath = "/home/abi/school/kellogg-research/specimin";

        Path tempDir;
        try {
            tempDir = Files.createTempDirectory(dst, "specimin");
        } catch (IOException e) {
            String errorMessage = "Failed to create temporary directory";
            throw new IOException(errorMessage, e);
        }
        // Delete the temporary directory when the JVM exits.
        // This is a fail-safe in case Ashe#run fails to delete the temporary directory.
        tempDir.toFile().deleteOnExit();

        List<String> argsWithOption = formatSpeciminArgs(tempDir, classPath, root, targetFile, target, type);

        List<String> commands = prepareCommands(speciminPath, argsWithOption);

        startSpeciminProcess(commands, speciminPath);
    }

    /**
     * Formats the arguments for the Specimin tool.
     *
     * @param outputDirectory Directory for Specimin's output.
     * @param root            The root directory for the tool.
     * @param targetFile      File to be targeted by the tool.
     * @param target          Method/field to be targeted by the tool.
     * @param targetType      The type of target.
     * @return Formatted string of arguments.
     */
    private static List<String> formatSpeciminArgs(
            Path outputDirectory, Path classPath, Path root, Path targetFile, String target, SpeciminTargetType targetType) {
        return List.of(
                "--outputDirectory", outputDirectory.toString(),
                "--root", root.toString(),
                "--jarPath", classPath.toString(),
                "--targetFile", targetFile.toString(),
                targetType == SpeciminTargetType.METHOD ? "--targetMethod" : "--targetField", target
        );
    }

    /**
     * Prepares the commands to be executed by the Specimin tool.
     *
     * @param speciminPath   Path to the Specimin tool.
     * @param argsWithOption Formatted arguments string.
     * @return List of commands for execution.
     */
    private static List<String> prepareCommands(String speciminPath, List<String> argsWithOption) {
        List<String> commands = new ArrayList<>();
        /* TODO: you know why this is a problem (make the path configurable) */
        commands.add("/home/abi/.nix-profile/bin/java");
        commands.add("-jar");
        commands.add(speciminPath + "/build/libs/specimin.jar");
        commands.addAll(argsWithOption);

        return commands;
    }


    /**
     * // TODO: Specimin path should change to using a jar once we are ready Starts the Specimin
     * process with the given commands and path to the Specimin project.
     *
     * @param commands     List of commands to be executed.
     * @param speciminPath Path to the Specimin tool project. // TODO: This may be changed to a jar
     *                     once we are ready
     * @throws IOException          If there's an error executing the command or reading the output.
     * @throws InterruptedException If the process execution is interrupted.
     */
    private static void startSpeciminProcess(List<String> commands, String speciminPath)
            throws IOException, InterruptedException {
        System.out.println(commands.stream().collect(Collectors.joining(" ")));
        ProcessBuilder builder = new ProcessBuilder(commands);
        builder.redirectErrorStream(true);
        builder.directory(new File(speciminPath));

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            String errorMessage = "Failed to start the Specimin process";
            throw new IOException(errorMessage, e);
        }

        logProcessOutput(process);
        finalizeProcess(process);
    }

    /**
     * Logs the output from the Specimin process. If there is an and the model is not dryrun, the
     * logger will skip the output and provide a failure message. Else, the entirety of the output
     * will be logged.
     *
     * @param process The running Specimin process.
     * @throws IOException If there's an error reading the output.
     */
    private static void logProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Log the output if there's no exception or if running in dryrun mode.
                // Logging the exception in dryrun mode is useful for reporting bugs in Specimin.
                output.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            String errorMessage = "Failed to read output from Specimin process";
            throw new IOException(errorMessage, e);
        }

        // Prepend the output with a header
        output.insert(0, System.lineSeparator() + "Specimin output:" + System.lineSeparator());
        System.out.println(output);
    }

    /**
     * Finalizes the Specimin process by closing streams and destroying the process.
     *
     * @param process The running Specimin process.
     * @throws InterruptedException If the process execution is interrupted.
     * @throws IOException          If there's an error closing the streams.
     */
    private static void finalizeProcess(Process process) throws InterruptedException, IOException {
        try {
            int exitValue = process.waitFor();
            /* this seems to activate extraneously for some reason? */
            if (exitValue != 0) {
                String errorMessage = "Error executing the command. Exit value: " + exitValue + " command stderr:\n";
                throw new InterruptedException(errorMessage);
            }
        } finally {
            process.getErrorStream().close();
            process.getOutputStream().close();
            process.destroy();
        }
    }

    /**
     * Finds all Java files in the minimized directory and returns them as a list.
     *
     * @param minimizedDir the path to the minimized directory
     * @return the list of Java files in the directory
     * @throws IOException if there's an error reading the directory
     */
    private static List<File> findAllJavaFilesInMinimizedDir(Path minimizedDir) throws IOException {
        List<File> javaFiles;
        try (Stream<Path> paths = Files.walk(minimizedDir)) {
            javaFiles =
                    paths
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java"))
                            .map(Path::toFile)
                            .collect(Collectors.toList());
        }
        return javaFiles;
    }
}

