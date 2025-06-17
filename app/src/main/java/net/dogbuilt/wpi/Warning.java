package net.dogbuilt.wpi;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.Optional;

public record Warning(Path file, int line) {
    /* TODO: this is almost certainly the wrong place for this */
    Optional<MethodDeclaration> getEnclosingMethod() throws FileNotFoundException {
        return StaticJavaParser.parse(file.toFile())
                .findAll(MethodDeclaration.class)
                .stream()
                .filter(declaration -> declaration.getBegin().map(b -> b.line <= line).orElse(false))
                .filter(declaration -> declaration.getEnd().map(e -> e.line >= line).orElse(false))
                .findAny();
    }

    Optional<FieldDeclaration> getEnclosingField() throws FileNotFoundException {
        return StaticJavaParser.parse(file.toFile()).
                findAll(FieldDeclaration.class)
                .stream()
                .filter(declaration -> declaration.getBegin().map(b -> b.line <= line).orElse(false))
                .filter(declaration -> declaration.getEnd().map(e -> e.line >= line).orElse(false))
                .findAny();
    }

    /* TODO: this is needlessly slow.... */
    Optional<String> getFullyQualifiedClassName() throws FileNotFoundException {
        var packageDeclaration = StaticJavaParser.parse(file.toFile()).getPackageDeclaration();
        if (packageDeclaration.isEmpty())
            return Optional.empty();

        var enclosingMethod = getEnclosingMethod();
        Node enclosing;
        if (enclosingMethod.isPresent()) {
            enclosing = enclosingMethod.get();
        } else {
            var enclosingField = getEnclosingField();
            if (enclosingField.isPresent()) {
                enclosing = enclosingField.get();
            } else {
                return Optional.empty();
            }
        }

        // TODO: this isn't correct; consider the case of an enum inside of a class
        var enclosingClass = enclosing.findAncestor(ClassOrInterfaceDeclaration.class);
        var enclosingEnum = enclosing.findAncestor(EnumDeclaration.class);

        return enclosingClass.flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName).or(
                () -> enclosingEnum.flatMap(EnumDeclaration::getFullyQualifiedName));
    }
}
