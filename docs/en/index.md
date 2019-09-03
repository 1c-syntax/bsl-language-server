# BSL Language Server

[![Build Status](https://travis-ci.org/1c-syntax/bsl-language-server.svg?branch=master)](https://travis-ci.org/1c-syntax/bsl-language-server)
[![Download](https://img.shields.io/github/release/1c-syntax/bsl-language-server.svg?label=download&style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases/latest)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)

[Language Server Protocol](https://microsoft.github.io/language-server-protocol/) implementation for 1C (BSL) - 1C:Enterprise 8 and [OneScript](http://oscript.io) languages.

[Russian version](../index.md)

- [Contributing guidelines](contributing/index.md)
- <a href="#capabilities">Capabilities</a>
- <a href="#cli">Run from command line</a>
- <a href="#analyze">Run in analyzer mode</a>
- <a href="#configuration">Configuration file</a>
- <a href="#reporters">Reporters</a>
- <a href="#diagnostics">Diagnostics</a>

<a id="capabilities"></a>

## Capabilities

- File formatting
- Selected region formatting
- Symbol definition for current file (regions, procedures, functions, variables, defined via `Var` keyword)
- Folding regions definition `#Region`, `#If`, procedures and functions, code blocks
* Methods "Cognitive Complexity" score
- Diagnostics
- Quick fixes for several diagnostics
- Run diagnostics engine from command line

<a id="cli"></a>

## Run from command line

jar-files run through `java -jar path/to/file.jar`.

```sh
java -jar bsl-language-server.jar --help

usage: BSL language server [-a] [-c <arg>] [-h] [-o <arg>] [-r <arg>] [-s <arg>]
 -a,--analyze               Run analysis and get diagnostic info
 -c,--configuration <arg>   Path to language server configuration file
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

<a id="configuration"></a>

## Configuration file

Configuration file is a file in JSON format. The file can contain the following blocks:

* `diagnosticLanguage` - `String` - diagnostics text language. Valid values: `en` or `ru`. By default set to `ru`.
* `showCognitiveComplexityCodeLens` - `Boolean` - show cognitive complexity score above method definition (codeLens). By default set to `true`.
* `computeDiagnostics` - `String` - trigger for the computation of diagnostics. Valid values: `onType` (on file edit), `onSave` (on file save), `never`. By default set to `onSave`.
* `traceLog` - `String` - path to file to log all inbound and outbound requests between BSL Language Server and Language Client from used IDE. Can be absolute or relative (to the project root). If set ** significantly slows down** communication speed between server and client. Dy default - not set.
* `diagnostics` - `Object` - diagnostics settings collection. Collection items are objects with the structure as following:
    * object key - `String` - diagnostics key, as given in section <a href="#diagnostics">Diagnostics</a>.
    * object value
        - `Boolean` - `false` to disable diagnostics, `true` - to enable diagnostics without additional settings. By default set to `true`.
        - `Object` - Structure of settings for each diagnostics. Available parameters are give in each diagnostics section.

You may use this JSON-schema to simplify file editing:

```
https://raw.githubusercontent.com/1c-syntax/bsl-language-server/master/src/main/resources/org/github/_1c_syntax/bsl/languageserver/configuration/schema.json
```

Configuration file example, setting:

- diagnostics text language - Russian;
- [LineLength - Line length limit](diagnostics/LineLength.md) diagnostics settings - max line length set to 140 characters;
- [MethodSize - Method size limit](diagnostics/MethodSize.md) diagnostics settings - disabled;

```json
{
  "$schema": "https://raw.githubusercontent.com/1c-syntax/bsl-language-server/master/src/main/resources/org/github/_1c_syntax/bsl/languageserver/configuration/schema.json",
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

### Implemented diagnostics

| Key | Name| Enabled by default |
| --- | --- | :-: |
| [CanonicalSpellingKeywords](diagnostics/CanonicalSpellingKeywords.md) | Canonical spelling of keywords | Yes |
| [CognitiveComplexity](diagnostics/CognitiveComplexity.md) | Cognitive complexity | Yes |
| [DeletingCollectionItem](diagnostics/DeletingCollectionItem.md) | Deleting an item when iterating through collection using the operator "For each ... In ... Do" | Yes |
| [DeprecatedMessage](diagnostics/DeprecatedMessage.md) | Restriction on the use of deprecated "Message" method | Yes |
| [EmptyCodeBlock](diagnostics/EmptyCodeBlock.md) | Empty code block | Yes |
| [EmptyStatement](diagnostics/EmptyStatement.md) | Empty statement | Yes |
| [ExtraCommas](diagnostics/ExtraCommas.md) | Extra commas when calling a method | Yes |
| [FunctionShouldHaveReturn](diagnostics/FunctionShouldHaveReturn.md) | Function must have Return statement | Yes |
| [IdenticalExpressions](diagnostics/IdenticalExpressions.md) | There are identical sub-expressions to the left and to the right of the "foo" operator | Yes |
| [IfConditionComplexity](diagnostics/IfConditionComplexity.md) | If condition is too complex | Yes |
| [IfElseDuplicatedCodeBlock](diagnostics/IfElseDuplicatedCodeBlock.md) | Duplicated code blocks in If...Then...ElsIf... | Yes |
| [IfElseDuplicatedCondition](diagnostics/IfElseDuplicatedCondition.md) | Duplicated conditions in If...Then...ElsIf... | Yes |
| [IfElseIfEndsWithElse](diagnostics/IfElseIfEndsWithElse.md) | Using If...Then...ElsIf... statement | Yes |
| [LineLength](diagnostics/LineLength.md) | Line length restriction | Yes |
| [MagicNumber](diagnostics/MagicNumber.md) | Using magic number | Yes |
| [MethodSize](diagnostics/MethodSize.md) | Method size restriction | Yes |
| [MissingCodeTryCatchEx](diagnostics/MissingCodeTryCatchEx.md) | Missing code in Raise block in "Try ... Raise ... EndTry" | Yes |
| [NestedConstructorsInStructureDeclaration](diagnostics/NestedConstructorsInStructureDeclaration.md) | Nested constructors with parameters in structure declaration | Yes |
| [NestedStatements](diagnostics/NestedStatements.md) | Control flow statements should not be nested too deep | Yes |
| [NestedTernaryOperator](diagnostics/NestedTernaryOperator.md) | Nested ternary operator | Yes |
| [NumberOfOptionalParams](diagnostics/NumberOfOptionalParams.md) | Number of optional method parameters restriction | Yes |
| [NumberOfParams](diagnostics/NumberOfParams.md) | Number of method parameters restriction | Yes |
| [NumberOfValuesInStructureConstructor](diagnostics/NumberOfValuesInStructureConstructor.md) | Number of values in structure constructor restriction | Yes |
| [OneStatementPerLine](diagnostics/OneStatementPerLine.md) | One statement per line | Yes |
| [OrderOfParams](diagnostics/OrderOfParams.md) | Order of method parameters | Yes |
| [PairingBrokenTransaction](diagnostics/PairingBrokenTransaction.md) | Violation of pairing using methods "BeginTransaction()" & "CommitTransaction()" / "RollbackTransaction()" | Yes |
| [ParseError](diagnostics/ParseError.md) | Error parsing source code | Yes |
| [ProcedureReturnsValue](diagnostics/ProcedureReturnsValue.md) | Procedure must have no Return value | Yes |
| [SelfAssign](diagnostics/SelfAssign.md) | Variable self assignment | Yes |
| [SemicolonPresence](diagnostics/SemicolonPresence.md) | Statement should end with ";" | Yes |
| [SeveralCompilerDirectives](diagnostics/SeveralCompilerDirectives.md) | Misuse of multiple compilation directives | Yes |
| [SpaceAtStartComment](diagnostics/SpaceAtStartComment.md) | Space at the beginning of the comment | Yes |
| [TernaryOperatorUsage](diagnostics/TernaryOperatorUsage.md) | Ternary operator usage | No |
| [TryNumber](diagnostics/TryNumber.md) | Cast to number in try catch block | Yes |
| [UnknownPreprocessorSymbol](diagnostics/UnknownPreprocessorSymbol.md) | Unknown preprocessor symbol | Yes |
| [UseLessForEach](diagnostics/UseLessForEach.md) | Useless For Each loop | Yes |
| [UsingCancelParameter](diagnostics/UsingCancelParameter.md) | Using "Cancel" parameter | Yes |
| [UsingFindElementByString](diagnostics/UsingFindElementByString.md) | Restriction on the use of "FindByDescription" and "FindByCode" methods | Yes |
| [UsingGoto](diagnostics/UsingGoto.md) | Использование "Перейти" | Yes |
| [UsingServiceTag](diagnostics/UsingServiceTag.md) | Using service tags | Yes |
| [YoLetterUsage](diagnostics/YoLetterUsage.md) | Using "Ё" letter in code | Yes |
