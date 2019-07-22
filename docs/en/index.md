# BSL Language Server

[![Build Status](https://travis-ci.org/1c-syntax/bsl-language-server.svg?branch=master)](https://travis-ci.org/1c-syntax/bsl-language-server)
[![Download](https://img.shields.io/github/release/1c-syntax/bsl-language-server.svg?label=download&style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases/latest)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)

[Language Server Protocol](https://microsoft.github.io/language-server-protocol/) implementation for 1C (BSL) - 1C:Enterprise 8 and [OneScript](http://oscript.io) languages.

[English version](en/index.md)

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
- Определение символов текущего файла (области, процедуры, функции, переменные, объявленные через `Перем`)
- Определение сворачиваемых областей - `#Область`, `#Если`, процедуры и функции, блоки кода
- Diagnostics
- "Быстрые исправления" (quick fixes) для ряда диагностик
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

Configuration file is a file in JSON format.
The file can contain the following blocks:

- `diagnosticLanguage` - `String` - diagnostics text language. Valid values: `en` or `ru`. By default set to `ru`.
- `traceLog` - `String` - path to file to log all inbound and outbound requests between BSL Language Server and Language Client from used IDE. Can be absolute or relative (to the project root). If set ** significantly slows down** communication speed between server and client. Dy default - not set.
- `diagnostics` - `Object` - diagnostics settings collection. Collection items are objects with thestructure as following:
    - object key - `String` - diagnostics key, as given in section <a href="#diagnostics">Diagnostics</a>.
    - object value
        - `Boolean` - `false` to disable diagnostics, `true` - to enable diagnostics without additional settings. By deafult set to `true`.
        - `Object` - Structure of settings for each diagnostics. Available parameters are give in each diagnostics section.

Configuration file example, setting:

- diagnostics text language - Russian;
- [LineLength - Line length limit](diagnostics/LineLength.md) diagnostics settings - max line length set to 140 characters;
- [MethodSize - Method size limit](diagnostics/MethodSize.md) diagnostics settings - disabled;

```json
{
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

### Implemented diagnostics

- [CanonicalSpellingKeywords - Canonical spelling of keywords ](diagnostics/CanonicalSpellingKeywords.md)
- [DeprecatedMessage - Restriction on the use of deprecated "Message" method](diagnostics/DeprecatedMessage.md)
- [EmptyCodeBlock - Empty code block](diagnostics/EmptyCodeBlock.md)
- [EmptyStatement - Empty Ssatement](diagnostics/EmptyStatement.md)
- [ExtraCommas - Commas without a parameter at the end of a method call](diagnostics/ExtraCommas.md)
- [FunctionShouldHaveReturn - Function must have Return statement](diagnostics/FunctionShouldHaveReturn.md)
- [IfElseDuplicatedCodeBlockDiagnostic - Duplicated code blocks in If...Then...ElsIf...](diagnostics/IfElseDuplicatedCodeBlock.md)
- [IfElseDuplicatedConditionDiagnostic - Duplicated conditions in If...Then...ElsIf...](diagnostics/IfElseDuplicatedCondition.md)
- [IfElseIfEndsWithElse - Using If...Then...ElsIf... statement](diagnostics/IfElseIfEndsWithElse.md)
- [LineLength - Line length restriction](diagnostics/LineLength.md)
- [MagicNumber - Using magic number](diagnostics/MagicNumber.md)
- [MethodSize - Method size restriction](diagnostics/MethodSize.md)
- [NestedConstructorsInStructureDeclaration - Nested constructors with parameters in structure declaration](diagnostics/NestedConstructorsInStructureDeclaration.md)
- [NestedStatements - Control flow statements should not be nested too deep](diagnostics/NestedStatements.md)
- [NestedTernaryOperator - Nested Ternary Operator](diagnostics/NestedTernaryOperator.md)
- [NumberOfOptionalParams - Number of optional method parameters restriction](diagnostics/NumberOfOptionalParams.md)
- [NumberOfParams - Number of method parameters restriction](diagnostics/NumberOfParams.md)
- [NumberOfValuesInStructureConstructor - Number of values in structure constructor restriction](diagnostics/NumberOfValuesInStructureConstructor.md)
- [OneStatementPerLine - One Statement Per Line](diagnostics/OneStatementPerLine.md)
- [ParseError - Error parsing source code](diagnostics/ParseError.md)
- [OrderOfParams - Order of method parameters](diagnostics/OrderOfParams.md)
- [ProcedureReturnsValue - Procedure must have no Return statement](diagnostics/ProcedureReturnsValue.md)
- [SemicolonPresence - Statement should end with ";"](diagnostics/SemicolonPresence.md)
- [SelfAssign - Variable self assignment](diagnostics/SelfAssign.md)
- [TryNumber - Cast to number in try catch block](diagnostics/TryNumber.md)
- [UnknownPreprocessorSymbol - Unknown Preprocessor Symbol](diagnostics/UnknownPreprocessorSymbol.md)
- [UseLessForEach - Useless For Each loop](diagnostics/UseLessForEach.md)
- [UsingCancelParameter - Using "Cancel" parameter](diagnostics/UsingCancelParameter.md)
- [UsingFindElementByString - Restriction on the use of "FindByDescription" and "FindByCode" methods](diagnostics/UsingFindElementByString.md)
- [UsingServiceTag - Using service tags](diagnostics/UsingServiceTag.md)
- [YoLetterUsageDiagnostic - Using "Ё" letter in code](diagnostics/YoLetterUsage.md)
