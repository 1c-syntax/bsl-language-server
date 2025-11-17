# BSL Language Server

[![Actions Status](https://github.com/1c-syntax/bsl-language-server/workflows/Java%20CI/badge.svg)](https://github.com/1c-syntax/bsl-language-server/actions)
[![Download](https://img.shields.io/github/release/1c-syntax/bsl-language-server.svg?label=download&style=flat)](https://github.com/1c-syntax/bsl-language-server/releases/latest)
[![Latest release](https://badgen.net/github/release/1c-syntax/bsl-language-server)](https://github.com/1c-syntax/bsl-language-server/releases)
[![GitHub Releases](https://img.shields.io/github/downloads/1c-syntax/bsl-language-server/latest/total?style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases)
[![GitHub All Releases](https://img.shields.io/github/downloads/1c-syntax/bsl-language-server/total?style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Transifex](https://img.shields.io/badge/translation-transifex-green)](https://www.transifex.com/1c-syntax/bsl-language-server)
[![Benchmark](bench/benchmark.svg)](bench/index.html)
[![telegram](https://img.shields.io/badge/telegram-chat-green.svg)](https://t.me/bsl_language_server)

[Language Server Protocol](https://microsoft.github.io/language-server-protocol/) implementation for 1C (BSL) - 1C:Enterprise 8 and [OneScript](http://oscript.io) languages.

* [Contributing guidelines](contributing/index.md)
* <a href="#capabilities">Capabilities</a>
* <a href="#cli">Run from command line</a>
* <a href="#websocket">Run in websocket mode</a>
* <a href="#analyze">Run in analyzer mode</a>
* <a href="#format">Run in formatter mode</a>
* <a href="#configuration">Configuration file</a>
* <a href="reporters">Reporters</a>
* <a href="diagnostics">Diagnostics</a>
* <a href="features">Features</a>
* [Frequently asked questions](faq.md)
* [System requirements](systemRequirements.md)
* <a href="#thanks">Acknowledgments</a>

<a id="capabilities"></a>

Perfomance measurement - [SSL 3.1](../bench/index.html)

## Features

* File formatting
* Selected region formatting
* Symbol definition for current file (regions, procedures, functions, variables, defined via `Var` keyword)
* Folding regions definition `#Region`, `#If`, procedures and functions, code blocks, queries
* Methods "Cognitive Complexity" and "Cyclomatic Complexity" scores
* Tooltip on local methods and methods of common / manager modules
* Go to method definitions
* Finding places to use methods
* Method call hierarchy
* Expand selection
* Display color representation and convert between `Color` and `WebColors`
* Diagnostics
* Quick fixes and code actions for several diagnostics
* Run diagnostics engine from command line
* Run formatter engine from command line
* Renaming Symbols

## Supported protocol operations

??? workspace
    | Operation                                                     | Support                                                    | Commentary                                                  |
    | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
    | [didChangeWorkspaceFolders](https://microsoft.github.io/language-server-protocol/specification-current#workspace_didChangeWorkspaceFolders) |  <img src="./assets/images/cross.svg" alt="no" width="20">   |                                                              |
    | [didChangeConfiguration](https://microsoft.github.io/language-server-protocol/specification#workspace_didChangeConfiguration) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | with restrictions see [#1431](https://github.com/1c-syntax/bsl-language-server/issues/1431) |
    | [didChangeWatchedFiles](https://microsoft.github.io/language-server-protocol/specification#workspace_didChangeWatchedFiles) |  <img src="./assets/images/cross.svg" alt="no" width="20">   |                                                              |
    | [symbol](https://microsoft.github.io/language-server-protocol/specification#workspace_symbol) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |
    | [executeCommand](https://microsoft.github.io/language-server-protocol/specification#workspace_executeCommand) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |
    | [diagnostic/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_diagnostic_refresh) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | sent on configuration change |
    | [applyEdit](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_applyEdit) |  <img src="./assets/images/cross.svg" alt="no" width="20">   |                                                              |
    | [willCreateFiles](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_willCreateFiles) |   <img src="./assets/images/cross.svg" alt="no" width="20">  |                                                              |

??? "Text Synchronization"
| Opertaion                                                                                                                                            | Supported                                                      | Comment                                                                                |
| --------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| [didOpen](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didOpen) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
| [didChange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didChange) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | textDocumentSyncKind = Full                                  |                  |
| [didClose](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didClose) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
| [didSave](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didSave) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
| [willSave](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_willSave) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
| [willSaveWaitUntil](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_willSaveWaitUntil) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |

??? textDocument
    | Operation                                                     | Support                                                    | Commentary                                                  | Is configured? |
    | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ---------------- |
    | [publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_publishDiagnostics) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | tagSupport = true<br />versionSupport = true<br />[diagnostics](./diagnostics/index.md) | yes               |
    | [diagnostic](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_diagnostic) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | interFileDependencies = true<br />workspaceDiagnostics = false | no               |
    | [completion](https://github.com/1c-syntax/bsl-language-server/blob/develop/docs/diagnostics/index.md) | <img src="./assets/images/cross.svg" alt="no" width="20">    | resolveProvider = false                                      |                  |
    | [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#completionItem_resolve) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [hover](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_hover) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | contentFormat = MarkupContent                                |                  |
    | [signatureHelp](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_signatureHelp) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [declaration](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_declaration) | <img src="./assets/images/cross.svg" alt="no" width="20">    | not applicable in 1C:Enterprise                                |                  |
    | [definition](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_definition) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | linkSupport = true                                           |                  |
    | [typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_typeDefinition) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [implementation](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_implementation) | <img src="./assets/images/cross.svg" alt="no" width="20">    | not applicable in 1C:Enterprise                                |                  |
    | [references](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_references) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [documentHighlight](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentHighlight) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [documentSymbol](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentSymbol) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | hierarchicalDocumentSymbolSupport = true                     |                  |
    | [codeAction](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_codeAction) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | codeActionKinds = ? (см. [#1433](https://github.com/1c-syntax/bsl-language-server/issues/1433))<br />isPreferredSupport = true | yes               |
    | [codeAction/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeAction_resolve) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [codeLens](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_codeLens) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | resolveProvider = false                                      | yes               |
    | [codeLens/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeLens_resolve) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [codeLens/refresh](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeLens_refresh) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [documentLink](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentLink) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Showing hyperlinks to documentation on diagnostics.<br />tooltipSupport = true<br />resolveProvider = false | yes               |
    | [documentLink/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#documentLink_resolve) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [documentColor](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentColor) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [colorPresentation](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_colorPresentation) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [formatting](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_formatting) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [rangeFormatting](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rangeFormatting) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [onTypeFormatting](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_onTypeFormatting) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [rename](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rename) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [prepareRename](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareRename) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [foldingRange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_foldingRange) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [selectionRange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_selectionRange) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [prepareCallHierarchy](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareCallHierarchy) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [callHierarchy/incomingCalls](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#callHierarchy_incomingCalls) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [callHierarchy/outgoingCalls](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#callHierarchy_outgoingCalls) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [semanticTokens](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [linkedEditingRange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_linkedEditingRange) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [moniker](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_moniker) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [inlayHint](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    | resolveProvider = false | yes |
    | [inlayHint/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#inlayHint_resolve) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [inlayHint/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_inlayHint_refresh) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |

<a id="cli"></a>

## Run from command line

jar-files run through `java -jar path/to/file.jar`.

```sh
java -jar bsl-language-server.jar --help

BSL language server
Usage: bsl-language-server [-h] [-c=<path>] [COMMAND [ARGS]]
  -c, --configuration=<path>
               Path to language server configuration file
  -h, --help   Show this help message and exit
Commands:
  analyze, -a, --analyze      Run analysis and get diagnostic info
  format, -f, --format        Format files in source directory
  version, -v, --version      Print version
  lsp, --lsp                  LSP server mode (default)
  websocket, -w, --websocket  Websocket server mode
```

Starting BSL Language Server in standard mode will run the Language Server communicating via [LSP]([language server protocol](https://microsoft.github.io/language-server-protocol/)). stdin and stdout are used for communication.

By default diagnostics texts are displayed in Russian. To switch the diagnostics text language you need to set parameter `language` in configuration file or raise an event `workspace/didChangeConfiguration`:

```json
{
  "language": "en"
}
```

<a id="websocket"></a>

## Run in websocket mode

By default, interaction with the server goes through standard input / output streams. 
But you can run BSL Language Server with a built-in web server and interact with it via websocket.

To do this, start the BSL Language Server with the `--websocket` or `-w` option:

```sh
Usage: bsl-language-server websocket [-h] [--app.websocket.lsp-path=<path>]
                                     [-c=<path>] [--server.port=<port>]
Websocket server mode
      --app.websocket.lsp-path=<path>
                             Path to LSP endpoint. Default is /lsp
  -c, --configuration=<path> Path to language server configuration file
  -h, --help                 Show this help message and exit
      --server.port=<port>   Port to listen. Default is 8025
```

Once started, BSL Language Server will be available at `ws://localhost:8025/lsp`.

To redefine the port to the LSP server, you must use the `--server.port` option and the port number.
To redefine the path to the LSP server, you must use the `--app.websocket.lsp-path` option and a path starting with `/`.

An example of running BSL Language Server in websocket mode with port 8080:

```sh
java -jar bsl-language-server.jar --websocket --server.port=8080
```

> For large projects, it is recommended to specify the -Xmx parameter, which is responsible for the RAM limit for the java process. The amount of allocated memory depends on the size of the analyzed codebase.

```sh
java -Xmx4g -jar bsl-language-server.jar ... other parameters
```

<a id="analyze"></a>

## Run in analyzer mode

To run in analyzer mode use parameter `--analyze` (short `-a`).

```sh
Usage: bsl-language-server analyze [-hq] [-c=<path>] [-o=<path>] [-s=<path>]
                                   [-r=<keys>]...
Run analysis and get diagnostic info
  -c, --configuration=<path>
                           Path to language server configuration file
  -h, --help               Show this help message and exit
  -o, --outputDir=<path>   Output report directory
  -q, --silent             Silent mode
  -r, --reporter=<keys>    Reporter key (console, junit, json, tslint, generic)
  -s, --srcDir=<path>      Source directory
  -w, --workspaceDir=<path> 
                           Workspace directory
```

To set source code folder for analysis use parameter `--srcDir` (short `-s`) followed by the path (relative or absolute) to the source code folder. 
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

To run in formatter mode use parameter `--format` (short `-f`).

```sh
Usage: bsl-language-server format [-hq] [-s=<path>]
Format files in source directory
  -h, --help            Show this help message and exit
  -q, --silent          Silent mode
  -s, --src=<path>      Source directory or file
```

To set source code folder (or source file) for formatting use parameter `--src` (short `-s`) followed by the path (relative or absolute) to the source code folder (or file).

Command line example to run formatting:

```sh
java -jar bsl-language-server.jar --format --src ./src/cf
```

<a id="configuration"></a>

## Configuration file

A detailed description of the configuration file is given on [this page](features/ConfigurationFile.md)

<a id="thanks"></a>

## Special thanks

Many thanks to all [contributors](https://github.com/1c-syntax/bsl-language-server/graphs/contributors) to the project, to all who participated in the discussions, who helped with the testing.

You are awesome!

Thanks to companies supporting open source projects, and especially to those who supported us:

---

[![YourKit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com)  

[YourKit, LLC](https://www.yourkit.com) is the creator of innovative and intelligent tools for profiling `Java` and `.NET` applications. YourKit has offered an open source license [YourKit Java Profiler](https://www.yourkit.com) for `BSL Language Server` to improve its performance.

With `YourKit Java Profiler` we profile and improve project performance.

---

[![JetBrains](assets/images/jetbrains-variant-4.png)](https://www.jetbrains.com?from=bsl-language-server)

[JetBrains](https://www.jetbrains.com?from=bsl-language-server) is the creator of professional software for development. JetBrains has offered an open source license for his products, including `IntelliJ IDEA Ultimate`.

`IntelliJ IDEA Ultimate` is one of the best tools in its class.

---

[![Digilabs](https://digilabs.ru/1c359e054740a0b75966f8c4babc239a.svg)](https://Digilabs.ru)

[Digilabs](https://digilabs.ru) - authors of `Alkir` - a software package for monitoring the performance of systems based on 1C:Enterprise 8. Digilabs provides us with server facilities for continuous performance testing of the BSL Language Server.
