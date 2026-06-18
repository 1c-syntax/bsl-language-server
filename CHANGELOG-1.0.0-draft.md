# v1.0.0

> Черновик описания релиза. Изменения охватывают период между `v0.29.0` и текущим `develop`
> (≈420 коммитов, ≈100 объединённых pull request'ов).

Версия 1.0.0 — рубежный релиз. Основой стала **полностью новая система типов**, на которой
переосмыслены семантические возможности сервера: вывод типов, всплывающие подсказки,
автодополнение, подсказки по параметрам, семантическая подсветка и целое семейство новых
диагностик. Существенно расширена поддержка протокола LSP — добавлены автодополнение, подсказка по
параметрам, иерархия типов и другие запросы (вплоть до возможностей LSP 3.18). Добавлен
экспериментальный режим работы в качестве **MCP-сервера** и собран полноценный **каталог
возможностей** в документации.

## Новая система типов

Сердце релиза — заново спроектированная система типов (model, registry, inferencer, `TypeService`).
Это единый слой, который знает обо всех типах проекта и умеет выводить тип выражения в произвольной
точке кода. На нём построены ховер, автодополнение, подсказки по параметрам, семантическая подсветка
и новые диагностики.

* **Единая модель и реестр типов.** Типы собираются из нескольких источников в общий реестр:
  * платформенные типы 1С:Предприятие (через `bsl-context`), включая синтакс-помощник;
  * типы конфигурации (через `MDClasses`) — менеджеры объектов, реквизиты и т.п.;
  * типы OneScript из синтакс-помощника OneScript 2.1, а также пользовательские OScript-классы.
* **Вывод типов выражений (inference).** Сервер определяет тип переменной/выражения под курсором,
  в том числе по присваиванию, по возвращаемому значению функций (включая межмодульный вывод) и по
  полям структур, формируемых функцией-конструктором. Результаты инференса кэшируются.
* **Билингвальность.** Полноценная поддержка русских и английских имён типов и членов: двуязычное
  отображение в ховере, автодополнении и подсказках по параметрам; классификация смешанных имён;
  показ информации на «своём» языке символа (BSL/OneScript).
* **Глобальный контекст** представлен синтетическим типом `ГлобальныйКонтекст` — глобальные функции
  и свойства резолвятся в его членах.
* **Конструкторы, async и вариадик-параметры.** Отдельный `ConstructorSymbol` для конструктора
  OScript-класса; учёт модификатора `Асинх`/async у объявлений и вызовов методов; поддержка
  вариадик-параметров и необязательных параметров со значениями по умолчанию.
* **Поддержка фреймворка «ОСень» (Autumn).** Вывод типов внедряемых зависимостей (DI):
  * аннотации внедрения (`&Лог`, `&Контроллер`, killjoy и пр.);
  * `&ПсевдонимДля` (`AliasFor`) при разрешении типов;
  * коллекции `autumn-collections` и регистрация `&Обходимое`-классов OneScript как коллекций;
  * двунаправленные код-линзы навигации по бинам «ОСени».
* **Семантическая подсветка на базе типов.** Платформенные члены подсвечиваются как члены платформы
  (методы через `accessCall`, свойства через `accessProperty`), с async-модификатором; имена общих
  модулей и конструкторы классов после `Новый` подсвечиваются корректно; собственные методы и
  реквизиты конфигурации не окрашиваются как `defaultLibrary`. Поддержана подсветка лямбд внутри
  строковых литералов.

## Поддержка протокола LSP

Добавлен **каталог возможностей** в документации (`docs/capabilities`). Сама поддержка протокола
существенно расширена — реализованы новые запросы и улучшены существующие, вплоть до возможностей
LSP 3.17/3.18.

### Новые обрабатываемые запросы

* Добавлена обработка запроса [`textDocument/completion`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_completion) и [`completionItem/resolve`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#completionItem_resolve) — автодополнение кода: глобальные функции, методы и свойства объектов (с выводом типа), типы после `Новый`, ключевые слова и локальные переменные; нечёткий поиск (подстрока и подпоследовательность), ранжирование через `sortText`, сигнатура и тип в `labelDetails` (LSP 3.17), `commitCharacters`, отложенная документация через resolve, размещение курсора после скобок.
* Добавлена обработка запроса [`textDocument/signatureHelp`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_signatureHelp) — подсказка по параметрам вызываемого метода с подсветкой текущего параметра и учётом клиентских возможностей (`labelOffset`, `documentationFormat`, контекст retrigger).
* Добавлена обработка запроса [`inlayHint/resolve`](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#inlayHint_resolve) — отложенное наполнение подсказки-вставки (в v0.29.0 не поддерживался).
* Добавлена обработка запроса [`textDocument/implementation`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_implementation) — переход к реализациям метода интерфейса (`&Интерфейс` → `&Реализует`) для классов OneScript на библиотеке наследования `extends`.
* Добавлена обработка запросов [`textDocument/prepareTypeHierarchy`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareTypeHierarchy), [`typeHierarchy/supertypes`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#typeHierarchy_supertypes) и [`typeHierarchy/subtypes`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#typeHierarchy_subtypes) — иерархия супертипов и подтипов OneScript-классов по `&Расширяет`/`&Реализует` (с `SymbolKind.Interface` для интерфейсов).
* Добавлена обработка запроса [`textDocument/linkedEditingRange`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_linkedEditingRange) — одновременное редактирование объявления локального символа и всех его вхождений без вызова переименования.
* Добавлена обработка запроса [`textDocument/onTypeFormatting`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_onTypeFormatting) — форматирование по мере набора (флаг `useOnTypeFormatting`).
* Добавлена обработка запроса [`textDocument/rangesFormatting`](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#textDocument_rangesFormatting) — форматирование нескольких диапазонов (LSP 3.18).
* Добавлена обработка запроса [`textDocument/prepareRename`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareRename) — подготовка переименования с `PrepareRenameResult` и placeholder.
* Объявлена поддержка нескольких рабочих пространств — [`workspace/didChangeWorkspaceFolders`](https://microsoft.github.io/language-server-protocol/specification-current#workspace_didChangeWorkspaceFolders) (`workspaceFolders.supported` и `changeNotifications`): динамическое добавление и удаление workspace folders (см. раздел «Прочие новые возможности»).
* Добавлена поддержка операций над файлами [`workspace/didCreateFiles`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didCreateFiles), [`workspace/didRenameFiles`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didRenameFiles), [`workspace/didDeleteFiles`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_didDeleteFiles) (фильтры `**/*.bsl`, `**/*.os`, каталоги) и динамическая регистрация наблюдателей за файлами ([`didChangeWatchedFiles`](https://microsoft.github.io/language-server-protocol/specification#workspace_didChangeWatchedFiles)) через `RelativePattern`.
* Явно декларируется [`positionEncoding`](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#positionEncodingKind) = `utf-16`.

### Улучшения существующих запросов

* [`textDocument/inlayHint`](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint) — новые виды подсказок-вставок: имена параметров платформенных методов и конструкторов (`Новый Класс()`), выводимые типы переменных, значения по умолчанию пропущенных аргументов; кликабельные части подписи (`LabelPart`).
* [`textDocument/foldingRange`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_foldingRange) — сворачивание ветвей `ИначеЕсли`/`Иначе` и блока `Исключение`, блоков `#Вставка`/`#Удаление` расширений, осмысленный `collapsedText`, соблюдение клиентского `rangeLimit`.
* [`textDocument/documentLink`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentLink) — кликабельные ссылки `См.`/`See` к упомянутому методу/объекту и открытие http(s)-ссылок из комментариев.
* [`textDocument/documentHighlight`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentHighlight) — подсветка вхождений идентификаторов с видом Read/Write, `kind=Text` для парных лексем.
* [`textDocument/documentSymbol`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_documentSymbol) — `detail` с сигнатурой параметров метода.
* [`workspace/symbol`](https://microsoft.github.io/language-server-protocol/specification#workspace_symbol) — ранжированный символьный индекс, заполнение `containerName`, отмена запроса через `CancelChecker`, безопасный откат на буквальный поиск при невалидном regex.
* [`textDocument/codeAction`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_codeAction) — поддержка `source.fixAll` (автопочинка при сохранении) и учёт `context.triggerKind`.
* [`textDocument/rename`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rename) — `WorkspaceEdit` на `documentChanges` и `ChangeAnnotation`, валидация нового имени, защита от переименования символов-модулей.
* [`textDocument/formatting`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_formatting) — поддержка `insertFinalNewline` и `trimFinalNewlines`.
* [`textDocument/references`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_references) — учёт `context.includeDeclaration`.
* [`textDocument/definition`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_definition) — учёт клиентской возможности `linkSupport` (`LocationLink`).
* [`textDocument/prepareCallHierarchy`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareCallHierarchy) — раскрытие узла кода модуля в иерархии вызовов.
* [`textDocument/hover`](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_hover) — показ выводимого типа переменной/выражения, признака необязательности параметра («?»), состава возвращаемых структур и признака устаревания методов из исходников.

## Режим MCP (экспериментально)

Сервер умеет работать как сервер [Model Context Protocol (MCP)](https://modelcontextprotocol.io/),
открывая возможности анализа кода 1С (BSL) и OneScript AI-агентам. Инструменты MCP работают поверх
того же движка, что и LSP-режим. Рабочие пространства задаются через
[MCP roots](https://modelcontextprotocol.io/docs/concepts/roots) — прямой аналог workspace folders.

* **Режимы запуска:** отдельный MCP-сервер (команда `mcp` с транспортом `stdio`/`sse`/`streamable`)
  либо совместно с LSP по флагу `--mcp` (рядом с `stdio`- или `websocket`-LSP, по Streamable HTTP).
* **Инструменты:** `analyze_file`, `document_symbols`, `find_references`, `call_hierarchy`, `hover`,
  `definition`, `type_info` (свойства и методы типа по имени), `type_at_position` (выведенный тип
  выражения под курсором), `global_member_info`. Поддержан параметр `fileType` (BSL/OS).
* Основан на Spring AI 2.0; API и поведение могут меняться.

## Новые диагностики

На основе системы типов:

* [`UnknownMember`](https://1c-syntax.github.io/bsl-language-server/diagnostics/UnknownMember) — обращение к неизвестному методу или свойству.
* [`UnavailableMemberCall`](https://1c-syntax.github.io/bsl-language-server/diagnostics/UnavailableMemberCall) — использование метода/свойства, недоступного в целевой версии платформы.
* [`AssignToReadOnlyProperty`](https://1c-syntax.github.io/bsl-language-server/diagnostics/AssignToReadOnlyProperty) — присвоение значения свойству, доступному только для чтения.

Прочие:

* [`CommonModuleVariables`](https://1c-syntax.github.io/bsl-language-server/diagnostics/CommonModuleVariables) — объявление переменных (`Перем`) в общем модуле (Issue #3854).
* [`CompareWithBoolean`](https://1c-syntax.github.io/bsl-language-server/diagnostics/CompareWithBoolean) — сравнение с булевой константой (Issue #696).
* [`BadExceptionCategory`](https://1c-syntax.github.io/bsl-language-server/diagnostics/BadExceptionCategory) — недопустимая категория исключений в `ВызватьИсключение` (Issue #1935).
* [`EventHandlerInvalidSignature`](https://1c-syntax.github.io/bsl-language-server/diagnostics/EventHandlerInvalidSignature) — несоответствие сигнатуры обработчика платформенного события (с быстрыми исправлениями).
* [`EventHandlerOutsideEventRegion`](https://1c-syntax.github.io/bsl-language-server/diagnostics/EventHandlerOutsideEventRegion) — обработчик события вне стандартной области (с быстрыми исправлениями).

Механизм устаревания и недоступности платформенных членов переведён на data-driven модель: вместо
жёстко зашитых диагностик `DeprecatedMethods8310`/`DeprecatedMethods8317` устаревание и доступность
членов теперь определяются по версии платформы из данных типов.

## Прочие новые возможности

* **Поддержка нескольких рабочих пространств (multi-workspace)** с отдельной
  `LanguageServerConfiguration` на каждое рабочее пространство.
* **Исключение путей** из анализа.
* **Фильтрация диагностик по авторству Git (`ingoredAuthors`)** — замечания скрываются для строк,
  изменённых указанными авторами (по данным git blame).
* **Виртуальные потоки** для исполнителей LSP-запросов.
* Обновлены данные синтакс-помощника OneScript до OneScript 2.1; автодополнение видит соседей по
  своему пакету без `#Использовать`; добавлены описания встроенных ключевых слов oscript.
* Поддержка нового объекта метаданных «Цвет палитры» (`ЦветПалитры`).

## Производительность

* Кэш инференса типов и мемоизация получения членов; индекс вызовов для ускорения провайдеров.
* Устранён квадратичный обход AST в поставщике inlay-hint вызовов методов.
* Параллельное выполнение тестов JUnit; единая инициализация серверного контекста в «горячих» тестах.

## Исправлены общие ошибки

* Устранено ложное срабатывание `ServerCallsInFormEvents` на директиву `&НаСервереБезКонтекста`
  (Issue #3852).
* Устранено ложное срабатывание `MissingCommonModuleMethod` на цепочках вызовов через общий модуль.
* Устранено ложное срабатывание `MissingTemporaryFileDeletion` при асинхронном удалении файлов
  (Issue #3260).
* Исправлена работа `VirtualTableCallWithoutParameters` на `КритерийОтбора`.
* Устранены ложные срабатывания диагностик метаданных на объектах, заблокированных поддержкой.
* Исправлены падения при построении графа потока управления (CFG): `IllegalArgumentException` на
  «висячей» директиве препроцессора перед циклом и сбой при директивах препроцессора, пересекающих
  границы `ИначеЕсли`.
* Устранено ложное срабатывание `QueryParseError` на запросе с функцией `СТРОКА`.
* Исправлен `StringIndexOutOfBoundsException` в `QueryComputer` для соседних строковых токенов после
  разрыва строки.
* Исправлен `NullPointerException` в `WebColorInformationSupplier` при неизвестном имени цвета.
* `ColorProvider` больше не показывает ложный чёрный образец цвета для нелитеральных аргументов конструктора `Цвет`.
* Пропускаются объявления переменных без имени при построении структуры документа.
* Починен поиск ссылок на общий модуль в `ReferenceIndex.getReferencesTo`.
* Корректная обработка destruction callbacks в `WorkspaceBeanScope`.
* В Sentry: устойчивость к `null`-версии сервера при инициализации и распознавание pre-release версий
  вида `rc.N`.

## Обновления значимых зависимостей

Библиотеки 1c-syntax:

* `io.github.1c-syntax:bsl-parser`: 0.32.0 → 0.36.0;
* `io.github.1c-syntax:mdclasses`: 0.18.0 → 0.19.1;
* `io.github.1c-syntax:bsl-context`: новая зависимость, 0.7.0;
* `io.github.1c-syntax:bsl-common-library`: 0.10.0 → 0.11.0;
* `io.github.1c-syntax:utils`: 0.7.0 → 0.7.2.

Внешние библиотеки:

* `org.eclipse.lsp4j` (lsp4j core + websocket.jakarta): 0.24.0 → 1.0.0;
* `org.springframework.boot`: 4.0.1 → 4.1.0;
* `org.springframework.ai:spring-ai-bom`: новая зависимость, 2.0.0 (режим MCP);
* `org.eclipse.jgit`: 7.3.0 → 7.7.0 (фильтрация диагностик по git blame);
* `com.github.ben-manes.caffeine:caffeine`: 3.2.3 → 3.2.4;
* `org.ehcache:ehcache`: 3.11.1 → 3.12.0;
* `org.jgrapht:jgrapht-core`: 1.5.2 → 1.5.3;
* `commons-io:commons-io`: 2.21.0 → 2.22.0;
* `commons-codec:commons-codec`: 1.21.0 → 1.22.0;
* `com.google.guava:guava`: 33.5.0-jre → 33.6.0-jre;
* LanguageTool: 6.7 → 6.8.

Сборка и инструменты:

* `io.sentry.jvm.gradle`: 6.2.0 → 6.12.0;
* `org.springframework.boot` (Gradle-плагин): 4.0.1 → 4.1.0;
* `io.freefair.*` (lombok, javadoc-links, javadoc-utf-8, aspectj): 9.2.0 → 9.5.0;
* `org.sonarqube`: 7.2.3.7755 → 7.3.1.8318;
* `com.gorylenko.gradle-git-properties`: 2.5.7 → 4.0.1;
* `org.jreleaser`: 1.23.0 → 1.24.0;
* `com.github.ben-manes.versions`: 0.53.0 → 0.54.0;
* `com.github.hazendaz.jmockit:jmockit`: 2.1.0 → 2.2.0;
* проект переведён на JDK 21 как базовую версию Java; обновлены `actions/checkout`, `actions/setup-java`, `gradle/actions` и другие GitHub Actions.

## Спасибо!

* @nixel2007
* @theshadowco
* @sfaqer
* @erprivalov
* @johnnyshut

**Full Changelog**: https://github.com/1c-syntax/bsl-language-server/compare/v0.29.0...v1.0.0
