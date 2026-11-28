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
- **Правки `CLAUDE.md`** по итогам раундов — по правилам репозитория только с согласия мейнтейнера:
  - `reporters/CLAUDE.md`: у `ReportContext` появилось поле `workspaceDir` (раунд 7);
  - `configuration/CLAUDE.md`: порядок поиска файла конфигурации теперь живёт в
    `ConfigurationFiles` (раунд 1) — стоит сослаться на него; включение supplier'а проверяет не
    `XxxConfiguration.supplierIsEnabled(...)`, а `SupplierParameters.isEnabled` (раунд 15).

## Высокий приоритет (дефекты или вероятные дефекты)

- **Нет статического анализа потокобезопасности** изменяемого состояния синглтонов (Error Prone
  или SpotBugs). Ломает сборку на существующем коде — требует решения мейнтейнера.
- **Разделители путей в отчётах**: `GenericIssueReporter`, `TSLintReporter`, `JUnitTestSuites`
  пишут разделители ОС, `CodeQualityReporter` — `/`. Смена — изменение формата вывода на Windows,
  требует решения.
- **`UnreachableCodeDiagnostic`: позиция с 1-based строкой** сравнивается с 0-based диапазонами
  (гипотеза, `diagnostics/UnreachableCodeDiagnostic.java` ~:191).
- **Поля `serverPort`/`endpointPath`/`--mcp-path` у команд не читаются** (гипотеза): значения
  берёт Spring из сырых аргументов, поля нужны picocli для справки и приёма опций. Дефолты
  `8025`/`/lsp` в описаниях опций дублируют `application.properties`.

## Дублирование реализаций (канонизация)

- **Сравнение токена/позиции с `Position`** — 5 копий; `RewriteMethodParameterDiagnostic.compare`
  — дословная копия `Ranges.compare`.
- **Ручное построение `Position` из токена** вместо `Positions.create` — 6 мест, 2 обёртки.
- **«Самый вложенный узел типа T, содержащий позицию»** и `encloses` — копии в
  `NewExpressionReferenceFinder` и `SignatureHelpProvider`.
- **Терминал, заканчивающийся на курсоре** — `LinkedEditingRangeProvider`/`SelectionRangeProvider`.
- **`safeGetAst`** — 4 приватные копии и 3 inline (две политики перехвата).
- **Таблицы «тип токена → `Keywords.MultiName`»** — 3 копии (completion, formatter, diagnostic).
- **Мелкие пары копий**: `findMethodAt` (две диагностики
  EventHandler*), `getVariableName`, `record ProfileKey`, `isIdentChar`, `stringField`, `key`.
- **Реестры supplier'ов code lens / inlay hint / command** — конфигурации, `getId()` по суффиксу,
  `extractData`, разбор data в resolve написаны трижды-четырежды.
- **Отбор включённых supplier'ов** — у code lens prototype-бин с сортировкой по `@Order`, у
  inlay hint — фильтр в провайдере без сортировки (толкование параметров уже общее, раунд 15).
- **Чтение возможностей клиента** — четыре способа (кэш в holder'е, чтение на каждый вызов,
  из `InitializeParams` события, напрямую); дубли `isVsCodeLikeClient`, понижения
  `LocationLink → Location`.
- **`BSLTextDocumentService`**: 40 копий «найти документ → дефолт → `withFreshDocumentContext`»,
  непоследовательные пустые ответы (`null` vs `emptyList()`), порядок аргументов провайдеров.
- **Нормализация URI в MCP** — несколько способов; имя workspace выводится в двух местах.
- **Источник `language`** — глобальная и workspace-настройка с тремя правилами разрешения;
  `GlobalLanguageServerConfiguration.reset()` дублирует дефолты вручную.
- **Константы**: расширения `bsl`/`os` и glob'ы — `BSLFiles` и дважды в `BSLLanguageServer`;
  footer'ы CLI с разными годами; дефолты `server.port`/`/lsp` в полях websocket-команды.
- **Дефолты опций** — в полях `*Options`, в `schema.json` и в двух локалях документации, без
  автоматической сверки (кандидат на контрактный тест).

## Мёртвый код (гипотезы, перепроверить вызывающих)

- `FormatProvider.handleEvent(LanguageServerConfigurationChangedEvent)` — пустой слушатель.
- `AbstractDiagnosticReporter.serverContextProvider` — не используется наследниками.
