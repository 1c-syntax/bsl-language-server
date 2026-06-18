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
* [Run in MCP mode](features/McpMode.md)
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

Each language-server capability comes with a short animated demo (see the [full catalog](capabilities/index.md)):

* [Code completion](capabilities/completion.md) — Context-aware suggestions as you type: global functions, object methods and properties (with type inference), types after the `New` operator, keywords and local variables.
* [Go to definition](capabilities/definition.md) — Jump from a usage to the declaration of a procedure, function, variable or method. Works within a module and across configuration modules.
* [Go to implementations](capabilities/implementation.md) — For OneScript classes using the `extends` inheritance library: jump from an interface method (`&Интерфейс`) to the same-named methods in every implementing class (`&Реализует`).
* [Find references](capabilities/references.md) — Find all usages of a symbol across the project.
* [Quick documentation (hover)](capabilities/hover.md) — Hovering over a symbol shows its signature, type and the description from doc comments.
* [Signature help](capabilities/signatureHelp.md) — While typing a method call, shows the parameter list and highlights the active parameter.
* [Diagnostics](capabilities/diagnostics.md) — Highlights errors, potential issues and coding-standard violations inline and in the Problems panel.
* [Code actions / Quick fixes](capabilities/codeAction.md) — Offers automatic fixes for diagnostics and refactorings via a shortcut at the problem location.
* [Formatting](capabilities/formatting.md) — Format the whole document, a selection, or on-the-fly while typing (indentation, keyword casing).
* [Rename](capabilities/rename.md) — Safely rename a symbol together with all its usages.
* [Linked editing](capabilities/linkedEditing.md) — Editing the declaration of a local symbol (variable, parameter) updates all of its occurrences in the module at once — without invoking rename.
* [Document symbols / Outline](capabilities/documentSymbol.md) — A tree of the module's procedures, functions and regions — in the Outline view and quick navigation.
* [Workspace symbols](capabilities/workspaceSymbol.md) — Quickly jump to any method or object across the whole project by name.
* [Document highlight](capabilities/documentHighlight.md) — Placing the cursor on a symbol highlights all its occurrences in the current module.
* [Call hierarchy](capabilities/callHierarchy.md) — Who calls a method and what it calls — as an expandable tree.
* [Type hierarchy](capabilities/typeHierarchy.md) — For OneScript classes using the `extends` inheritance library: a tree of supertypes and subtypes derived from the `&Расширяет` and `&Реализует` annotations.
* [Code folding](capabilities/folding.md) — Collapse procedures, functions, regions and blocks for easier navigation.
* [Smart selection](capabilities/selectionRange.md) — Expand and shrink the selection step by step along syntactic boundaries.
* [Semantic highlighting](capabilities/semanticTokens.md) — Accurate highlighting based on code analysis: distinguishes variables, parameters, methods and annotations.
* [Inlay hints](capabilities/inlayHint.md) — Inline hints embedded in the code — for example, parameter names at call sites.
* [Code lens](capabilities/codeLens.md) — Informational lines above procedures: cognitive and cyclomatic complexity, test run and coverage.
* [Colors: preview and picker](capabilities/color.md) — Color preview for `Новый Цвет(...)` and `WebЦвета.*`. Clicking the swatch opens the picker — choosing a color updates the code. Web colors convert to/from the RGB constructor representation.
* [Document links (hyperlinks)](capabilities/documentLink.md) — Clickable links right in the module text: `См.`/`See` references in doc comments jump to the mentioned method or object; URLs in comments open in the browser; and optionally (**off by default**, `documentLink.showDiagnosticDescription`) the diagnosed range itself becomes a link to the diagnostic's documentation.

Briefly (including command-line features):

* File formatting
* Selected region formatting
* On-type formatting (on Enter and `;`)
* Symbol definition for current file (regions, procedures, functions, variables, defined via `Var` keyword)
* Folding regions definition `#Region`, `#If`, procedures and functions, code blocks, queries
* Methods "Cognitive Complexity" and "Cyclomatic Complexity" scores
* Tooltip on local methods and methods of common / manager modules
* Code completion: methods, functions and constructors with signatures, type members after a dot, variables, keywords
* Signature help
* Highlighting of matching constructs (if/elsif/else/endif, try/except/endtry, loops, regions, brackets)
* Go to method definitions
* Finding places to use methods
* Method call hierarchy
* Expand selection
* Display color representation and convert between `Color` and `WebColors`
* Semantic syntax highlighting
* Code lenses (cognitive/cyclomatic complexity)
* Inlay hints (method call parameters)
* Diagnostics
* Quick fixes and code actions for several diagnostics
* Run diagnostics engine from command line
* Run formatter engine from command line
* Renaming Symbols
* Multi-workspace support

## Supported protocol operations

