#!/usr/bin/env bash

# runs the checker framework for a given type system for a given source directory, returning the error lines
# TODO: distinguish between javac and checker framework errors; bail on javac errors

shopt -s globstar
# assumes layout from njr-1
../../checker-framework-3.49.1/checker/bin/javac \
    -cp "$2/lib" \
    -proc:only \
    -processor "$1" \
    $2/src/**/*.java \
    -Xmaxerrs 100000 2>&1 \
    | grep ": error: " \
    | cut -d":" -f1,2 \
    | tr ":" "\t"
