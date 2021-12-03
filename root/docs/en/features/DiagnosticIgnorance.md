# Escaping Code from Diagnostic

The static analyzer detects problems, errors, and flaws in the code in accordance with the rules (diagnostics) embedded in it. As elsewhere, there may be situations in the solution code where you need to deviate from the rules. These situations can occur for various reasons: both due to the architectural features of the solution, and as a result of the inability (for various reasons) to change the code to meet the requirements.

Instead of manually mark a comment as irrelevant every time, BSL LS provides functionality that allows you to hide or screen individual sections of code from triggering diagnostics.

## Description

To hide part of the code from the BSL LS analyzer, you must add a special comment to the code.   
The escaping comment is formed as follows: `[Prefix][:DiagnosticKey]-[ActivationFlag]`. Now in more detail.

- `Prefix` always is `// BSLLS`
- `DiagnosticKey` can be found in the [list of diagnostics](../diagnostics/index.md) by description
- `ActivationFlag` string parameter if diagnostic is On or Off. Supported Russian (`вкл` and `выкл`) and English (`on` and `off`).

To disable **ALL** diagnostics for part of the code, you must omit the diagnostic key.

## Examples and use cases

### Disable all diagnostics in the module

To disable all diagnostics in the module, i.e. essentially hide the module from the BSL LS analyzer, you need to insert a comment `// BSLLS-off` (or `// BSLLS-выкл`) at the beginning of the module

### Disable specific diagnostics in the module

For disable specific diagnostics in the module (for example, the cognitive complexity `CognitiveComplexity` and size limit of the method `MethodSize`), you must insert the comment `// BSLLS:CognitiveComplexity-off` and `// BSLLS:MethodSize-off` (or `// BSLLS:CognitiveComplexity-выкл` and `// BSLLS:MethodSize-выкл`)

### Disable all diagnostics for code block

If you want to disable diagnostics for a section of code, leaving the BSL LS option to analyze the remaining ones, you must `wrap` the hidden section of code

```bsl
// BSLLS-off
Procedure SkipByBSLLS()
    // content will be ignored
EndProcedure
// BSLLS-on

Procedure AnalyzeByBSLLS()
    // content will be  analyzed
EndProcedure
```

### Disable specific diagnostics for code block

If you need to disable specific diagnostics (for example, the cognitive complexity `CognitiveComplexity` and the limit on the size of the method `MethodSize`) for a section of code, you must `wrap` the hidden section of code for example

```bsl
// BSLLS:MethodSize-off
Procedure SkipedByMethodSizeMethod()
    // Very long code block
EndProcedure
// BSLLS:MethodSize-on

// BSLLS:CognitiveComplexity-off
Procedure SkipedByCognitiveComplexityMethod()
    // Very long and complexity code block
EndProcedure
// BSLLS:CognitiveComplexity-on
```

Embedding `wrappers`, is supported, i.e. it is possible to escape a block of code from several diagnostics, for example

```bsl
// BSLLS:CognitiveComplexity-off
// BSLLS:MethodSize-off
Procedure SkipedByMethodSizeCognitiveComplexityMethod()
    // Very long code block
EndProcedure
// BSLLS:MethodSize-on

Procedure SkipedByCognitiveComplexityПроцедура()
   // Very complex and incomprehensible code block, but not long
EndProcedure
// BSLLS:CognitiveComplexity-on
```

### Disable single line diagnostics

To escape a single line, you can use `wrapper` as in the example above, but it is more convenient to use `inline comment`, i.e. the comment located at the end of the line, example

```bsl
Procedure SomeMethode()
    if true then// BSLLS:CanonicalSpellingKeywords-off
        // Analized content
    EndIf;
EndProcedure
```