??? workspace
    | Operation   | Support  | Comment  |
    | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
    | [didChangeWorkspaceFolders](https://microsoft.github.io/language-server-protocol/specification-current#workspace_didChangeWorkspaceFolders) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Dynamic add/remove of workspace folders                      |
    | [didChangeConfiguration](https://microsoft.github.io/language-server-protocol/specification#workspace_didChangeConfiguration) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | with restrictions see [#1431](https://github.com/1c-syntax/bsl-language-server/issues/1431) |
    | [didChangeWatchedFiles](https://microsoft.github.io/language-server-protocol/specification#workspace_didChangeWatchedFiles) |  <img src="./assets/images/checkmark.svg" alt="yes" width="20">   |                                                              |
    | [didCreateFiles](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didCreateFiles) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Filters: `**/*.bsl`, `**/*.os`, folders |
    | [didRenameFiles](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didRenameFiles) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Filters: `**/*.bsl`, `**/*.os`, folders |
    | [didDeleteFiles](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didDeleteFiles) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Filters: `**/*.bsl`, `**/*.os`, folders |
    | [symbol](https://microsoft.github.io/language-server-protocol/specification#workspace_symbol) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |
    | [executeCommand](https://microsoft.github.io/language-server-protocol/specification#workspace_executeCommand) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |
    | [diagnostic/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_diagnostic_refresh) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | sent on configuration change |
    | [applyEdit](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_applyEdit) |  <img src="./assets/images/cross.svg" alt="no" width="20">   |                                                              |
    | [willCreateFiles](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_willCreateFiles) |   <img src="./assets/images/cross.svg" alt="no" width="20">  |                                                              |

??? "Text Synchronization"
    | Operation                                                                                                                                            | Supported                                                      | Comment                                                                                |
    | --------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
    | [didOpen](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didOpen) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [didChange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didChange) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | textDocumentSyncKind = Incremental                                  |                  |
    | [didClose](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didClose) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [didSave](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didSave) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [willSave](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_willSave) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [willSaveWaitUntil](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_willSaveWaitUntil) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |

??? textDocument
    | Operation                                                     | Support                                                    | Commentary                                                  | Is configured? |
    | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ---------------- |
    | [publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_publishDiagnostics) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | tagSupport = true<br />versionSupport = true<br />[diagnostics](./diagnostics/index.md) | yes               |
    | [diagnostic](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_diagnostic) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | interFileDependencies = true<br />workspaceDiagnostics = false | no               |
    | [completion](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_completion) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | resolveProvider = true<br />triggerCharacters = `.`<br />completionItem.labelDetailsSupport = true<br />Offers methods, functions and constructors with signatures, type members after a dot, local variables and keywords |                  |
    | [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#completionItem_resolve) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Lazy documentation resolution (member/function description) when supported by the client |                  |
    | [hover](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_hover) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | contentFormat = MarkupContent                                |                  |
    | [signatureHelp](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_signatureHelp) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | triggerCharacters = `(`, `,`<br />retriggerCharacters = `,` |                  |
    | [declaration](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_declaration) | <img src="./assets/images/cross.svg" alt="no" width="20">    | not applicable in 1C:Enterprise                                |                  |
    | [definition](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_definition) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | linkSupport = true                                           |                  |
    | [typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_typeDefinition) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [implementation](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_implementation) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    | only for OneScript interfaces of the extends library (&Реализует) |                  |
    | [references](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_references) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [documentHighlight](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentHighlight) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Highlight related constructs: if/elsif/else/endif, try/except/endtry, loops, regions, brackets |                  |
    | [documentSymbol](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentSymbol) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | hierarchicalDocumentSymbolSupport = true                     |                  |
    | [codeAction](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_codeAction) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | codeActionKinds = ? (см. [#1433](https://github.com/1c-syntax/bsl-language-server/issues/1433))<br />isPreferredSupport = true | yes               |
    | [codeAction/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeAction_resolve) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [codeLens](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_codeLens) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | resolveProvider = true                                       | yes               |
    | [codeLens/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeLens_resolve) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [codeLens/refresh](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeLens_refresh) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [documentLink](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentLink) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Showing hyperlinks to documentation on diagnostics.<br />tooltipSupport = true<br />resolveProvider = false | yes               |
    | [documentLink/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#documentLink_resolve) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [documentColor](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentColor) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [colorPresentation](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_colorPresentation) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [formatting](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_formatting) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [rangeFormatting](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rangeFormatting) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [onTypeFormatting](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_onTypeFormatting) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | firstTriggerCharacter = `\n`<br />moreTriggerCharacter = `;` |                  |
    | [rename](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rename) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [prepareRename](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareRename) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [foldingRange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_foldingRange) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [selectionRange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_selectionRange) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [prepareCallHierarchy](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareCallHierarchy) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [callHierarchy/incomingCalls](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#callHierarchy_incomingCalls) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [callHierarchy/outgoingCalls](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#callHierarchy_outgoingCalls) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [prepareTypeHierarchy](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareTypeHierarchy) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | only for OneScript classes using the extends library         |                  |
    | [typeHierarchy/supertypes](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#typeHierarchy_supertypes) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | only for OneScript classes using the extends library         |                  |
    | [typeHierarchy/subtypes](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#typeHierarchy_subtypes) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | only for OneScript classes using the extends library         |                  |
    | [semanticTokens/full](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    | multilineTokenSupport = true                                                             |                  |
    | [semanticTokens/full/delta](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [semanticTokens/range](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [linkedEditingRange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_linkedEditingRange) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [moniker](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_moniker) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [inlayHint](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    | resolveProvider = true | yes |
    | [inlayHint/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#inlayHint_resolve) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
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

The `-c` (`--configuration`) flag specifies the path to a configuration file. If not provided, BSL Language Server automatically searches for `.bsl-language-server.json` first in the current working directory, then in the user's home directory. See [Configuration file](features/ConfigurationFile.md) for details.

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

[![JetBrains](assets/images/jetbrains-variant-4.png)](https://www.jetbrains.com?from=bsl-language-server)

[JetBrains](https://www.jetbrains.com?from=bsl-language-server) is the creator of professional software for development. JetBrains has offered an open source license for his products, including `IntelliJ IDEA Ultimate`.

`IntelliJ IDEA Ultimate` is one of the best tools in its class.

---

[![Digilabs](https://digilabs.ru/1c359e054740a0b75966f8c4babc239a.svg)](https://Digilabs.ru)

[Digilabs](https://digilabs.ru) - authors of `Alkir` - a software package for monitoring the performance of systems based on 1C:Enterprise 8. Digilabs provides us with server facilities for continuous performance testing of the BSL Language Server.
