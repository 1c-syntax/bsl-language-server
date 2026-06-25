<!--
Заметка для мейнтейнеров (этот блок вырезается из контекста Claude и не тратит токены):
- Держи файл компактным: цель — < 200 строк (рекомендация Anthropic: чем короче, тем стабильнее следование).
- Не дублируй сюда docs/contributing — давай ссылки, агент прочитает их по требованию.
- Ссылки на docs делай обычными markdown-ссылками, НЕ через @import: @import грузит файл в контекст
  целиком при старте и раздувает его. Обычная ссылка читается агентом только при необходимости.
- Узкие правила для отдельных каталогов лучше класть в nested CLAUDE.md (например, diagnostics/CLAUDE.md):
  они подгружаются лениво, когда агент работает с файлами в этом каталоге.
-->

# CLAUDE.md

Инструкции для Claude Code по работе с этим репозиторием: что это за проект, его архитектура,
и правила сборки/тестов/оформления. Это краткая карта со ссылками на первоисточники, а не их копия.

## Поддержание файла в актуальности

**Если обнаружишь расхождение между написанным здесь и реальным состоянием репозитория**
(другие команды, изменилась структура/версии/процесс) — не молчи: сообщи пользователю о
несоответствии и **предложи обновить соответствующий `CLAUDE.md`** (корневой или вложенный).
Применяй правку только с согласия пользователя. То же касается вложенных `CLAUDE.md` в подкаталогах.

## Что это за проект

