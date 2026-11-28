# Пул задач упорядочивания архитектуры

Найденная архитектурная энтропия, ещё не устранённая. Пункт удаляется отсюда, когда закрыт
(запись о закрытии — в [progress.md](progress.md)). Статус: **проверено** — подтверждено чтением
кода; **гипотеза** — найдено сканированием, перед работой перепроверить.

## Ожидают подтверждения (продуктовое решение или ломающее изменение)

- **CLI: устаревший ключ `--srcDir` у `format`** (`cli/FormatCommand.java`, `TODO delete old key`).
  Удаление ломает скрипты пользователей. У `analyze` основным ключом остаётся `--srcDir`, так что
  «старый» он только для `format` — нужно решить, какой ключ каноничен для обеих команд.
- **Отчёт JSON: поле `covlocData`** (`context/MetricStorage.java`, `@Deprecated`). Входит в формат
  отчёта, который читают внешние инструменты; удаление — изменение контракта.
- **Цикл `references` ↔ `types`** (известный долг из `ArchitectureTest`). Разрыв требует переноса
  резолва конструкторов/членов платформы между подсистемами — существенное изменение архитектуры.
- **Значение `-c` различается между подкомандами**: `analyze` применяет файл к глобальной и
  workspace-конфигурации, `lsp`/`websocket`/`mcp` — только к глобальной, у `format` ключа нет
  (гипотеза). Унификация меняет поведение CLI.
- **`LanguageServerConfiguration.update` с битым файлом не бросает** (ADR-0002): утвердить или
  вернуть исключение для явного `update`, оставив терпимость только загрузке при старте.
- **Правки `CLAUDE.md`** по итогам раундов — по правилам репозитория только с согласия мейнтейнера:
  - `reporters/CLAUDE.md`: у `ReportContext` появилось поле `workspaceDir` (раунд 7);
    `AbstractDiagnosticReporter` больше не даёт доступа к `ServerContextProvider` (раунд 26);
  - `configuration/CLAUDE.md`: порядок поиска файла конфигурации теперь живёт в
    `ConfigurationFiles` (раунд 1) — стоит сослаться на него; включение supplier'а проверяет не
    `XxxConfiguration.supplierIsEnabled(...)`, а `SupplierParameters.isEnabled` (раунд 15).

## Высокий приоритет (дефекты или вероятные дефекты)

- **Нет статического анализа потокобезопасности** изменяемого состояния синглтонов (Error Prone
  или SpotBugs). Ломает сборку на существующем коде — требует решения мейнтейнера.
- **Разделители путей в отчётах**: `GenericIssueReporter`, `TSLintReporter`, `JUnitTestSuites`
  пишут разделители ОС, `CodeQualityReporter` — `/`. Смена — изменение формата вывода на Windows,
  требует решения.
- **Поля `serverPort`/`endpointPath`/`--mcp-path` у команд не читаются** (гипотеза): значения
  берёт Spring из сырых аргументов, поля нужны picocli для справки и приёма опций. Дефолты
  `8025`/`/lsp` в описаниях опций дублируют `application.properties`.

## Дублирование реализаций (канонизация)

- **Сравнение начала токена с `Position`** без выделения объекта — `KeywordCompletion.startsBefore`,
  `SignatureHelpProvider.tokenBefore`, `CodeBlockLookup.startsAfter`,
  `EventHandlerInvalidSignatureDiagnostic.isAfterPosition`, `SymbolTree.before`. Часть стоит на
  горячих путях двоичного поиска; кандидат — открыть `Ranges.isBefore(int, int, int, int)`.
- **«Самый вложенный узел типа T, содержащий позицию»** и `encloses` — копии в
  `NewExpressionReferenceFinder` и `SignatureHelpProvider`.
- **Терминал, заканчивающийся на курсоре** — `LinkedEditingRangeProvider`/`SelectionRangeProvider`.
- **`safeGetAst` с перехватом любого `RuntimeException`** (`FormExpressionInference`,
  `PropertyMethodInference`) — после раунда 25 остались две копии второй политики; сузить до
  `findAst()` можно только решением владельцев `types/` (сейчас скрываются и настоящие ошибки).
- **Рассмотрено и отклонено**: объединение `isIdentChar`/сканирования префикса идентификатора
  (`TypeService`, `CompletionProvider`) и `getVariableName` (диагностика и `utils`) — трёхстрочные
  фрагменты с разными результатами, общий хелпер дал бы лишь формальное единообразие; снятие
  кэширования `isVsCodeLikeClient` в двух code lens-классах — снимок на `initialize` осознанный и
  покрыт тестами, выигрыш ~25 строк.
- **`format` без найденных файлов** завершается с кодом 1 без сообщения.
- **Таблицы «тип токена → `Keywords.MultiName`»** — 3 копии (completion, formatter, diagnostic).
- **Мелкие пары копий**: `record ProfileKey` (`MemberMethodProfiles`, `CompletionMethodTargets`),
  `stringField` (два загрузчика JSON в `types/registry`), `key` (нижний регистр с `Locale.ROOT`).
- **Реестры supplier'ов code lens / inlay hint / command** — конфигурации (карта по id +
  регистрация подтипов Jackson) и `extractData` написаны трижды-четырежды (`getId()` уже общий,
  раунд 27).
- **Отбор включённых supplier'ов** — у code lens prototype-бин с сортировкой по `@Order`, у
  inlay hint — фильтр в провайдере без сортировки (толкование параметров уже общее, раунд 15).
- **Чтение возможностей клиента** — четыре способа (кэш в holder'е, чтение на каждый вызов,
  из `InitializeParams` события, напрямую); дубли `isVsCodeLikeClient`, понижения
  `LocationLink → Location`. Связанное: «обновить фичу на событие» реализовано в каждом
  провайдере отдельно (code lens, inlay hints, semantic tokens, diagnostics) — после раундов
  22–23 набор событий выровнен, но механизм остаётся четырьмя копиями; кандидат на общий
  компонент обновления клиента (существенное изменение — оценить отдельно).
- **Пустые ответы LSP-обработчиков непоследовательны**: при неизвестном документе часть
  списковых запросов отвечает `null` (`codeAction`, `documentSymbol`, `foldingRange`,
  `documentLink`, `formatting`…), часть — пустым списком; `definition` — `Either.forRight`,
  `implementation` — `Either.forLeft`. Унификация меняет видимое клиенту поведение — решение
  мейнтейнера. Порядок аргументов провайдеров `(params, doc)` vs `(doc, params)` различается.
- **Нормализация URI в MCP** — несколько способов; имя workspace выводится в двух местах.
- **Источник `language`** — глобальная и workspace-настройка с тремя правилами разрешения;
  `GlobalLanguageServerConfiguration.reset()` дублирует дефолты вручную.
- **Дефолты опций в документации** (`ConfigurationFile.md`, обе локали) не сверяются
  автоматически; код ↔ схема сверяет `ConfigurationSchemaDefaultsTest` (раунд 17).
