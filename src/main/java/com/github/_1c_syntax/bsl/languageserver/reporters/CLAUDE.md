<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# reporters/ — отчёты режима `analyze`

Выгрузка результатов пакетного анализа (подкоманда `analyze`) в разные форматы. Подключаемый
плагин-фреймворк: один формат = один Spring-бин. См. корневой
[CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Контракт

Отчёт формируется **потоково**: результат каждого файла записывается сразу после его разбора и
не удерживается в памяти (см. issue #4412). Накапливать `FileInfo` нельзя; если формату нужны
сводные данные — копить надо агрегаты.

- **`DiagnosticReporter`** — `key()` + жизненный цикл `beginReport(ReportContext, Path)` →
  `accept(FileInfo)`×N → ровно один из `endReport()` / `abortReport()`.
  Все вызовы последовательны и из одного потока, синхронизация не нужна.
  Порядок файлов — порядок завершения разбора, недетерминирован.
- **`AbstractDiagnosticReporter`** — база с доступом к `ServerContextProvider` и `DiagnosticInfos`.
- **`ReportFile`** — файл отчёта «всё или ничего»: без `commit()` удаляется при закрытии.
  Используй его вместо ручного `FileOutputStream`, иначе при сбое останется обрезанный отчёт.
- **`ReportSession`** (создаётся `ReportersAggregator.beginReport()`) — раздаёт результаты активным
  репортёрам. Запись идёт в отдельном потоке через `ReportWriteQueue`: анализ только ставит
  задачу в очередь и не ждёт ввода-вывода.
- Выбор форматов: CLI `analyze --reporter <key>` → бин `filteredReporters`
  (`cli/ReporterSelectionConfiguration`, зависит от `AnalyzeCommand`) фильтрует все бины
  `DiagnosticReporter` по ключам.

## Форматы (ключ → файл)

`json` (`JsonReporter`) · `junit` (`JUnitReporter`, JUnit XML) · `console` (`ConsoleReporter`) ·
`generic` (`GenericIssueReporter`, Generic Issue Import для SonarQube) ·
`code-quality` (`CodeQualityReporter`, GitLab Code Quality) · `sarif` (`SarifReporter`, SARIF 2.1.0) ·
`tslint` (`TSLintReporter`).

## Модель данных

- **`ReportContext`** (record) — `date`, `sourceDir`: «шапка» отчёта, всё, что известно до анализа.
- **`FileInfo`** — путь файла, `mdoRef`, список диагностик, `MetricStorage`; строится из `DocumentContext`.
- **`AnalysisInfo`** (record) — модель **чтения** формата `bsl-json.json`; в записи не участвует.
- `data/` — записи модели; `databind/` — Jackson-хелперы сериализации (`AnalysisInfoJsonMapper`,
  сериализаторы `DiagnosticCode`/`DiagnosticMessage`, `DiagnosticMixIn`).

## Правки в этом каталоге

- Новый формат отчёта = новый бин `DiagnosticReporter` со своим `key()` (наследуй
  `AbstractDiagnosticReporter`, если нужен доступ к контексту/инфо диагностик). Регистрировать
  вручную в аггрегаторе не нужно — он подхватывается по бину и ключу.
- Пиши через `SequenceWriter` (`writerFor(Тип.class).writeValuesAsArray(...)`): для массива верхнего
  уровня он заменяет генератор целиком, для вложенного — принимает уже открытый генератор и при
  своём `close()` его не закрывает. XML (`junit`) — только `ToXmlGenerator`.
- Формат файлов зафиксирован побайтово в `ReporterOutputFormatTest`. Если тест упал — вывод
  изменился; это ломает интеграции, а не просто тест.
- Имя ключа стабильно (используется в CLI и интеграциях) — не переименовывай без причины.
