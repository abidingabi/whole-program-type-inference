# Whole Program Type Inference

We have subcommands:

./gradlew run specimin checker project specimin-out,
which runs specimin on every warning for a given checker on project and stores 
the results in specimin-out. We assume project is formatted like an NJR-1
project.


./gradlew localannotate checker specimin-out destination,
which attempts to locally annotate each specimin output to minimize warnings.
