# Diagnostics structure, files contents and purpose

This article contains rules of diagnostics usage, creation and information about content templates.

- [Diagnostics structure, files contents and purpose](#%d0%a1%d1%82%d1%80%d1%83%d0%ba%d1%82%d1%83%d1%80%d0%b0-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8-%d0%bd%d0%b0%d0%b7%d0%bd%d0%b0%d1%87%d0%b5%d0%bd%d0%b8%d0%b5-%d0%b8-%d1%81%d0%be%d0%b4%d0%b5%d1%80%d0%b6%d0%b8%d0%bc%d0%be%d0%b5-%d1%84%d0%b0%d0%b9%d0%bb%d0%be%d0%b2)
  - [Diagnostics structure](#%d0%a1%d0%be%d1%81%d1%82%d0%b0%d0%b2-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8)
  - [Diagnostics implementation class](#%d0%9a%d0%bb%d0%b0%d1%81%d1%81-%d1%80%d0%b5%d0%b0%d0%bb%d0%b8%d0%b7%d0%b0%d1%86%d0%b8%d0%b8-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8)
    - [Diagnostics class, implements BSLDiagnostic interface](#%d0%9a%d0%bb%d0%b0%d1%81%d1%81-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8-%d1%80%d0%b5%d0%b0%d0%bb%d0%b8%d0%b7%d1%83%d1%8e%d1%89%d0%b8%d0%b9-%d0%b8%d0%bd%d1%82%d0%b5%d1%80%d1%84%d0%b5%d0%b9%d1%81-bsldiagnostic)
    - [Diagnostics class, inherits from AbstractDiagnostic](#%d0%9a%d0%bb%d0%b0%d1%81%d1%81-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8-%d1%83%d0%bd%d0%b0%d1%81%d0%bb%d0%b5%d0%b4%d0%be%d0%b2%d0%b0%d0%bd%d0%bd%d1%8b%d0%b9-%d0%be%d1%82-abstractdiagnostic)
    - [Diagnostics class, inherits from AbstractVisitorDiagnostic](#%d0%9a%d0%bb%d0%b0%d1%81%d1%81-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8-%d1%83%d0%bd%d0%b0%d1%81%d0%bb%d0%b5%d0%b4%d0%be%d0%b2%d0%b0%d0%bd%d0%bd%d1%8b%d0%b9-%d0%be%d1%82-abstractvisitordiagnostic)
    - [Diagnostics class, inherits from AbstractListenerDiagnostic **(Work in Progress)**](#%d0%9a%d0%bb%d0%b0%d1%81%d1%81-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8-%d1%83%d0%bd%d0%b0%d1%81%d0%bb%d0%b5%d0%b4%d0%be%d0%b2%d0%b0%d0%bd%d0%bd%d1%8b%d0%b9-%d0%be%d1%82-abstractlistenerdiagnostic-%d0%92-%d0%a0%d0%90%d0%97%d0%a0%d0%90%d0%91%d0%9e%d0%a2%d0%9a%d0%95)
  - [Diagnostics test class](#%d0%9a%d0%bb%d0%b0%d1%81%d1%81-%d1%82%d0%b5%d1%81%d1%82%d0%b0-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8)
    - [Diagnostics test](#%d0%a2%d0%b5%d1%81%d1%82-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8)
    - [Test of configuration method for parameterized diagnostics](#%d0%a2%d0%b5%d1%81%d1%82-%d0%bc%d0%b5%d1%82%d0%be%d0%b4%d0%b0-%d0%ba%d0%be%d0%bd%d1%84%d0%b8%d0%b3%d1%83%d1%80%d0%b8%d1%80%d0%be%d0%b2%d0%b0%d0%bd%d0%b8%d1%8f-%d0%b4%d0%bb%d1%8f-%d0%bf%d0%b0%d1%80%d0%b0%d0%bc%d0%b5%d1%82%d1%80%d0%b8%d0%b7%d0%be%d0%b2%d0%b0%d0%bd%d0%bd%d1%8b%d1%85-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba)
    - [Quick fixes test **(Work in progress)**](#%d0%a2%d0%b5%d1%81%d1%82-%22%d0%b1%d1%8b%d1%81%d1%82%d1%80%d1%8b%d1%85-%d0%b7%d0%b0%d0%bc%d0%b5%d0%bd%22-%d0%92-%d0%a0%d0%90%d0%97%d0%a0%d0%90%d0%91%d0%9e%d0%a2%d0%9a%d0%95)
  - [Diagnostics resources](#%d0%a0%d0%b5%d1%81%d1%83%d1%80%d1%81%d1%8b-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8)
  - [Diagnostics test resources](#%d0%a0%d0%b5%d1%81%d1%83%d1%80%d1%81%d1%8b-%d1%82%d0%b5%d1%81%d1%82%d0%b0-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8)
  - [Diagnostics description](#%d0%9e%d0%bf%d0%b8%d1%81%d0%b0%d0%bd%d0%b8%d0%b5-%d0%b4%d0%b8%d0%b0%d0%b3%d0%bd%d0%be%d1%81%d1%82%d0%b8%d0%ba%d0%b8)

## Diagnostics structure

Diagnostics consists of a set of files, which are described in detail in the sections below.  
The required set of files as part of the diagnostics at the time of this writing and the rules for their naming

- Diagnostic implementation class.                 The file name is formed according to the principle `% Diagnostic Key%` + `Diagnosctic.java`
- Diagnostics test class.                      The file name is generated according to the principle `%DiagnosticKey%` + `DiagnoscticTest.java`
- Diagnostic resource file in Russian.    The file name is formed according to the principle `%DiagnosticKey%` + `Diagnosctic_en.properties`
- Diagnostic resource file in English. The file name is formed according to the principle `%DiagnosticKey%` + `Diagnosctic_en.properties`
- Resource file (fixture) test.                The file name is formed according to the principle `%DiagnosticKey%` + `Diagnosctic.bsl`
- Diagnostic description file in Russian.   The file name is formed according to the principle `%DiagnosticKey%` + `.md`
- Diagnostic resource file in English. The file name is formed according to the principle `%DiagnosticKey%` + `.md`

**Note:**  
To create necessary files in right places, should run command `gradlew newDiagnostic --key="KeyDiagnostic"`, where `KeyDiagnostic` should be replaced with your own diagnostics key. Details in help `gradlew -q help --task newDiagnostic`.

## Diagnostics implementation class

Diagnostics is implemented by adding a java class to the `com.github._1c_syntax.bsl.languageserver.diagnostics` package in the `src/main/java` directory.

In the body of the file, you need to specify the package to which the class and the import block are added _(when using ide, the import list is updated automatically)_. It is necessary to ensure that **only** is imported that is necessary for implementation, everything unused should be **removed** _(if [settings](EnvironmentSetting.md) are correct, then ide will do everything automatically)_.

Each diagnostic must have a `@DiagnosticMetadata`, class annotation containing diagnostic metadata. The actual content can always be obtained by examining the [file](https://github.com/1c-syntax/bsl-language-server/blob/develop/src/main/java/com/github/_1c_syntax/bsl/languageserver/diagnostics/metadata/DiagnosticMetadata.java).

At the time of this writing, the following properties are available:

- The type of diagnostics is `type` and its importance is `severity`, for each diagnostics it is necessary to define them. In order to choose the correct type and importance of diagnostics, you can refer to [article](DiagnosticTypeAndSeverity.md).
- Time to fix issue `minutesToFix` (default 0). This value is used when calculating the total technical debt of the project in labor costs to correct all comments (the sum of time to correct for all detected comments). It is worth indicating the time, as realistic as possible, that the developer should spend on fixing.
- Using the `extraMinForComplexity` parameter, you can dynamically increase the time to correct a comment for diagnostics that take into account several places that violate the rule, for example, when calculating the complexity of a method.
- A set of diagnostics tags `tag` that indicate the group to which it belongs. Read more about tags in the [article](DiagnosticTag.md).
- Applicability limit `scope` (by default `ALL`, i.e. no limit). BSL LS supports multiple languages (oscript and bsl) and diagnostics can be applied to one specific language or to all at once.
- Default diagnostic active `activatedByDefault` (default `True`). When developing experimental, controversial, or not applicable in most projects, it is worth turning off diagnostics by default, the activation will be performed by the end user of the solution.
- Compatibility mode `compatibilityMode`, by which diagnostics are filtered when using metadata. The default is `UNDEFINED`.
- List of module types `modules` for the ability to limit the area analyzed by diagnostics
- Sign of the ability to set issues on the entire project `canLocateOnProject`. Used for diagnostics not related to the source code module. At the moment, the option is accepted only by SonarQube, other tools ignore it.
- LSP severity level `lspSeverity` (by default empty string). Allows explicit control over the LSP severity level for the diagnostic. When set, this value takes priority over the calculated LSP severity. Supported values: `Error`, `Warning`, `Information`, `Hint`. This parameter can also be overridden in the configuration file.
The last two can be omitted.

Annotation example

```java
@DiagnosticMetadata(
type = DiagnosticType.CODE_SMELL, 
severity = DiagnosticSeverity.MINOR, 
minutesToFix = 1, 
activatedByDefault = false, 
scope = DiagnosticScope.BSL,
compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_3, 
tags = {
DiagnosticTag.STANDARD 
},
modules = {
ModuleType.CommonModule 
},
canLocateOnProject = false, 
extraMinForComplexity = 1, // For each additional note position (`DiagnosticRelatedInformation`) one minute will be added
lspSeverity = "Warning" // Explicit LSP severity level (Error, Warning, Information, Hint)
)

```

Class should implement the interface `BSLDiagnostic`. If diagnostic bases on AST, that class should extends at one of classes, that implement `BSLDiagnostic` below:

- for simple diagnostics (module context checking) it is worth using inheritance `AbstractVisitor` with the implementation of a single `check` method
- if you need to analyze a visit to a node / sequence of nodes, use the `listener` strategy, you need to inherit the class from `AbstractListenerDiagnostic`
- in other cases, you need to use the strategy `visitor` and
  - `AbstractVisitorDiagnostic` for diagnostics of 1C code
  - `AbstractSDBLVisitorDiagnostic` for diagnostics of 1C query

Examples

```java
public class TemplateDiagnostic implements BSLDiagnostic
```

```java
public class TemplateDiagnostic extends AbstractDiagnostic
```

```java
public class TemplateDiagnostic extends AbstractVisitorDiagnostic
```

```java
public class TemplateDiagnostic extends AbstractListenerDiagnostic
```

```java
public class TemplateDiagnostic extends AbstractSDBLVisitorDiagnostic
```

```java
public class TemplateDiagnostic extends AbstractSDBLListenerDiagnostic
```

Diagnostic may provide so-called `quick fixes`. In order to provide quick fixes the diagnostic class must implement `QuickFixProvider` interface. See this [article](DiagnosticQuickFix.md) on adding a `quick fix` to diagnostic.

Examples

```java
public class TemplateDiagnostic implements BSLDiagnostic, QuickFixProvider
```

```java
public class TemplateDiagnostic extends AbstractDiagnostic implements QuickFixProvider
```

```java
public class TemplateDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider
```

```java
public class TemplateDiagnostic extends AbstractListenerDiagnostic implements QuickFixProvider
```

```java
public class TemplateDiagnostic extends AbstractSDBLVisitorDiagnostic implements QuickFixProvider
```

```java
public class TemplateDiagnostic extends AbstractSDBLListenerDiagnostic implements QuickFixProvider
```

After the declaration of the class, a block with their parameters is located for parameterizable diagnostics. For details on the diagnostic parameters, see the [article](DiagnostcAddSettings.md).

Below are the differences in the implementation of diagnostic classes.

### Diagnostics class, implements BSLDiagnostic interface

In the class, you need to define a private field `diagnosticStorage` of type `DiagnosticStorage` to store detected diagnostics, and a private property `info` of type `DiagnosticInfo`, for access to diagnostic data.

```java
 private DiagnosticStorage diagnosticStorage = new DiagnosticStorage(this);
private final DiagnosticInfo info;
```

In the class, you need to implement:

- method `getDiagnostics` accepting the context of the file being analyzed and returning a list of detected diagnostics `List<Diagnostic>`
- getter `getInfo`
- setter `setInfo`

Method structure `getDiagnostics`

```java
  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    // Clearing diagnostics storage
    diagnosticStorage.clearDiagnostics();

    documentContext.getComments()  // Getting the collection of tokens, here comments
      .parallelStream()
      .filter((Token t) ->         // Search for "necessary" - those that the diagnostics is aimed at detecting
        !goodCommentPattern.matcher(t.getText()).matches())
      .sequential()
      .forEach((Token t) ->        // Adding errors, here for each token a separate error
        diagnosticStorage.addDiagnostic(t));

    // Return of found
    return diagnosticStorage.getDiagnostics();
  }
```

### Diagnostics class, inherits from AbstractDiagnostic

For simple diagnostics, you should inherit your class from the AbstractDiagnostic class. In the diagnostic class, you need to implement the `check` method. The method should analyze the context of the document and, if noted, add diagnostics to `diagnosticStorage`.

Example:

```java

  @Override
  protected void check() {
    documentContext.getTokensFromDefaultChannel()
      .parallelStream()
      .filter((Token t) ->
        t.getType() == BSLParser.IDENTIFIER &&
          t.getText().toUpperCase(Locale.ENGLISH).contains("Ё"))
      .forEach(token -> diagnosticStorage.addDiagnostic(token));
  }
```

### Diagnostics class, inherits from AbstractVisitorDiagnostic

In the diagnostic class, it is necessary to implement the methods of all necessary `AST visitors`, in accordance with the language grammar described in the [ BSLParser ](https://github.com/1c-syntax/bsl-parser/blob/master/src/main/antlr/BSLParser.g4) project.  A complete list of existing visitor methods can be found in the `BSLParserBaseVisitor` class. Please note: for simplicity, `generalized` visitors have been created, for example, instead of two `visitFunction` for a function and `visitProcedure` for a procedure, you can use `visitSub`.

As a parameter, an AST node of the corresponding type is passed to each method of the visitor. In the body of the method, it is necessary to analyze the node and / or its child nodes and decide if there is an error. When a problem is found, it must be added to `diagnosticStorage` _(the field is already defined in the abstract class)_. An error note can be attached to the passed node or to its child or parent nodes, to the desired block of code.

Method structure

```java
  @Override
  public ParseTree visitModuleVar(BSLParser.ModuleVarContext ctx) {                 // Visitor for module variables
    if(Trees.findAllRuleNodes(ctx, BSLParser.RULE_compilerDirective).size() > 1) {  // Finding child nodes
      diagnosticStorage.addDiagnostic(ctx);                                         // Adding a error to the entire site
    }
    return ctx;
  }
```

If the diagnostics **does not provide** analysis of nested nodes, then it must return the passed input node, otherwise it is necessary to call the `super-method` of the same name.   
This rule will save application resources without making a meaningless call.

Examples:

- Diagnostics for a method or file must immediately return a value, because nested methods/files do not exist
- Diagnostics for a condition or region block must call the `super-method`, as they exist and are used (e.g. `return super.visitSub(ctx)` for methods)

### Diagnostics class, inherits from AbstractSDBLVisitorDiagnostic

The diagnostic class implements the necessary `AST visitors`, according to the grammar of the query language (see [BSLParser](https://github.com/1c-syntax/bsl-parser/blob/master/src/main/antlr/SDBLParser.g4)). The complete list of visitor methods is in the `SDBLParserBaseVisitor` class.

The rest of the rules are identical to `AbstractVisitorDiagnostic`.

### Diagnostics class, inherits from AbstractListenerDiagnostic **(Work in Progress)**

_**<Work in Progress>**_

## Diagnostics test class

The tests use the [JUnit5 framework](https://junit.org/junit5/) and the [AssertJ assertion library](https://joel-costigliola.github.io/assertj/) providing a [fluent interface](https://ru.wikipedia.org/wiki/Fluent_interface) "expectations", like the familiar [asserts](https://github.com/oscript-library/asserts) library for [OneScript](http://oscript.io/).

A test is a java class added to the `com.github._1c_syntax.bsl.languageserver.diagnostics` package in the `src/test/java` directory.

In the file, you need to specify the package in which the class and the import block _(similar to the diagnostic implementation class)_ are added, then you need to create a class of the same name to the file, inherited from the `AbstractDiagnosticTest` class for the created diagnostic class.

Test class example

```java
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateDiagnosticTest extends AbstractDiagnosticTest<TemplateDiagnostic> {

    TemplateDiagnosticTest() {
        super(TemplateDiagnostic.class);
    }
}
```

To add a new test to the created class, you need to add a method with the `@Test` annotation.

The test class must contain methods for testing.

- diagnostic test itself
- test of configuration method for parameterized diagnostics
- test "quick fixes" if available

### Diagnostics test

Simplified, the diagnostic test contains the steps

- getting a list of diagnostics
- checking the number of items found
- checking the location of detected items

The first step is to get the list of notes by calling the `getDiagnostics()` method _(implemented in the `AbstractDiagnosticTest` class)_. Calling this method will parse the diagnostics resource file and return a list of remarks in it.
The next step is to use the `hasSize()` statement to make sure that the number of diagnostics is fixed as much as allowed in the fixtures.
After that, you need to make sure that the diagnostics are detected correctly. To do this, you need to compare the diagnostic area obtained by the `getRange()` method with the expected area _(you should use the `RangeHelper` class to simplify the formation of control values)_.
If the text of the error is templated, then it is necessary to check it in the test by getting the text of the error message using the `getMessage()` method of diagnostics.

Test method example

```java
    @Test
    void test() {
      List<Diagnostic> diagnostics = getDiagnostics();   // getting a list of diagnostics

      assertThat(diagnostics).hasSize(2);                // checking the number of errors found

      // verification of special cases
      assertThat(diagnostics)
        .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(27, 4, 27, 29)))
        .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(40, 4, 40, 29)));
    }
```

To reduce the amount of test code, you can use the `util.Assertions.assertThat` helper, then the example above will look like this:

```java
    @Test
    void test() {
      List<Diagnostic> diagnostics = getDiagnostics();   // getting a list of diagnostics

      assertThat(diagnostics).hasSize(2);                // checking the number of errors found

      // verification of special cases
      assertThat(diagnostics, true)
        .hasRange(27, 4, 27, 29)
        .hasRange(40, 4, 40, 29);
    }
```

### Test of configuration method for parameterized diagnostics

Tests for the configuration method should cover all possible settings and their combinations. The test has almost the same structure as the diagnostic test.
Before setting new values ​​for diagnostic parameters, you must get the default diagnostic settings using the `getDefaultDiagnosticConfiguration()` method using the information of the current diagnostic object `diagnosticInstance.getInfo()`. The result is a map in which the `put` method needs to change the values ​​of the required parameters. To apply the changed settings, you need to call the `configure()` method of the current diagnostic object `diagnosticInstance`.

Test method example

```java
    @Test
    void testConfigure() {
        // getting default diagnostic settings
        Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultDiagnosticConfiguration();

        configuration.put("templateParem", "newValue");     // setting "templateParem" to "newValue"
        diagnosticInstance.configure(configuration);        // applying settings

        List<Diagnostic> diagnostics = getDiagnostics();    // getting a list of diagnostics

        assertThat(diagnostics).hasSize(2);                 // checking the number of detected

        // special case check
        assertThat(diagnostics, true)
          .hasRange(27, 4, 27, 29)
          .hasRange(40, 4, 40, 29);
    }
```

### Quick fixes test **(Work in progress)**

_**<Work in Progress>**_

## Diagnostics resources

BSL LS supports two languages ​​in diagnostics: Russian and English, so the diagnostics includes two resource files located in the `src/main/resources` directory in the `com.github._1c_syntax.bsl.languageserver.diagnostics` package, one for each language. The file structure is the same: it is a text file in UTF-8 encoding, each line of which contains a "Key=Value" pair.

Required parameters used when adding diagnostics using the `diagnosticStorage.addDiagnostic` method

- diagnosticMessage - diagnostic message. Value supports parameterization (see `String.format`)
- diagnosticName - Diagnostic name, human-readable

For `quick fixes`, the `quickFixMessage` parameter is used, which contains a description of the fix action.

## Diagnostics test resources

The fixtures are the contents of the test resource file located in the `src/test/resources` directory in the `diagnostics` package. The file must contain the necessary code examples in 1C language _(or oscript language)_. 

It is necessary to add both erroneous and correct code, **marking the places of errors with comments**. It is best if the test cases are `real`, from practice, and not synthetic, invented `for diagnostics`.

## Description

The diagnostic description is created in the [Markdown](https://ru.wikipedia.org/wiki/Markdown) format in two versions - for Russian and English. The files are located in the `docs/diagnostics` directory for Russian, for English in `docs/en/diagnostics`. 
The file has the structure

- Header equal to the value of `diagnosticName` from the corresponding language's diagnostic resource file
- A block with a description of the diagnostics, indicating "why it is so bad"
- List of exceptions that diagnostics do not detect
- Examples of good and bad code
- The diagnostic algorithm, if it is not obvious
- If diagnostics is an implementation of the standard, then links to sources (for example, to [ITS](https://its.1c.ru)).
