#!/usr/bin/env bash

# runs the checker framework for a given type system for a given source directory, returning the error lines

if [ -z "$CHECKERFRAMEWORK" ]; then
    echo "CHECKER_FRAMEWORK environment variable must be set."
    exit
fi

shopt -s globstar
# assumes layout from njr-1
$CHECKERFRAMEWORK/checker/bin/javac \
    -cp "$2/lib" \
    -proc:only \
    -processor "$1" \
    $2/src/**/*.java \
    -Xmaxerrs 100000 2>&1 \
    | grep ": error: " \
    | cut -d":" -f1,2 \
    | tr ":" "\t"
