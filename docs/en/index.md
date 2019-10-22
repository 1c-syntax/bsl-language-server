# BSL Language Server

[![Build Status](https://travis-ci.org/1c-syntax/bsl-language-server.svg?branch=master)](https://travis-ci.org/1c-syntax/bsl-language-server)
[![Download](https://img.shields.io/github/release/1c-syntax/bsl-language-server.svg?label=download&style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases/latest)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)

[Language Server Protocol](https://microsoft.github.io/language-server-protocol/) implementation for 1C (BSL) - 1C:Enterprise 8 and [OneScript](http://oscript.io) languages.

[Russian version](en/index.md)

- [Contributing guidelines](contributing/index.md)
- <a href="#capabilities">Capabilities</a>
- <a href="#cli">Run from command line</a>
- <a href="#analyze">Run in analyzer mode</a>
- <a href="#format">Run in formatter mode</a>
- <a href="#configuration">Configuration file</a>
- <a href="#reporters">Reporters</a>
- <a href="#diagnostics">Diagnostics</a>

<a id="capabilities"></a>

## Capabilities

- File formatting
- Selected region formatting
- Symbol definition for current file (regions, procedures, functions, variables, defined via `Var` keyword)
- Folding regions definition `#Region`, `#If`, procedures and functions, code blocks
- Methods "Cognitive Complexity" score
- Diagnostics
- Quick fixes for several diagnostics
- Run diagnostics engine from command line
- Run formatter engine from command line

<a id="cli"></a>

## Run from command line

jar-files run through `java -jar path/to/file.jar`.

```sh
java -jar bsl-language-server.jar --help

usage: BSL language server [-a] [-c <arg>] [-f] [-h] [-o <arg>] [-r <arg>] [-s <arg>]
 -a,--analyze               Run analysis and get diagnostic info
 -c,--configuration <arg>   Path to language server configuration file
 -f,--format                Format files in source directory
 -h,--help                  Show help.
 -o,--outputDir <arg>       Output report directory
 -r,--reporter <arg>        Reporter key
 -s,--srcDir <arg>          Source directory
 -v,--version               Version
```

Starting BSL Language Server in standard mode will run the Language Server communicating via [LSP]([language server protocol](https://microsoft.github.io/language-server-protocol/)). stdin and stdout are used for communication.

By default diagnostics texts are displayed in Russian. To switch the diagnostics text language you need to set parameter `diagnosticLanguage` in configuration file or raise an event `workspace/didChangeConfiguration`:

<a id="analyze"></a>

## Run in analyzer mode

To run in analyzer mode use parameter `--analyze` (short `-a`). To set source code folder for analysis use parameter
`--srcDir` (short `-s`) followed by the path (relative or absolute) to the source code folder.

To generate an analysis report you need to specify one or more reporters. To specify reporter use parameter `--reporter` or `-r`, followed by reporter key. You may specify several reporters. The list of reporters see in section  **Reporters**.

Command line example to run analysis:

```sh
java -jar bsl-language-server.jar --analyze --srcDir ./src/cf --reporter json
```

> When run analysis for large code base it is recommended to set parameter {code0}-Xmx{/code0} to set maximum limit of  memory being allocated to java process. The size of allocated memory depends on the size of code base for analysis.

```sh
java -Xmx4g -jar bsl-language-server.jar ... other parameters
```

<a id="format"></a>

## Run in formatter mode

To run in formatter mode use parameter `--format` (short `-f`). To set source code folder for formatting use parameter `--srcDir` (short `-s`) followed by the path (relative or absolute) to the source code folder.

Command line example to run formatting:

```sh
java -jar bsl-language-server.jar --format --srcDir ./src/cf
```

<a id="configuration"></a>

## Configuration file

Configuration file is a file in JSON format. The file can contain the following blocks:

- `diagnosticLanguage` - `String` - diagnostics text language. Valid values: `en` or `ru`. By default set to `ru`.
- `showCognitiveComplexityCodeLens` - `Boolean` - show cognitive complexity score above method definition (codeLens). By default set to `true`.
- `computeDiagnostics` - `String` - trigger for the computation of diagnostics. Valid values: `onType` (on file edit), `onSave` (on file save), `never`. By default set to `onSave`.
- `traceLog` - `String` - path to file to log all inbound and outbound requests between BSL Language Server and Language Client from used IDE. Can be absolute or relative (to the project root). If set ** significantly slows down** communication speed between server and client. Dy default - not set.
- `diagnostics` - `Object` - diagnostics settings collection. Collection items are objects with the structure as following:
    - object key - `String` - diagnostics key, as given in section <a href="#diagnostics">Diagnostics</a>.
    - object value
        - `Boolean` - `false` to disable diagnostics, `true` - to enable diagnostics without additional settings. By default set to `true`.
        - `Object` - Structure of settings for each diagnostics. Available parameters are give in each diagnostics section.

You may use this JSON-schema to simplify file editing:

```
https://raw.githubusercontent.com/1c-syntax/bsl-language-server/master/src/main/resources/com/github/_1c_syntax/bsl/languageserver/configuration/schema.json
```

Configuration file example, setting:

- diagnostics text language - Russian;
- [LineLength - Line length limit](diagnostics/LineLength.md) diagnostics settings - max line length set to 140 characters;
- [MethodSize - Method size limit](diagnostics/MethodSize.md) diagnostics settings - disabled;

```json
{
  "$schema": "https://raw.githubusercontent.com/1c-syntax/bsl-language-server/master/src/main/resources/com/github/_1c_syntax/bsl/languageserver/configuration/schema.json",
  "diagnosticLanguage": "en",
  "diagnostics": {
    "LineLength": {
      "maxLineLength": 140
    },
    "MethodSize": false
  }
}
```

<a id="reporters"></a>

## Reporters

Used to get analysis results.

### Implemented reporters

- [json](reporters/json.md) - analysis results in proprietary JSON format, supported by [SonarQube 1C (BSL) Community Plugin](https://github.com/1c-syntax/sonar-bsl-plugin-community);
- [generic](reporters/generic.md) - analysis results in [Generic issue](https://docs.sonarqube.org/latest/analysis/generic-issue/) format for SonarQube;
- [junit](reporters/junit.md);
- [tslint](reporters/tslint.md);
- [console](reporters/console.md).

<a id="diagnostics"></a>

## Diagnostics

Used for code analysis to meet coding standards and search for possible errors.

Some of diagnostics are disabled by default. Use <a href="#configuration">configuration file</a> to enable them.

To escape individual sections of code or files from triggering diagnostics, you can use special comments of the form `// BSLLS:DiagnosticKey-off` . This functionality is described in more detail in [Escaping sections of code](features/DiagnosticIgnorance.md) .

### Implemented diagnostics

| Key | Name| Enabled by default | Tags |
| --- | --- | :-: | --- |
| [BeginTransactionBeforeTryCatch](diagnostics/BeginTransactionBeforeTryCatch.md) | Violating transaction rules for the 'BeginTransaction' method | Yes | `standard` |
| [CanonicalSpellingKeywords](diagnostics/CanonicalSpellingKeywords.md) | Canonical spelling of keywords | Yes | `standard` |
| [CognitiveComplexity](diagnostics/CognitiveComplexity.md) | Cognitive complexity | Yes | `brainoverload` |
| [CommentedCode](diagnostics/CommentedCode.md) | Commented out code | Yes | `standard`<br/>`badpractice` |
| [CommitTransactionOutsideTryCatch](diagnostics/CommitTransactionOutsideTryCatch.md) | Violating transaction rules for the 'CommitTransaction' method | Yes | `standard` |
| [DeletingCollectionItem](diagnostics/DeletingCollectionItem.md) | Deleting an item when iterating through collection using the operator "For each ... In ... Do" | Yes | `standard`<br/>`error` |
| [DeprecatedMessage](diagnostics/DeprecatedMessage.md) | Restriction on the use of deprecated "Message" method | Yes | `standard`<br/>`deprecated` |
| [EmptyCodeBlock](diagnostics/EmptyCodeBlock.md) | Empty code block | Yes | `badpractice`<br/>`suspicious` |
| [EmptyStatement](diagnostics/EmptyStatement.md) | Empty statement | Yes | `badpractice` |
| [ExtraCommas](diagnostics/ExtraCommas.md) | Extra commas when calling a method | Yes | `standard`<br/>`badpractice` |
| [FunctionShouldHaveReturn](diagnostics/FunctionShouldHaveReturn.md) | Function must have Return statement | Yes | `suspicious`<br/>`unpredictable` |
| [IdenticalExpressions](diagnostics/IdenticalExpressions.md) | There are identical sub-expressions to the left and to the right of the "foo" operator | Yes | `suspicious` |
| [IfConditionComplexity](diagnostics/IfConditionComplexity.md) | If condition is too complex | Yes | `brainoverload` |
| [IfElseDuplicatedCodeBlock](diagnostics/IfElseDuplicatedCodeBlock.md) | Duplicated code blocks in If...Then...ElsIf... | Yes | `suspicious` |
| [IfElseDuplicatedCondition](diagnostics/IfElseDuplicatedCondition.md) | Duplicated conditions in If...Then...ElsIf... | Yes | `suspicious` |
| [IfElseIfEndsWithElse](diagnostics/IfElseIfEndsWithElse.md) | Using If...Then...ElsIf... statement | Yes | `badpractice` |
| [LineLength](diagnostics/LineLength.md) | Line length restriction | Yes | `standard`<br/>`badpractice` |
| [MagicNumber](diagnostics/MagicNumber.md) | Using magic number | Yes | `badpractice` |
| [MethodSize](diagnostics/MethodSize.md) | Method size restriction | Yes | `badpractice` |
| [MissingCodeTryCatchEx](diagnostics/MissingCodeTryCatchEx.md) | Missing code in Raise block in "Try ... Raise ... EndTry" | Yes | `standard`<br/>`badpractice` |
| [MissingSpace](diagnostics/MissingSpace.md) | Missing space | Yes | `badpractice` |
| [NestedConstructorsInStructureDeclaration](diagnostics/NestedConstructorsInStructureDeclaration.md) | Nested constructors with parameters in structure declaration | Yes | `badpractice`<br/>`brainoverload` |
| [NestedStatements](diagnostics/NestedStatements.md) | Control flow statements should not be nested too deep | Yes | `badpractice`<br/>`brainoverload` |
| [NestedTernaryOperator](diagnostics/NestedTernaryOperator.md) | Nested ternary operator | Yes | `brainoverload` |
| [NonExportMethodsInApiRegion](diagnostics/NonExportMethodsInApiRegion.md) | Non export methods in API regions | Yes | `standard` |
| [NumberOfOptionalParams](diagnostics/NumberOfOptionalParams.md) | Limit number of optional parameters in method | Yes | `standard`<br/>`brainoverload` |
| [NumberOfParams](diagnostics/NumberOfParams.md) | Number of method parameters restriction | Yes | `standard`<br/>`brainoverload` |
| [NumberOfValuesInStructureConstructor](diagnostics/NumberOfValuesInStructureConstructor.md) | Number of values in structure constructor restriction | Yes | `standard`<br/>`brainoverload` |
| [OneStatementPerLine](diagnostics/OneStatementPerLine.md) | One statement per line | Yes | `standard`<br/>`design` |
| [OrderOfParams](diagnostics/OrderOfParams.md) | Order of method parameters | Yes | `standard`<br/>`design` |
| [PairingBrokenTransaction](diagnostics/PairingBrokenTransaction.md) | Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" | Yes | `standard` |
| [ParseError](diagnostics/ParseError.md) | Error parsing source code | Yes | `error` |
| [ProcedureReturnsValue](diagnostics/ProcedureReturnsValue.md) | Procedure must have no Return value | Yes | `error` |
| [SelfAssign](diagnostics/SelfAssign.md) | Variable self assignment | Yes | `suspicious` |
| [SelfInsertion](diagnostics/SelfInsertion.md) | Insert a collection into itself | Yes | `standard`<br/>`unpredictable`<br/>`performance` |
| [SemicolonPresence](diagnostics/SemicolonPresence.md) | Statement should end with ";" | Yes | `standard`<br/>`badpractice` |
| [SeveralCompilerDirectives](diagnostics/SeveralCompilerDirectives.md) | Erroneous indication of several compilation directives | Yes | `unpredictable`<br/>`error` |
| [SpaceAtStartComment](diagnostics/SpaceAtStartComment.md) | Space at the beginning of the comment | Yes | `standard` |
| [TernaryOperatorUsage](diagnostics/TernaryOperatorUsage.md) | Ternary operator usage | No | `brainoverload` |
| [TryNumber](diagnostics/TryNumber.md) | Cast to number in try catch block | Yes | `standard` |
| [UnknownPreprocessorSymbol](diagnostics/UnknownPreprocessorSymbol.md) | Unknown preprocessor symbol | Yes | `standard`<br/>`error` |
| [UnreachableCode](diagnostics/UnreachableCode.md) | Unreachable Code | Yes | `design`<br/>`suspicious` |
| [UseLessForEach](diagnostics/UseLessForEach.md) | Useless For Each loop | Yes | `clumsy` |
| [UsingCancelParameter](diagnostics/UsingCancelParameter.md) | Using "Cancel" parameter | Yes | `standard`<br/>`badpractice` |
| [UsingFindElementByString](diagnostics/UsingFindElementByString.md) | Restriction on the use of "FindByDescription" and "FindByCode" methods | Yes | `standard`<br/>`badpractice`<br/>`performance` |
| [UsingGoto](diagnostics/UsingGoto.md) | "Goto" usage | Yes | `standard`<br/>`badpractice` |
| [UsingHardcodePath](diagnostics/UsingHardcodePath.md) | Using hardcode file paths and ip addresses in code | Yes | `standard` |
| [UsingHardcodeSecretInformation](diagnostics/UsingHardcodeSecretInformation.md) | Storing confidential information in code | Yes | `standard` |
| [UsingModalWindows](diagnostics/UsingModalWindows.md) | Using modal windows | No | `standard` |
| [UsingObjectNotAvailableUnix](diagnostics/UsingObjectNotAvailableUnix.md) | Using of objects not available in Unix | Yes | `standard`<br/>`lockinos` |
| [UsingServiceTag](diagnostics/UsingServiceTag.md) | Using service tags | Yes | `badpractice` |
| [UsingSynchronousCalls](diagnostics/UsingSynchronousCalls.md) | Using synchronous calls | No | `standard` |
| [UsingThisForm](diagnostics/UsingThisForm.md) | Using the "ThisForm" property | Yes | `standard`<br/>`deprecated` |
| [YoLetterUsage](diagnostics/YoLetterUsage.md) | Using "Ё" letter in code | Yes | `standard` |
