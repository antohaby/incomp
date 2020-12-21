# Incremental Java Compilation Tool

## Quick start

Compile & Install: 
``./gradlew installDist``

Usage:
``./build/install/incomp/bin/incomp [javaSourceDir] [targetDir] [classPath]``

Or use ``./gradlew run --args"...""``

Example: ``./build/install/incomp/bin/incomp test-project/src/ test-project/build/ test-project/libs/*``
