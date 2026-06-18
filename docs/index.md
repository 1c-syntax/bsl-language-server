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

Реализация протокола [language server protocol](https://microsoft.github.io/language-server-protocol/) для языка 1C (BSL) - языка 1С:Предприятие 8 и [OneScript](http://oscript.io).

* [Руководство контрибьютора](contributing/index.md)
* <a href="#capabilities">Возможности</a>
* <a href="#cli">Запуск из командной строки</a>
* <a href="#websocket">Запуск в режиме websocket</a>
* <a href="#analyze">Запуск в режиме анализатора</a>
* <a href="#format">Запуск в режиме форматтера</a>
* [Запуск в режиме MCP](features/McpMode.md)
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

Каждая возможность языкового сервера — с краткой анимированной демонстрацией (см. [полный каталог](capabilities/index.md)):

* [Автодополнение кода](capabilities/completion.md) — Контекстные подсказки по мере ввода: глобальные функции, методы и свойства объектов (с выводом типа), типы после оператора `Новый`, ключевые слова и локальные переменные.
* [Переход к определению](capabilities/definition.md) — Переход к объявлению процедуры, функции, переменной или метода из места использования. Работает внутри модуля и между модулями конфигурации.
* [Переход к реализациям](capabilities/implementation.md) — Для классов OneScript, использующих библиотеку наследования `extends`: переход от метода интерфейса (`&Интерфейс`) ко всем одноимённым методам реализующих его классов (`&Реализует`).
* [Поиск использований](capabilities/references.md) — Поиск всех мест использования символа по проекту.
* [Всплывающая документация](capabilities/hover.md) — При наведении на символ показывает сигнатуру, тип и описание из комментариев.
* [Подсказка по параметрам](capabilities/signatureHelp.md) — При вводе вызова метода показывает список параметров и подсвечивает текущий.
* [Диагностики](capabilities/diagnostics.md) — Подсветка ошибок, потенциальных проблем и нарушений стандартов прямо в коде и в панели Проблемы.
* [Быстрые исправления](capabilities/codeAction.md) — Предлагает автоматические исправления диагностик и рефакторинги по сочетанию клавиш у проблемного места.
* [Форматирование](capabilities/formatting.md) — Форматирование всего документа, выделенного фрагмента и по мере набора (отступы, регистр ключевых слов).
* [Переименование](capabilities/rename.md) — Безопасное переименование символа со всеми его использованиями.
* [Связанное редактирование](capabilities/linkedEditing.md) — Редактирование объявления локального символа (переменной, параметра) одновременно изменяет все его вхождения в модуле — без вызова переименования.
* [Структура документа](capabilities/documentSymbol.md) — Дерево процедур, функций и областей модуля — в панели Структура и в быстром переходе.
* [Поиск символов по проекту](capabilities/workspaceSymbol.md) — Быстрый переход к любому методу или объекту во всём проекте по имени.
* [Подсветка вхождений](capabilities/documentHighlight.md) — Подсветка всех вхождений символа под курсором, а также парных ключевых слов конструкции (Если…КонецЕсли и т.п.).
* [Иерархия вызовов](capabilities/callHierarchy.md) — Кто вызывает метод и кого вызывает он — в виде разворачиваемого дерева.
* [Иерархия типов](capabilities/typeHierarchy.md) — Для классов OneScript, использующих библиотеку наследования `extends`: дерево супертипов и подтипов по аннотациям `&Расширяет` и `&Реализует`.
* [Сворачивание кода](capabilities/folding.md) — Сворачивание процедур, функций, областей и блоков для удобной навигации.
* [Умное выделение](capabilities/selectionRange.md) — Пошаговое расширение и сужение выделения по синтаксическим границам.
* [Семантическая подсветка](capabilities/semanticTokens.md) — Точная подсветка на основе разбора кода: переменные, параметры, методы, аннотации, а также язык запросов (SDBL) внутри строк.
* [Подсказки-вставки](capabilities/inlayHint.md) — Встроенные в код подсказки — например, имена параметров в вызовах.
* [Код-линзы](capabilities/codeLens.md) — Информационные строки над процедурами: когнитивная и цикломатическая сложность, запуск тестов и покрытие.
* [Цвета: превью и палитра](capabilities/color.md) — Превью цвета для `Новый Цвет(...)` и `WebЦвета.*`. Клик по образцу открывает палитру — выбор цвета обновляет код. Для веб-цветов доступна конвертация в RGB-представление (`Новый Цвет`) и обратно.
* [Гиперссылки в коде](capabilities/documentLink.md) — Кликабельные ссылки прямо в тексте модуля: ссылки `См.`/`See` в документирующих комментариях ведут к упомянутому методу или объекту; URL в комментариях открываются в браузере; а опционально (по умолчанию **выключено**, `documentLink.showDiagnosticDescription`) сам диагностируемый фрагмент становится ссылкой на документацию диагностики.

Кратко (включая возможности командной строки):

* Форматирование файла
* Форматирование выбранного диапазона
* Форматирование при наборе (по Enter и `;`)
* Определение символов текущего файла (области, процедуры, функции, переменные, объявленные через `Перем`)
* Определение сворачиваемых областей - `#Область`, `#Если`, процедуры и функции, блоки кода, пакеты запросов
* Показ когнитивной и цикломатической сложности метода
* Всплывающая подсказка по локальным методам и методам общих модулей/модулей менеджеров
* Автодополнение (completion): методы, функции и конструкторы с сигнатурами, члены типов после точки, переменные, ключевые слова
* Подсказка по сигнатуре вызова (signature help)
* Подсветка парных конструкций (if/elsif/else/endif, try/except/endtry, циклы, регионы, скобки)
* Переходы к определению методов
* Поиск мест использования методов
* Иерархия вызовов методов
* Расширение текущего выделения (expand selection)
* Отображение представления цвета и конвертация между `Цвет` и `WebЦвета`
* Семантическая подсветка синтаксиса
* Линзы кода (когнитивная/цикломатическая сложность)
* Встроенные подсказки (параметры вызовов методов)
* Диагностики
* "Быстрые исправления" (quick fixes) для ряда диагностик и "быстрые действия" (code actions)
* Запуск движка диагностик из командной строки
* Запуск форматирования файлов в каталоге из командной строки
* Переименование символов
* Поддержка работы с несколькими рабочими областями (multi-workspace)

## Поддерживаемые операции протокола

??? workspace
    | Операция                                                     | Поддержка                                                    | Комментарий                                                  |
    | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
    | [didChangeWorkspaceFolders](https://microsoft.github.io/language-server-protocol/specification-current#workspace_didChangeWorkspaceFolders) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Динамическое добавление и удаление рабочих областей          |
    | [didChangeConfiguration](https://microsoft.github.io/language-server-protocol/specification#workspace_didChangeConfiguration) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | с ограничениями, см. [#1431](https://github.com/1c-syntax/bsl-language-server/issues/1431) |
    | [didChangeWatchedFiles](https://microsoft.github.io/language-server-protocol/specification#workspace_didChangeWatchedFiles) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |
    | [didCreateFiles](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didCreateFiles) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Фильтры: `**/*.bsl`, `**/*.os`, каталоги |
    | [didRenameFiles](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didRenameFiles) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Фильтры: `**/*.bsl`, `**/*.os`, каталоги |
    | [didDeleteFiles](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didDeleteFiles) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Фильтры: `**/*.bsl`, `**/*.os`, каталоги |
    | [symbol](https://microsoft.github.io/language-server-protocol/specification#workspace_symbol) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |
    | [executeCommand](https://microsoft.github.io/language-server-protocol/specification#workspace_executeCommand) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |
    | [diagnostic/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_diagnostic_refresh) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | отправляется при изменении конфигурации |
    | [applyEdit](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_applyEdit) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |
    | [willCreateFiles](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_willCreateFiles) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |

??? "Text Synchronization"
    | Операция                                                                                                                                            | Поддержка                                                      | Комментарий                                                                                |
    | --------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
    | [didOpen](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didOpen) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [didChange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didChange) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | textDocumentSyncKind = Incremental                           |                  |
    | [didClose](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didClose) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [didSave](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_didSave) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [willSave](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_willSave) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [willSaveWaitUntil](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_willSaveWaitUntil) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |

??? textDocument
    | Операция                                                     | Поддержка                                                    | Комментарий                                                  | Конфигурируется? |
    | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ---------------- |
    | [publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_publishDiagnostics) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | tagSupport = true<br />versionSupport = true<br />[список диагностик](./diagnostics/index.md) | да               |
    | [diagnostic](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_diagnostic) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | interFileDependencies = true<br />workspaceDiagnostics = false | нет               |
    | [completion](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_completion) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | resolveProvider = true<br />triggerCharacters = `.`<br />completionItem.labelDetailsSupport = true<br />Предлагает методы, функции и конструкторы с сигнатурами, члены типов после точки, локальные переменные и ключевые слова |                  |
    | [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#completionItem_resolve) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Ленивое дотягивание documentation (описание члена/функции) при поддержке клиентом |                  |
    | [hover](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_hover) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | contentFormat = MarkupContent                                |                  |
    | [signatureHelp](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_signatureHelp) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | triggerCharacters = `(`, `,`<br />retriggerCharacters = `,` |                  |
    | [declaration](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_declaration) | <img src="./assets/images/cross.svg" alt="no" width="20">    | не применимо в 1С:Предприятие                                |                  |
    | [definition](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_definition) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | linkSupport = true                                           |                  |
    | [typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_typeDefinition) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [implementation](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_implementation) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    | только для интерфейсов OneScript библиотеки extends (&Реализует) |                  |
    | [references](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_references) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> |                                                              |                  |
    | [documentHighlight](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentHighlight) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Подсветка связанных конструкций: if/elsif/else/endif, try/except/endtry, циклы, регионы, скобки |                  |
    | [documentSymbol](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentSymbol) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | hierarchicalDocumentSymbolSupport = true                     |                  |
    | [codeAction](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_codeAction) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | codeActionKinds = ? (см. [#1433](https://github.com/1c-syntax/bsl-language-server/issues/1433))<br />isPreferredSupport = true | да               |
    | [codeAction/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeAction_resolve) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [codeLens](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_codeLens) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | resolveProvider = true                                       | да               |
    | [codeLens/resolve](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeLens_resolve) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [codeLens/refresh](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeLens_refresh) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [documentLink](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentLink) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | Показ гиперссылок на документацию по диагностикам.<br />tooltipSupport = true<br />resolveProvider = false | да               |
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
    | [prepareTypeHierarchy](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareTypeHierarchy) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | только для OneScript-классов библиотеки extends              |                  |
    | [typeHierarchy/supertypes](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#typeHierarchy_supertypes) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | только для OneScript-классов библиотеки extends              |                  |
    | [typeHierarchy/subtypes](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#typeHierarchy_subtypes) | <img src="./assets/images/checkmark.svg" alt="yes" width="20"> | только для OneScript-классов библиотеки extends              |                  |
    | [semanticTokens/full](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    | multilineTokenSupport = true                                                             |                  |
    | [semanticTokens/full/delta](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [semanticTokens/range](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_semanticTokens) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [linkedEditingRange](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_linkedEditingRange) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [moniker](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_moniker) | <img src="./assets/images/cross.svg" alt="no" width="20">    |                                                              |                  |
    | [inlayHint](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    | resolveProvider = true | да |
    | [inlayHint/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#inlayHint_resolve) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |
    | [inlayHint/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_inlayHint_refresh) | <img src="./assets/images/checkmark.svg" alt="yes" width="20">    |                                                              |                  |

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
  analyze, -a, --analyze      Run analysis and get diagnostic info
  format, -f, --format        Format files in source directory
  version, -v, --version      Print version
  lsp, --lsp                  LSP server mode (default)
  websocket, -w, --websocket  Websocket server mode
```

При запуске BSL Language Server в обычном режиме будет запущен сам Language Server, взаимодействующий по протоколу [LSP]([language server protocol](https://microsoft.github.io/language-server-protocol/)). Для взаимодействия используются stdin и stdout.

Ключ `-c` (`--configuration`) задаёт путь к конфигурационному файлу. Если ключ не указан, BSL Language Server автоматически ищет файл `.bsl-language-server.json` сначала в текущем каталоге, а затем в домашнем каталоге пользователя. Подробнее — на странице [Конфигурационный файл](features/ConfigurationFile.md).

По умолчанию тексты диагностик выдаются на русском языке. Для переключения языка сообщений от движка диагностик необходимо настроить параметр `language` в конфигурационном файле или вызвав событие `workspace/didChangeConfiguration`:

```json
{
  "language": "en"
}
```

<a id="websocket"></a>

## Запуск в режиме websocket

По умолчанию взаимодействие с сервером идет через стандартные потоки ввода/вывода. 
Но вы можете запустить BSL Language Server со встроенным веб-сервером и взаимодействовать с ним через websocket.

Для этого необходимо запустить BSL Language Server с ключом `--websocket` или `-w`:

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

После запуска BSL Language Server будет доступен по адресу `ws://localhost:8025/lsp`.

Для переопределения порта к LSP-серверу необходимо использовать параметр `--server.port`, за которым следует номер желаемого порта.
Для переопределения пути к LSP-серверу необходимо использовать параметр `--app.websocket.lsp-path`, за которым следует желаемый путь, начинающийся с `/`.

Пример строки запуска BSL Language Server в режиме websocket с указанием порта 8080:

```sh
java -jar bsl-language-server.jar --websocket --server.port=8080
```

> При работе с большим проектом рекомендуется дополнительно указывать параметр -Xmx, отвечающий за предел оперативной памяти для java процесса. Размер выделяемой памяти зависит от размера анализируемой кодовой базы.

```sh
java -Xmx4g -jar bsl-language-server.jar ...остальные параметры
```

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
Для формирования отчета об анализе требуется указать один из "репортеров". Для указания репортера используется параметр `--reporter` (сокращенно `-r`), за которым следует ключ репортера. Допустимо указывать несколько репортеров. Список репортетов см. в разделе **Репортеры**.

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

[![JetBrains](assets/images/jetbrains-variant-4.png)](https://www.jetbrains.com?from=bsl-language-server)  

Создатель профессиональных инструментов разработки программного обеспечения, инновационных и мощных, [JetBrains](https://www.jetbrains.com?from=bsl-language-server) поддержал наш проект, предоставив лицензии на свои продукты, в том числе на `IntelliJ IDEA Ultimate`.

`IntelliJ IDEA Ultimate` один из лучших инструментов в своем классе.

---

[![Digilabs](https://digilabs.ru/1c359e054740a0b75966f8c4babc239a.svg)](https://Digilabs.ru)

[Digilabs](https://digilabs.ru) - авторы `Алькир`- программного комплекса по мониторингу производительности систем на базе 1С:Предприятие 8. Digilabs предоставляет нам серверные мощности для проведения постоянного тестирования производительности BSL Language Server.
