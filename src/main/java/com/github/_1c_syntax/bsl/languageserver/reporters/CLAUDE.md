<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# reporters/ — отчёты режима `analyze`

Выгрузка результатов пакетного анализа (подкоманда `analyze`) в разные форматы. Подключаемый
плагин-фреймворк: один формат = один Spring-бин. См. корневой
[CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Контракт

- **`DiagnosticReporter`** — интерфейс: `key()` (уникальный ключ формата) + `report(AnalysisInfo, Path)`.
- **`AbstractDiagnosticReporter`** — база с доступом к `ServerContextProvider` и `DiagnosticInfos`.
- Выбор форматов: CLI `analyze --reporter <key>` → бин `filteredReporters`
  (`cli/ReporterSelectionConfiguration`, зависит от `AnalyzeCommand`) фильтрует все бины
  `DiagnosticReporter` по ключам → `ReportersAggregator.report()` вызывает каждый активный.

## Форматы (ключ → файл)

`json` (`JsonReporter`) · `junit` (`JUnitReporter`, JUnit XML) · `console` (`ConsoleReporter`) ·
`generic` (`GenericIssueReporter`, Generic Issue Import для SonarQube) ·
`code-quality` (`CodeQualityReporter`, GitLab Code Quality) · `sarif` (`SarifReporter`, SARIF 2.1.0) ·
`tslint` (`TSLintReporter`).

## Модель данных

- **`AnalysisInfo`** (record) — `date`, `fileinfos: List<FileInfo>`, `sourceDir`.
- **`FileInfo`** — путь файла, `mdoRef`, список диагностик, `MetricStorage`; строится из `DocumentContext`.
- `data/` — записи модели; `databind/` — Jackson-хелперы сериализации (`AnalysisInfoJsonMapper`,
  сериализаторы `DiagnosticCode`/`DiagnosticMessage`, `DiagnosticMixIn`).

## Правки в этом каталоге

- Новый формат отчёта = новый бин `DiagnosticReporter` со своим `key()` (наследуй
  `AbstractDiagnosticReporter`, если нужен доступ к контексту/инфо диагностик). Регистрировать
  вручную в аггрегаторе не нужно — он подхватывается по бину и ключу.
- Имя ключа стабильно (используется в CLI и интеграциях) — не переименовывай без причины.
