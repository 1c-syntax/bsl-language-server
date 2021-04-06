# An example of creating a new diagnostic

Diagnostics is a class that analyzes the source code to identify a problem or error.

## Diagnostic contents (briefly)

To implement diagnostics, you need to create several files

* Diagnostic implementation class
* Two resource files (for English and Russian) containing the name of the diagnostic and the error message
* BSL fixture file used for diagnostic testing
* Diagnostic test class
* Two files (for English and Russian) with diagnostic documentation

A detailed description of the contents of the files and the rules of use can be found in [article](DiagnosticStructure.md).

## Create Diagnostic

Below is an example of creating diagnostics based on the already created one.

### Determining the purpose of diagnostic

Before implementing a new diagnostic, you need to define its purpose - what error (or deficiency) you need to detect. As an example, we will write a diagnostic that checks for the presence of a semicolon `;` at the end of each expression. The next step is to come up with a unique diagnostic key, under which it will be added to the general list of diagnostics. Let's take the name `SemicolonPresence`.

### Diagnostic implementation class

In the directory `src/main/java`  in the package `com.github._1c_syntax.bsl.languageserver.diagnostics`, create the `SemicolonPresenceDiagnostic.java` diagnostic class file. In the file, create the class of the same name, inherited from the `AbstractVisitorDiagnostic` class. The result will be

```java
package com.github._1c_syntax.bsl.languageserver.diagnostics;

public class SemicolonPresenceDiagnostic extends AbstractVisitorDiagnostic {
}
```

Each diagnostic must have a `@DiagnosticMetadata`, class annotation containing diagnostic metadata. ПDetails on annotations in \[article\]\[DiagnosticStructure\]. In the example, we implement diagnostics related to the quality of the code (`CODE_SMELL`), low priority (`MINOR`), which requires 1 minute to fix and related to the 1C standard. Итоговый вид класса с аннотацией

```java
package com.github._1c_syntax.bsl.languageserver.diagnostics;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tag = {
    DiagnosticTag.STANDARD
  }
)
public class SemicolonPresenceDiagnostic extends AbstractVisitorDiagnostic {
}
```

### Class resources

In the directory `src/main/resources`  in the package `com.github._1c_syntax.bsl.languageserver.diagnostics` create 2 diagnostic resource files. In our example, these will be files `SemicolonPresenceDiagnostic_ru.properties` and `SemicolonPresenceDiagnostic_en.properties`.

File `SemicolonPresenceDiagnostic_ru.properties`

```properties
diagnosticMessage=Пропущена точка с запятой в конце выражения
diagnosticName=Выражение должно заканчиваться ";"
```

File `SemicolonPresenceDiagnostic_en.properties`

```properties
diagnosticMessage=Missed semicolon at the end of statement
diagnosticName=Statement should end with ";"
```

### Test fixtures

For testing purposes, let's add a file to the project containing examples of both erroneous and correct code. We will place the `SemicolonPresenceDiagnostic.bsl` file with fixtures in the  `src/test/resources` resources directory in the  `diagnostics` package. As data for testing, let's add the code to the file

```bsl
A = 0;
If True Then
  A = 0;
  A = 0           // Diagnostics should work here
EndIf             // and here
```

**Attention!** It is necessary to mark with comments the places where the diagnostics should work.

### Test writing

package com.github._1c_syntax.bsl.languageserver.diagnostics; @DiagnosticMetadata( type = DiagnosticType.CODE_SMELL, severity = DiagnosticSeverity.MINOR, minutesToFix = 1, tag = { DiagnosticTag.STANDARD } ) public class SemicolonPresenceDiagnostic extends AbstractVisitorDiagnostic { @Override public ParseTree visitStatement(BSLParser.StatementContext ctx) {         if (ctx.SEMICOLON() == null) { diagnosticStorage.addDiagnostic(ctx); } return super.visitStatement(ctx); } }

```java
package com.github._1c_syntax.bsl.languageserver.diagnostics;

class SemicolonPresenceDiagnosticTest extends AbstractDiagnosticTest<SemicolonPresenceDiagnostic>{
    SemicolonPresenceDiagnosticTest() {
        super(SemicolonPresenceDiagnostic.class);
    }
}
```

Simplified basic test consists of the steps

* getting a list of diagnostics
* checking the number of items found
* checking the location of detected items

Without going into detail, remember that

* To get diagnostics for a fixture, use the `getDiagnostics()` method implemented in the abstract class `AbstractDiagnosticTest`. It returns a list of encountered errors of the current type.
* To check the number of errors, use the `hasSize`, assertion, the parameter of which will be the number of expected elements.
* To check for each error, it is necessary to compare the error range with the expected range.

The resulting test class

```java
class SemicolonPresenceDiagnosticTest extends AbstractDiagnosticTest<SemicolonPresenceDiagnostic> {

  SemicolonPresenceDiagnosticTest() {
    super(SemicolonPresenceDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(4, 0, 4, 9)
      .hasRange(3, 6, 3, 7);
  }
}
```

### Diagnostic algorithm implementation

According to the rules of the grammar of the language, described in the project [BSLParser](https://github.com/1c-syntax/bsl-parser/blob/master/src/main/antlr/BSLParser.g4), for our example, it is necessary to analyze nodes with the type `statement`. Need to use visitor `visitStatement`. Each selected node of the AST-tree must contain an ending "semicolon" represented by node `SEMICOLON`.

Thus, the check will be to find the `SEMICOLON`  token in each `statement` node. Если токен не будет найден, то необходимо зарегистрировать замечание.  
После реализации проверки, файл примет следующий вид

```java
package com.github._1c_syntax.bsl.languageserver.diagnostics;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tag = {
    DiagnosticTag.STANDARD
  }
)
public class SemicolonPresenceDiagnostic extends AbstractVisitorDiagnostic {
    @Override
    public ParseTree visitStatement(BSLParser.StatementContext ctx) { // выбранный визитер
        if (ctx.SEMICOLON() == null) {                                // получение дочернего узла SEMICOLON
            diagnosticStorage.addDiagnostic(ctx);                     // добавление замечания
        }
        // Для не-терминальных выражений в качестве возвращаемого значения
        // обязательно должен вызываться super-метод.
        return super.visitStatement(ctx);
    }
}
```

It is necessary to run a diagnostic test and make sure that it works correctly.

### Creating a diagnostic description

To describe the created diagnostics, create two files `SemicolonPresence.md`: in catalog`docs/diagnostics` in Russian, in catalog `docs/en/diagnostics` in English.

## Completion

After successfully testing the diagnostics, add it to the list of implemented diagnostics of the project by editing the files `docs/index.md` for the Russian and `docs/en/index.md` for the English. Updating the documentation index can be done automatically by running the updateDiagnosticsIndex gradle task (from the command line or IDE)`gradlew updateDiagnosticsIndex`. 
