# BSL Language Server

[![Actions Status](https://github.com/1c-syntax/bsl-language-server/workflows/Java%20CI/badge.svg)](https://github.com/1c-syntax/bsl-language-server/actions)
[![Download](https://img.shields.io/github/release/1c-syntax/bsl-language-server.svg?label=download&style=flat)](https://github.com/1c-syntax/bsl-language-server/releases/latest)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![GitHub Releases](https://img.shields.io/github/downloads/1c-syntax/bsl-language-server/latest/total?style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases)
[![GitHub All Releases](https://img.shields.io/github/downloads/1c-syntax/bsl-language-server/total?style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Benchmark](bench/benchmark.svg)](../bench/index.html)
[![telegram](https://img.shields.io/badge/telegram-chat-green.svg)](https://t.me/bsl_language_server)

[Language Server Protocol](https://microsoft.github.io/language-server-protocol/) implementation for 1C (BSL) - 1C:Enterprise 8 and [OneScript](http://oscript.io) languages.

- [Contributing guidelines](contributing/index.md)
- <a href="#capabilities">Capabilities</a>
- <a href="#cli">Run from command line</a>
- <a href="#analyze">Run in analyzer mode</a>
- <a href="#format">Run in formatter mode</a>
- <a href="#configuration">Configuration file</a>
- <a href="reporters">Reporters</a>
- <a href="diagnostics">Diagnostics</a>
- <a href="features">Features</a>
- [Frequently asked questions](faq.md)
- [System requirements](systemRequirements.md)
- <a href="#thanks">Special thanks</a>

<a id="capabilities"></a>

Perfomance measurement - [SSL 3.1](bench/index.html)

## Capabilities

- File formatting
- Selected region formatting
- Symbol definition for current file (regions, procedures, functions, variables, defined via `Var` keyword)
- Folding regions definition `#Region`, `#If`, procedures and functions, code blocks
- Methods "Cognitive Complexity" and "Cyclomatic Complexity" scores
- Diagnostics
- Quick fixes for several diagnostics
- Run diagnostics engine from command line
- Run formatter engine from command line

<a id="cli"></a>

## Run from command line

jar-files run through `java -jar path/to/file.jar`.

```sh
java -jar bsl-language-server.jar --help  BSL language server Usage: bsl-language-server [-h] [-c=<path>] [COMMAND [ARGS]]   -c, --configuration=<path>                Path to language server configuration file   -h, --help   Show this help message and exit Commands:   analyze, -a, --analyze  Run analysis and get diagnostic info   format, -f, --format    Format files in source directory   version, -v, --version  Print version   lsp, --lsp              LSP server mode (default)
```

Starting BSL Language Server in standard mode will run the Language Server communicating via [LSP]([language server protocol](https://microsoft.github.io/language-server-protocol/)). stdin and stdout are used for communication.

By default diagnostics texts are displayed in Russian. To switch the diagnostics text language you need to set parameter `language` in configuration file or raise an event `workspace/didChangeConfiguration`:

<a id="analyze"></a>

## Run in analyzer mode

To run in analyzer mode use parameter `--analyze` (short `-a`).

```sh
Usage: bsl-language-server analyze [-hq] [-c=<path>] [-o=<path>] [-s=<path>]                                    [-r=<keys>]... Run analysis and get diagnostic info   -c, --configuration=<path>                            Path to language server configuration file   -h, --help               Show this help message and exit   -o, --outputDir=<path>   Output report directory   -q, --silent             Silent mode   -r, --reporter=<keys>    Reporter key (console, junit, json, tslint, generic)   -s, --srcDir=<path>      Source directory   -w, --workspaceDir=<path>                             Workspace directory
```

To set source code folder for analysis use parameter `--srcDir` (short `-s`) followed by the path (relative or absolute) to the source code folder. To generate an analysis report you need to specify one or more reporters. To specify reporter use parameter `--reporter` or `-r`, followed by reporter key. You may specify several reporters. The list of reporters see in section  **Reporters**.

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
Usage: bsl-language-server format [-hq] [-s=<path>] Format files in source directory   -h, --help            Show this help message and exit   -q, --silent          Silent mode   -s, --srcDir=<path>   Source directory
```

To set source code folder (or source file) for formatting use parameter `--src` (short `-s`) followed by the path (relative or absolute) to the source code folder (or file).

Command line example to run formatting:

```sh
java -jar bsl-language-server.jar --format --src ./src/cf
```

<a id="configuration"></a>

## Configuration file

A detailed description of the configuration file is given on [this page](features/ConfigurationFile.md)

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