**BSL Language Server** — реализация [LSP](https://microsoft.github.io/language-server-protocol/)
для языка **1С:Предприятие 8 (BSL)** и [OneScript](http://oscript.io). Консольное Java-приложение
на Spring Boot. Главная ценность — движок **диагностик** (150+ правил статического анализа кода 1С).

Режимы работы (подкоманды): `lsp` (по умолчанию, stdin/stdout) · `analyze` (пакетный анализ для CI,
отчёты SARIF/Generic/JSON) · `format` (форматтер) · `websocket` · `mcp` (Model Context Protocol) · `version`.

Ссылки: [сайт](https://1c-syntax.github.io/bsl-language-server) ·
[DeepWiki — машинный обзор архитектуры](https://deepwiki.com/1c-syntax/bsl-language-server) (проверяй по коду) ·
[docs/index.md](docs/index.md) (ru) · [docs/en/index.md](docs/en/index.md) (en) ·
[руководство контрибьютора](docs/contributing/index.md).

## Стек

- **Java**, компиляция таргетится на **Java 21**; поддерживаемый рантайм — 17/21/25 (CI: 21 и 25).
- **Gradle 9.6** (wrapper, Kotlin DSL — `build.gradle.kts`); **Spring Boot 4** (DI, кэш, websocket, MCP).
- Парсер [`bsl-parser`](https://github.com/1c-syntax/bsl-parser) (ANTLR4, грамматики BSL и SDBL-запросов);
  метаданные 1С — [`mdclasses`](https://github.com/1c-syntax/mdclasses); LSP — Eclipse LSP4J.
- Кэш Caffeine + EhCache · NLP JLanguageTool (ru/en) · AspectJ (AOP) · Lombok · JSpecify · picocli (CLI).

## Архитектура

Точка входа — `BSLLSPLauncher` (picocli; по подкоманде поднимает Spring-контекст, делегирует в `cli/`).
Ключевые абстракции (пакет `com.github._1c_syntax.bsl.languageserver`):

- **`ServerContext` / `ServerContextProvider`** (`context/`) — рабочая область: коллекция документов +
  метаданные конфигурации 1С.
- **`DocumentContext`** (`context/`) — состояние одного файла: AST, токены, диагностики, метрики.
  Тяжёлые вычисления **ленивые** и кэшируются. `DocumentChangeExecutor` применяет инкрементальные `didChange`.
- **`BSLLanguageServer` / `BSLTextDocumentService` / `BSLWorkspaceService`** — реализация LSP4J, маршрутизация.
- **Провайдеры** (`providers/`) — возможности LSP: hover, definition, references, rename, code actions,
  formatting, semantic tokens, inlay hints, code lens, folding и т.д. `DiagnosticProvider` поддерживает
  обе модели публикации — push (`publishDiagnostics`) и pull (`textDocument/diagnostic`).
- **Диагностики** (`diagnostics/`) — правила анализа (наследники `AbstractDiagnostic`, аннотация `@DiagnosticMetadata`).
- **Индекс ссылок** (`references/`) · **символы** (`context/symbol/`) · **CFG** (`cfg/`) ·
  **конфигурация** (`configuration/`) · **отчёты** (`reporters/`).

Ключевые подсистемы снабжены вложенными `CLAUDE.md` (подгружаются при работе с их файлами):
[`context/`](src/main/java/com/github/_1c_syntax/bsl/languageserver/context/CLAUDE.md) (контекст и символы) ·
[`types/`](src/main/java/com/github/_1c_syntax/bsl/languageserver/types/CLAUDE.md) (система типов) ·
[`references/`](src/main/java/com/github/_1c_syntax/bsl/languageserver/references/CLAUDE.md) ·
[`diagnostics/`](src/main/java/com/github/_1c_syntax/bsl/languageserver/diagnostics/CLAUDE.md) ·
[`providers/`](src/main/java/com/github/_1c_syntax/bsl/languageserver/providers/CLAUDE.md).

### Структура каталогов

```
src/main/java/.../languageserver/
  cli/ context/ providers/ diagnostics/ references/ configuration/ reporters/ cfg/ mcp/
  codeactions/ hover/ completion/ inlayhints/ codelenses/ rename/ folding/ semantictokens/ …
src/main/resources/  локализованные ресурсы диагностик (_ru/_en .properties), application*.properties
src/test/java/       тесты (JUnit 5);  src/test/resources/  фикстуры (.bsl/.os, метаданные, ожидаемые результаты)
src/jmh/             бенчмарки JMH;     docs/ (ru) · docs/en/ (en) · docs/contributing/ — для разработчиков
```

## Сборка и запуск

Используй **только** wrapper `./gradlew` (Windows — `gradlew.bat`); устанавливать Gradle вручную не нужно.

```bash
./gradlew build       # сборка + проверки + тесты (долго, см. ниже)
./gradlew bootJar     # исполняемый fat-jar (classifier -exec)
java -jar build/libs/bsl-language-server-*-exec.jar --help    # запуск; подкоманды см. выше
```

## Тесты и их длительность

```bash
./gradlew test --tests "*SomeDiagnosticTest"   # один класс — используй это при разработке
./gradlew test                                  # весь набор (МЕДЛЕННО)
./gradlew check                                 # то, что гоняет CI: test + jacoco + spotless + javadoc
```

- **600+ тестовых классов**, многие перезагружают Spring-контекст (`@DirtiesContext`,
  `@CleanupContextBeforeClassAndAfterClass`) → полный прогон занимает **много минут** (самый долгий шаг CI).
  Не прерывай его раньше времени, считая «зависшим»; при разработке гоняй одиночный класс через `--tests`.
- Параллелизм — на уровне **форков JVM** (не потоков): на CI 1 форк, локально половина ядер (1..4),
  переопределяется `-PmaxParallelForks=N`. Тестовой JVM нужен `maxHeapSize = 3g`.
- Автоматически подключаются java-агенты **jmockit** и **mockito**.

## UTF-8 и кириллица — обязательно к соблюдению

Проект двуязычный (ru/en), в коде/ресурсах/тестах **много кириллицы** (1С пишется кириллицей).

- **Всё в UTF-8 без BOM.** Компиляция, `processResources`, Sonar настроены на UTF-8.
- **`.properties` диагностик пиши живой кириллицей** — Gradle сам экранирует их в `\uXXXX` через
  `EscapeUnicode` (замена `native2ascii`). Не экранируй вручную и не коммить уже экранированные `.properties`.
- **EOL** по `.gitattributes`: для большинства файлов (`*.java/*.bsl/*.xml/*.md/*.json`) — **LF**,
  для `*.bat` — **CRLF**. Не меняй EOL целиком; отдельные тестовые файлы помечены `binary` или особыми
  правилами в `.editorconfig` — их whitespace не трогай.

## Диагностики

Одна диагностика = несколько связанных файлов (подробные гайды в `docs/contributing/` — следуй им, не выдумывай свой процесс):

1. Java-класс в `diagnostics/` — наследник `AbstractDiagnostic` с `@DiagnosticMetadata`.
2. Сообщения — `XxxDiagnostic_ru.properties` и `_en.properties` в `src/main/resources/.../diagnostics/`.
3. Документация — `docs/diagnostics/Xxx.md` (ru) и `docs/en/diagnostics/Xxx.md` (en);
   встраивается в ресурсы задачей `generateDiagnosticDocs`.
4. Тест `XxxDiagnosticTest` + фикстуры в `src/test/resources/diagnostics/`.

Подавление в коде 1С: `// BSLLS:КлючДиагностики-off` / `-on`, либо `// BSLLS-off`.
Гайды: [DiagnosticExample](docs/contributing/DiagnosticExample.md) ·
[DiagnosticStructure](docs/contributing/DiagnosticStructure.md) ·
[DiagnostcAddSettings](docs/contributing/DiagnostcAddSettings.md) ·
[DiagnosticQuickFix](docs/contributing/DiagnosticQuickFix.md) ·
[DiagnosticDevWorkFlow](docs/contributing/DiagnosticDevWorkFlow.md).

## Стиль кода и правила

- **Spotless** проверяет лицензионные заголовки `.java`. Перед коммитом при необходимости:
  `./gradlew spotlessApply` (алиас `./gradlew updateLicenses`). `check` упадёт без корректного заголовка.
- **Lombok** активно используется (нужен annotation processing). Соблюдай `.editorconfig`.
- Не запускай «оптимизацию импортов» по всему проекту — за порядком импортов следят мейнтейнеры
  (см. [EnvironmentSetting](docs/contributing/EnvironmentSetting.md)). Javadoc проверяется `-Xdoclint:all,-missing`.
- Следуй идиомам соседних файлов. Документация — в `docs/` и `docs/en/`; при изменении поведения обновляй **обе** локали.

## Git

- Веди разработку в отдельной ветке; не пуш в `develop`/`master` без явного запроса.
- Не создавай Pull Request, если об этом явно не попросили. Версия проставляется плагином
  git-versioning по тегам/веткам — руками не правь.
- **Сообщения коммитов — по [Conventional Commits](https://www.conventionalcommits.org/)**:
  `type(scope): описание` (типы: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`,
  `perf`, `style`). Например: `feat(diagnostics): add NewDiagnostic` / `fix(providers): …`.

### Работа в git worktree

Плагин **git-versioning** (`me.qoomon.git-versioning`) определяет версию по git-ref'у текущего
рабочего дерева. В **отдельном git worktree** он не может корректно её вычислить и **сборка
падает**. В этом случае отключи плагин и **задай версию вручную**:

```bash
./gradlew build -Dversioning.disable -Pversion=0.0.0-worktree
# эквивалент через переменные окружения:
# VERSIONING_DISABLE=true ./gradlew build -Pversion=0.0.0-worktree
```

`-Dversioning.disable` выключает плагин, `-Pversion=…` задаёт версию проекта (без неё она будет
`unspecified`). Флаги нужно передавать при каждом вызове `gradlew` в worktree.
