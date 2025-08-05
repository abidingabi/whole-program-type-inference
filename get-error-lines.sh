#!/usr/bin/env bash

# runs the checker framework for a given type system for a given source directory, returning the error lines

if [ -z "$CHECKER_FRAMEWORK_JAVAC_PATH" ]; then
    echo "CHECKER_FRAMEWORK_JAVAC_PATH environment variable must be set."
    exit
fi

shopt -s globstar
# assumes layout from njr-1
$CHECKER_FRAMEWORK_JAVAC_PATH \
    -cp "$2/lib" \
    -proc:only \
    -processor "$1" \
    $2/src/**/*.java \
    -Xmaxerrs 100000 2>&1 \
    | grep ": error: " \
    | cut -d":" -f1,2 \
    | tr ":" "\t"
