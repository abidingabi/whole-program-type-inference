package net.dogbuilt.wpi;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.File;
import java.io.FileNotFoundException;

public record Warning(String file, int line) {
    /* TODO: this is almost certainly the wrong place for this */
    @Nullable MethodDeclaration getEnclosingMethod() throws FileNotFoundException {
        return StaticJavaParser.parse(new File(file))
                .findAll(MethodDeclaration.class)
                .stream()
                .filter(declaration -> declaration.getBegin().map(b -> b.line <= line).orElse(false))
                .filter(declaration -> declaration.getEnd().map(e -> e.line >= line).orElse(false))
                .findAny()
                .orElse(null);
    }

    @Nullable FieldDeclaration getEnclosingField() throws FileNotFoundException {
        return StaticJavaParser.parse(new File(file)).
                findAll(FieldDeclaration.class)
                .stream()
                .filter(declaration -> declaration.getBegin().map(b -> b.line <= line).orElse(false))
                .filter(declaration -> declaration.getEnd().map(e -> e.line >= line).orElse(false))
                .findAny()
                .orElse(null);
    }

    /* TODO: this is needlessly slow.... */
    @Nullable String getFullyQualifiedClassName() throws FileNotFoundException {
        var packageDeclaration = StaticJavaParser.parse(new File(file)).getPackageDeclaration();
        if (packageDeclaration.isEmpty())
            return null;

        var enclosingMethod = getEnclosingMethod();
        Node enclosing;
        if (enclosingMethod != null) {
            enclosing = enclosingMethod;
        } else {
            var enclosingField = getEnclosingField();
            if (enclosingField != null) {
                enclosing = enclosingField;
            } else {
                return null;
            }
        }

        // TODO: this isn't correct; consider the case of an enum inside of a class
        var enclosingClass = enclosing.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        var enclosingEnum = enclosing.findAncestor(EnumDeclaration.class).orElse(null);

        if (enclosingClass != null) {
            return enclosingClass.getFullyQualifiedName().orElse(null);
        }
        if (enclosingEnum != null) {
            return enclosingEnum.getFullyQualifiedName().orElse(null);
        }
        return null;
    }
}
