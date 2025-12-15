# Code Style Guide

This document contains general guidelines for writing code in the BSL Language Server project.

Try to stick to them and the code review process will be simple.

## Null values

If a method can legally return `null`, it is recommended that you return `Optional<T>` instead of explicitly returning `null`. Exceptions (eg. high frequency or performance functions) are negotiated separately.

The description of the `package-info.java` package must indicate that the NonNull API is used by default in the package.
To do this, the annotation `@NullMarked` is added above the package name

Example:
```java
// ...License...
@NullMarked
package com.github._1c_syntax.bsl.languageserver;

import org.jspecify.annotations.NullMarked;
```

To explicitly indicate that a method can accept or return `null`, use the annotation `@org.jspecify.annotations.Nullable`.

This avoids using the `@org.jspecify.annotations.NonNull` annotation.

The `null` control annotations from the `javax.annotations`, `jetbrains.annotations` or `edu.umd.cs.findbugs.annotations` packages are not allowed.

## Formatting

1. All code in the modules should be automatically formatted.
1. Indents - spaces
1. The indent size is 2 characters. Continuous indent are also two characters. IntelliJ IDEA sets indentation according to 2-2-2

## Class Members Location

1. The location of fields, methods, modifiers in general must comply with Oracle recommendations - [File organization](https://www.oracle.com/java/technologies/javase/codeconventions-fileorganization.html), and SonarSource - [Modifiers should be declared in the correct order](https://rules.sonarsource.com/java/tag/convention/RSPEC-1124)
1. Methods are _recommended_ to be placed in the following order:
    1. public object methods
    1. protected/package private object methods
    1. private object methods
    1. public class methods
    1. protected/package private class methods
    1. private class methods

## Static analysis

BSL Language Server code is automatically checked against coding standards on the SonarCloud service - [project link](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server).

The list of activated rules and their settings can be viewed in the quality profile [1c-syntax way](https://sonarcloud.io/organizations/1c-syntax/rules?activation=true&qprofile=AWdJBUnB2EsKsQgQiNpk).

Due to security restrictions, pull requests not from the repository `1c-syntax/bsl-language-server` are not checked for compliance. After accepting the pull request, it is recommended to go to [the project page on SonarCloud](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server) and view the list of comments filtered by author. Pull-request with fixes is welcome.

## Linter

To improve the quality of the code and preliminary check for compliance with standards, it is recommended to install plugin [SonarLint](https://www.sonarlint.org) in your IDE.

To automatically download and parameterize rules in accordance with the settings on SonarCloud, it may be necessary to "bind" the local project to the SonarCloud project. If your IDE cannot find a matching project on SonarCloud, contact the project maintainers to add you to the "Members" list on SonarCloud.

## Logging

Library `Slf4j` is used for logging. The use of output in `System.out` and `System.err` is allowed only in exceptional cases (for example, in cli command handlers).

To simplify the creation of a logger instance, it is recommended to use the `@lombok.extern.slf4j.Slf4j` class annotation.

## Documentation

1. Every new class must have javadoc. Exception - classes that implement interfaces `BSLDiagnostic` or `QuickFixSupplier`.
1. Every new package must have `package-info.java` with the javadoc of the package.
1. Every new public method in the `utils` package, helper classes, or classes that already have a javadoc must have a method javadoc.

## External dependencies

1. Connecting new libraries to the implementation scope should be done carefully, with control over the increase in the size of the resulting jar file. If possible, "unnecessary" and unused sub-dependencies should be excluded through `exclude`.
1. Explicit linking of the `com.google.guava`, `Google Collections` library or other parts of the Guava family of libraries is prohibited. **If absolutely necessary**, it is permissible to copy the implementation from `Guava` inside the BSL Language Server, subject to the terms of the Guava license. For everything else, there is Apache Commons.
1. Import of `*.google.*` classes, as well as other parts of Guava libraries, is prohibited. With no exceptions.
1. The `jsr305` package and its annotations should not be used in code. See section "Null values".

> In the process...
