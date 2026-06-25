# CLAUDE.md

Инструкции для Claude Code (и других ИИ-ассистентов) по работе с этим репозиторием.
Документ описывает, что это за проект, его архитектуру, и практические правила сборки,
тестирования и оформления изменений. Держи его в актуальном состоянии при крупных
изменениях в структуре проекта.

## Что это за проект

**BSL Language Server** — реализация [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
для языка **1С:Предприятие 8 (BSL)** и [OneScript](http://oscript.io).

Это консольное Java-приложение на Spring Boot, которое умеет работать в нескольких режимах:

- **LSP-сервер** (`lsp`, режим по умолчанию) — интеграция с редакторами по stdin/stdout.
- **WebSocket-сервер** (`websocket`) — подключение браузерных/удалённых редакторов.
- **Пакетный анализатор** (`analyze`) — статический анализ всего проекта для CI/CD с
  выгрузкой отчётов в разных форматах (SARIF, Generic issue, JSON и т.д.).
- **Форматтер** (`format`) — массовое форматирование кода / pre-commit хук.
- **MCP-сервер** (`mcp`) — предоставление возможностей анализа через Model Context Protocol.

Главная ценность проекта — движок **диагностик**: 150+ встроенных правил статического
анализа кода 1С (каталог `diagnostics/`).

Полезные ссылки:
- Сайт проекта: https://1c-syntax.github.io/bsl-language-server
- DeepWiki (автогенерированный обзор архитектуры): https://deepwiki.com/1c-syntax/bsl-language-server
- Исходники документации: [`docs/index.md`](docs/index.md) (ru), [`docs/en/index.md`](docs/en/index.md) (en)
- Руководство контрибьютора: [`docs/contributing/index.md`](docs/contributing/index.md)

## Технологический стек

- **Язык/JDK:** Java. Компиляция таргетится на **Java 21** (`sourceCompatibility`/`targetCompatibility = 21`),
  поддерживаемый рантайм — **Java 17, 21, 25** (CI собирает на 21 и 25).
- **Сборка:** Gradle (wrapper, **Gradle 9.6**, Kotlin DSL — `build.gradle.kts`).
- **Фреймворк:** Spring Boot 4 (DI, lifecycle, кэш, websocket, Spring AI MCP starters).
- **Парсер:** [`bsl-parser`](https://github.com/1c-syntax/bsl-parser) (ANTLR4) — грамматики BSL и SDBL (запросы).
- **Метаданные 1С:** [`mdclasses`](https://github.com/1c-syntax/mdclasses).
- **LSP:** Eclipse LSP4J.
- **Кэширование:** Caffeine + EhCache.
- **NLP:** JLanguageTool (`language-en`, `language-ru`) — проверка орфографии/опечаток.
- **AOP:** AspectJ (post-compile weaving, см. пакет `aop/`).
- **Прочее:** Lombok, JSpecify (nullability), Jackson, JGraphT, java-sarif, picocli (CLI).

## Архитектура верхнего уровня

Точка входа — `BSLLSPLauncher` (`Main-Class` в jar). Это picocli-приложение, которое по
имени подкоманды (`lsp` / `analyze` / `format` / `websocket` / `mcp` / `version`) поднимает
нужный Spring-контекст и делегирует работу классам из пакета `cli/`.

Ключевые абстракции (пакет `com.github._1c_syntax.bsl.languageserver`):

- **`ServerContextProvider` / `ServerContext`** (`context/`) — рабочая область (workspace).
  `ServerContext` хранит коллекцию `DocumentContext` и метаданные конфигурации 1С (через `mdclasses`).
- **`DocumentContext`** (`context/`) — состояние одного файла: AST (дерево разбора), поток токенов,
  вычисленные диагностики и метрики. Тяжёлые вычисления **ленивые** и кэшируются.
- **`DocumentChangeExecutor`** — применяет инкрементальные изменения из LSP `didChange`.
- **`BSLLanguageServer` / `BSLTextDocumentService` / `BSLWorkspaceService`** — реализация
  интерфейсов LSP4J, маршрутизация запросов протокола к провайдерам.
- **Провайдеры** (`providers/`) — реализуют отдельные возможности LSP: hover, definition,
  references, call hierarchy, rename, code actions, formatting, semantic tokens, inlay hints,
  code lens, folding и т.д. `DiagnosticProvider` поддерживает обе модели публикации —
  push (`publishDiagnostics`) и pull (`textDocument/diagnostic`).
- **Диагностики** (`diagnostics/`) — правила статического анализа (наследники `AbstractDiagnostic`).
- **Индекс ссылок** (`references/`) — межфайловый индекс символов для go-to-definition / find-references.
- **Символы** (`context/symbol/`) — извлечение символов программы из AST.
- **Граф потока управления** (`cfg/`) — построение CFG для анализа достижимости и т.п.
- **Конфигурация** (`configuration/`) — `LanguageServerConfiguration`, настройки диагностик,
  маппинг на JSON-схему (`.bsl-language-server.json`).
- **Отчёты** (`reporters/`) — форматы выгрузки для режима `analyze`.

> Для более подробного обзора см. DeepWiki (ссылка выше) — но проверяй его выводы по коду,
> это машинно-сгенерированный текст.

## Структура каталогов

```
src/main/java/.../languageserver/
  ├─ cli/            CLI-подкоманды (analyze, format, lsp, mcp, websocket, version)
  ├─ context/        ServerContext, DocumentContext, символы, метрики, CFG-вход
  ├─ providers/      провайдеры возможностей LSP
  ├─ diagnostics/    правила статического анализа (~235 файлов, 150+ правил)
  ├─ references/     индекс межфайловых ссылок
  ├─ configuration/  конфигурация сервера и диагностик
  ├─ reporters/      форматы отчётов для пакетного анализа
  ├─ codeactions/, hover/, completion/, inlayhints/, codelenses/, rename/,
  │  folding/, semantictokens/, color/, documentlink/, documenthighlight/ — фичи LSP
  ├─ cfg/            граф потока управления
  ├─ mcp/            MCP-сервер
  ├─ commands/, events/, jsonrpc/, recognizer/, infrastructure/, utils/, aop/, databind/
  └─ BSLLSPLauncher, BSLLanguageServer, BSLTextDocumentService, BSLWorkspaceService …
src/main/resources/   локализованные ресурсы диагностик (_ru/_en .properties), application*.properties
src/test/java/        тесты (JUnit 5)
src/test/resources/   фикстуры: .bsl/.os файлы, метаданные, ожидаемые результаты
src/jmh/              бенчмарки JMH
docs/                 документация (ru), docs/en/ (en), docs/contributing/ — для разработчиков
```

## Сборка и запуск

Используй **только** Gradle wrapper (`./gradlew`, на Windows `gradlew.bat`). Не требуется
устанавливать Gradle вручную.

```bash
./gradlew build          # полная сборка + проверки + тесты (долго, см. ниже)
./gradlew bootJar        # собрать исполняемый "fat" jar (classifier -exec)
./gradlew clean          # очистка
java -jar build/libs/bsl-language-server-*-exec.jar --help   # запуск собранного сервера
```

Подкоманды запуска: `lsp` (по умолчанию), `analyze`, `format`, `websocket`, `mcp`, `version`.

## Тесты и их длительность

```bash
./gradlew test                                   # весь набор тестов (МЕДЛЕННО)
./gradlew test --tests "*SomeDiagnosticTest"     # один класс — для разработки используй это
./gradlew check                                  # то, что гоняет CI: test + jacoco + spotless + javadoc
```

**Важно про длительность:** в проекте **600+ тестовых классов**, многие из которых
поднимают/перезагружают Spring-контекст (`@DirtiesContext`, `@CleanupContextBeforeClassAndAfterClass`).
Полный прогон `./gradlew test` занимает **много минут** (на CI с одним форком и `maxHeapSize=3g`
это самый долгий шаг сборки). Поэтому:

- При локальной разработке **запускай только нужный класс/тест** через `--tests`, а не весь набор.
- Не жди мгновенного завершения `./gradlew check` / `test` — закладывай время и не прерывай
  раньше, считая, что «завис».
- Тесты используют java-агенты **jmockit** и **mockito** (подключаются автоматически в `tasks.test`).
- Параллелизм — на уровне **форков JVM** (не потоков), т.к. тесты меняют общий Spring-контекст и
  статические кэши. На CI — 1 форк; локально — половина ядер (1..4). Можно переопределить:
  `./gradlew test -PmaxParallelForks=4`.
- Требуется ощутимая память: тестовой JVM выставлен `maxHeapSize = 3g`.

## UTF-8 и кириллица — читай обязательно

Проект **двуязычный** (ru/en), и в исходниках/ресурсах/тестах **много кириллицы** (код 1С
пишется кириллицей). Правила:

- **Кодировка везде UTF-8.** Компиляция, `processResources` и Sonar настроены на UTF-8
  (`options.encoding = "UTF-8"`, `filteringCharset = "UTF-8"`, `sonar.sourceEncoding = UTF-8`).
  Сохраняй любые файлы в UTF-8 **без BOM**.
- **`.properties`-файлы диагностик пиши в обычном UTF-8** (с живой кириллицей). При сборке
  Gradle сам прогоняет их через `EscapeUnicode` (замена `native2ascii` → `\uXXXX`).
  **Не** экранируй кириллицу вручную и не коммить уже экранированные `.properties`.
- **Переводы строк (EOL):** задаются в `.gitattributes`. Для большинства файлов
  (`*.java`, `*.bsl`, `*.xml`, `*.md`, `*.json`) — **LF**; для `*.bat` — **CRLF**.
  Не меняй EOL целиком в файлах при правках. Отдельные тестовые файлы помечены как `binary`
  или имеют особые правила в `.editorconfig` — не трогай их whitespace.
- При создании/правке тестовых фикстур (`.bsl`/`.os` в `src/test/resources`) сохраняй кириллицу
  как есть в UTF-8.

## Диагностики (как они устроены)

Это основная и чаще всего расширяемая часть проекта. Одна диагностика = несколько связанных файлов:

1. **Java-класс** в `src/main/java/.../diagnostics/` — наследник `AbstractDiagnostic`
   (или одного из `Abstract*Diagnostic`), с аннотацией `@DiagnosticMetadata`.
2. **Локализованные сообщения** — `XxxDiagnostic_ru.properties` и `XxxDiagnostic_en.properties`
   в `src/main/resources/.../diagnostics/`.
3. **Документация** — `docs/diagnostics/XxxDiagnostic.md` (ru) и `docs/en/diagnostics/XxxDiagnostic.md` (en).
   Она встраивается в ресурсы при сборке задачей `generateDiagnosticDocs`.
4. **Тест** — `XxxDiagnosticTest` + фикстуры в `src/test/resources/diagnostics/`.

Подавление диагностик в коде 1С: `// BSLLS:КлючДиагностики-off` / `-on`, либо `// BSLLS-off`.

Подробные пошаговые руководства уже есть в репозитории — **используй их, не выдумывай свой процесс**:
- [`docs/contributing/DiagnosticExample.md`](docs/contributing/DiagnosticExample.md) — пример с нуля
- [`docs/contributing/DiagnosticStructure.md`](docs/contributing/DiagnosticStructure.md) — структура файлов
- [`docs/contributing/DiagnostcAddSettings.md`](docs/contributing/DiagnostcAddSettings.md) — параметры
- [`docs/contributing/DiagnosticQuickFix.md`](docs/contributing/DiagnosticQuickFix.md) — quick fix
- [`docs/contributing/DiagnosticDevWorkFlow.md`](docs/contributing/DiagnosticDevWorkFlow.md) — общий workflow

## Стиль кода и проверки

- **Spotless** следит за **лицензионными заголовками** в `.java` (шаблон `license/HEADER.txt`).
  Перед коммитом при необходимости: `./gradlew spotlessApply` (или алиас `./gradlew updateLicenses`).
  `./gradlew check` упадёт, если заголовок отсутствует/устарел.
- **Lombok** активно используется — включи annotation processing (в IDE — автоматически).
- **EditorConfig** — соблюдай (`.editorconfig`). Не запускай «оптимизацию импортов» по всему
  проекту: за порядком импортов следят мейнтейнеры (см. `docs/contributing/EnvironmentSetting.md`).
- **Javadoc** проверяется с `-Xdoclint:all,-missing` — не ломай ссылки/HTML в javadoc.
- Соблюдай существующие соглашения соседних файлов (именование, аннотации, идиомы).

## Документация

- Документация живёт в `docs/` (ru) и `docs/en/` (en), собирается MkDocs
  (`mkdocs.yml`, `mkdocs.en.yml`). При изменении поведения/диагностик обновляй **обе** локали.
- Раздел для разработчиков — `docs/contributing/`. Это первоисточник по процессам разработки;
  при работе над фичами сверяйся с ним.

## Рабочий процесс (git)

- Веди разработку в отдельной ветке; не пуш в `develop`/`master` без явного запроса.
- Не создавай Pull Request, если об этом явно не попросили.
- Пиши осмысленные сообщения коммитов.
- Версия проставляется автоматически плагином git-versioning по тегам/веткам — руками версию не правь.
