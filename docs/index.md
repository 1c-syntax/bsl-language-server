# BSL Language Server

[![Actions Status](https://github.com/1c-syntax/bsl-language-server/workflows/Java%20CI/badge.svg)](https://github.com/1c-syntax/bsl-language-server/actions)
[![Download](https://img.shields.io/github/release/1c-syntax/bsl-language-server.svg?label=download&style=flat)](https://github.com/1c-syntax/bsl-language-server/releases/latest)
[![JitPack](https://jitpack.io/v/1c-syntax/bsl-language-server.svg)](https://jitpack.io/#1c-syntax/bsl-language-server)
[![GitHub Releases](https://img.shields.io/github/downloads/1c-syntax/bsl-language-server/latest/total?style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases)
[![GitHub All Releases](https://img.shields.io/github/downloads/1c-syntax/bsl-language-server/total?style=flat-square)](https://github.com/1c-syntax/bsl-language-server/releases)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=alert_status)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=1c-syntax_bsl-language-server&metric=coverage)](https://sonarcloud.io/dashboard?id=1c-syntax_bsl-language-server)
[![Benchmark](bench/benchmark.svg)](bench/index.html)
[![telegram](https://img.shields.io/badge/telegram-chat-green.svg)](https://t.me/bsl_language_server)

Реализация протокола [language server protocol](https://microsoft.github.io/language-server-protocol/) для языка 1C (BSL) - языка 1С:Предприятие 8 и [OneScript](http://oscript.io).

* [Руководство контрибьютора](contributing/index.md)
* <a href="#capabilities">Возможности</a>
* <a href="#cli">Запуск из командной строки</a>
* <a href="#analyze">Запуск в режиме анализатора</a>
* <a href="#format">Запуск в режиме форматтера</a>
* <a href="#configuration">Конфигурационный файл</a>
* <a href="reporters">Репортеры</a>
* <a href="diagnostics">Диагностики</a>
* <a href="features">Дополнительные возможности</a>
* [Часто задаваемые вопросы](faq.md)
* [Системные требования](systemRequirements.md)
* <a href="#thanks">Благодарности</a>

<a id="capabilities"></a>

Замеры производительности - [SSL 3.1](bench/index.html)

## Возможности

* Форматирование файла
* Форматирование выбранного диапазона
* Определение символов текущего файла (области, процедуры, функции, переменные, объявленные через `Перем`)
* Определение сворачиваемых областей - `#Область`, `#Если`, процедуры и функции, блоки кода
* Показ когнитивной и цикломатической сложности метода
* Диагностики
* "Быстрые исправления" (quick fixes) для ряда диагностик
* Запуск движка диагностик из командной строки
* Запуск форматирования файлов в каталоге из командной строки

## Поддерживаемые операции протокола

| Операция                                                                                                                                                      | Поддержка                                                      | Комментарий                                                  | Конфигурируется? |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- | ------------------------------------------------------------ | ---------------- |
| [workspace/didChangeWorkspaceFolders](https://microsoft.github.io/language-server-protocol/specification-current#workspace_didChangeWorkspaceFolders)         | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [workspace/didChangeConfiguration](https://microsoft.github.io/language-server-protocol/specification#workspace_didChangeConfiguration)                       | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | с ограничениями, см. [#1431](https://github.com/1c-syntax/bsl-language-server/issues/1431) |                  |
| [workspace/didChangeWatchedFiles](https://microsoft.github.io/language-server-protocol/specification#workspace_didChangeWatchedFiles)                         | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [workspace/symbol](https://microsoft.github.io/language-server-protocol/specification#workspace_symbol)                                                       | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
| [workspace/executeCommand](https://microsoft.github.io/language-server-protocol/specification#workspace_executeCommand)                                       | <img src="./assets/images/wip.svg" alt="WiP" width="20">       |                                                              |                  |
| [textDocument/didOpen](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didOpen)                       | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
| [textDocument/didChange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didChange)                   | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | см. [#1432](https://github.com/1c-syntax/bsl-language-server/issues/1432)<br />textDocumentSyncKind = Full |                  |
| [textDocument/didClose](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didClose)                     | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
| [textDocument/didSave](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didSave)                       | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
| [textDocument/willSave](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_willSave)                     | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/willSaveWaitUntil](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_willSaveWaitUntil)   | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_publishDiagnostics) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | tagSupport = true<br />versionSupport = true<br />[список диагностик](./diagnostics/index.md)              | да               |
| [textDocument/completion](https://github.com/1c-syntax/bsl-language-server/blob/develop/docs/diagnostics/index.md)                                            | <img src="./assets/images/cross.svg" alt="no" width="20">      | resolveProvider = false                                      |                  |
| [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#completionItem_resolve)                   | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/hover](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_hover)                           | <img src="./assets/images/wip.svg" alt="WiP" width="20">       | contentFormat = MarkupContent<br />см [#1405](https://github.com/1c-syntax/bsl-language-server/pull/1405)  |                  |
| [textDocument/signatureHelp](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_signatureHelp)           | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/declaration](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_declaration)               | <img src="./assets/images/cross.svg" alt="no" width="20">      | не применимо в 1С:Предприятие                                |                  |
| [textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_definition)                 | <img src="./assets/images/wip.svg" alt="WiP" width="20">       | linkSupport = true<br />см. [#1405](https://github.com/1c-syntax/bsl-language-server/pull/1405)             |                  |
| [textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_typeDefinition)         | <img src="./assets/images/cross.svg" alt="no" width="20">      | не применимо в 1С:Предприятие                                |                  |
| [textDocument/implementation](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_implementation)         | <img src="./assets/images/cross.svg" alt="no" width="20">      | не применимо в 1С:Предприятие                                |                  |
| [textDocument/references](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_references)                 | <img src="./assets/images/wip.svg" alt="WiP" width="20">       | см. [#1405](https://github.com/1c-syntax/bsl-language-server/pull/1405)                                     |                  |
| [textDocument/documentHighlight](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentHighlight)   | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/documentSymbol](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentSymbol)         | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | hierarchicalDocumentSymbolSupport = true                     |                  |
| [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_codeAction)                 | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | codeActionKinds = ? (см. [#1433](https://github.com/1c-syntax/bsl-language-server/issues/1433))<br />isPreferredSupport = false (см. [#1434](https://github.com/1c-syntax/bsl-language-server/issues/1434)) | да               |
| [textDocument/codeLens](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_codeLens)                     | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | resolveProvider = false                                      | да               |
| [codeLens/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeLens_resolve)                               | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentLink)             | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Показ гиперссылок на документацию по диагностикам.<br />tooltipSupport = true<br />resolveProvider = false | да               |
| [documentLink/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#documentLink_resolve)                       | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/documentColor](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentColor)           | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/colorPresentation](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_colorPresentation)   | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/formatting](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_formatting)                 | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
| [textDocument/rangeFormatting](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rangeFormatting)       | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
| [textDocument/onTypeFormatting](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_onTypeFormatting)     | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/rename](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rename)                         | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/prepareRename](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareRename)           | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |
| [textDocument/foldingRange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_foldingRange)             | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
| [textDocument/selectionRange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_selectionRange)         | <img src="./assets/images/cross.svg" alt="no" width="20">      |                                                              |                  |

<a id="cli"></a>

## Запуск из командной строки

Запуск jar-файлов осуществляется через `java -jar path/to/file.jar`.

```sh
java -jar bsl-language-server.jar --help

BSL language server
Usage: bsl-language-server [-h] [-c=<path>] [COMMAND [ARGS]]
  -c, --configuration=<path>
               Path to language server configuration file
  -h, --help   Show this help message and exit
Commands:
  analyze, -a, --analyze  Run analysis and get diagnostic info
  format, -f, --format    Format files in source directory
  version, -v, --version  Print version
  lsp, --lsp              LSP server mode (default)
```

При запуске BSL Language Server в обычном режиме будет запущен сам Language Server, взаимодействующий по протоколу [LSP]([language server protocol](https://microsoft.github.io/language-server-protocol/)). Для взаимодействия используются stdin и stdout.

По умолчанию тексты диагностик выдаются на русском языке. Для переключения языка сообщений от движка диагностик необходимо настроить параметр `language` в конфигурационном файле или вызвав событие `workspace/didChangeConfiguration`:

<a id="analyze"></a>

## Запуск в режиме анализатора

Для запуска в режиме анализа используется параметр `--analyze` (сокращенно `-a`). 

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

Для указания каталога расположения анализируемых исходников используется параметр `--srcDir` (сокращенно `-s`), за которым следует путь (относительный или абсолютный) к каталогу исходников. 
Для формирования отчета об анализе требуется указать один или "репортеров". Для указания репортера используется параметр `--reporter` (сокращенно `-r`), за которым следует ключ репортера. Допустимо указывать несколько репортеров. Список репортетов см. в разделе **Репортеры**.

Пример строки запуска анализа:

```sh
java -jar bsl-language-server.jar --analyze --srcDir ./src/cf --reporter json
```

> При анализе больших исходников рекомендуется дополнительно указывать параметр -Xmx, отвечающий за предел оперативной памяти для java процесса. Размер выделяемой памяти зависит от размера анализируемой кодовой базы.

```sh
java -Xmx4g -jar bsl-language-server.jar ...остальные параметры
```

<a id="format"></a>

## Запуск в режиме форматтера

Для запуска в режиме форматтера используется параметр `--format` (сокращенно `-f`).

```sh
Usage: bsl-language-server format [-hq] [-s=<path>]
Format files in source directory
  -h, --help            Show this help message and exit
  -q, --silent          Silent mode
  -s, --src=<path>      Source directory or file
```

Для указания каталога расположения форматируемых исходников (или файла) используется параметр `--src` (сокращенно `-s`), за которым следует путь (относительный или абсолютный) к каталогу исходников (или файлу).

Пример строки запуска форматирования:

```sh
java -jar bsl-language-server.jar --format --src ./src/cf
```

<a id="configuration"></a>

## Конфигурационный файл

Подробное описание конфигурационного файла приведено на [этой странице](features/ConfigurationFile.md)

<a id="thanks"></a>

## Благодарности

Огромное спасибо всем [контрибьюторам](https://github.com/1c-syntax/bsl-language-server/graphs/contributors) проекта, всем участвовавшим в обсуждениях, помогавшим с тестированием.

Вы потрясающие!  

Спасибо компаниям, поддерживающим проекты с открытым исходным кодом, а особенно тем, кто поддержали нас: 

---

[![YourKit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com)  

Создатель инновационных и интеллектуальных инструментов для профилирования приложений `Java` и `.NET` [YourKit, LLC](https://www.yourkit.com) любезно предоставил нам лицензии на продукт `YourKit Java Profiler`.

С помощью `YourKit Java Profiler` мы мониторим и улучшаем производительность проекта.

---

[![JetBrains](assets/images/jetbrains-variant-4.png)](https://www.jetbrains.com?from=bsl-language-server)  

Создатель профессиональных инструментов разработки программного обеспечения, инновационных и мощных, [JetBrains](https://www.jetbrains.com?from=bsl-language-server) поддержал наш проект, предоставив лицензии на свои продукты, в том числе на `IntelliJ IDEA Ultimate`.

`IntelliJ IDEA Ultimate` один из лучших инструментов в своем классе.

