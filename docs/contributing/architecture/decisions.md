# Архитектурные решения (ADR)

Короткие записи решений, принятых при упорядочивании архитектуры. Формат: контекст → варианты →
решение → последствия. Новые записи добавляются в конец; отменённое решение не удаляется, а
помечается ссылкой на заменившее.

Авторитетная карта зависимостей между пакетами — не этот файл, а правило
`layer_dependencies_are_respected` в
[`ArchitectureTest`](../../../src/test/java/com/github/_1c_syntax/bsl/languageserver/architecture/ArchitectureTest.java).
Обзор подсистем — корневой `CLAUDE.md` и вложенные `CLAUDE.md` пакетов.

## ADR-0001. Рабочая ветка вместо отдельного git worktree

- **Контекст.** Работа по упорядочиванию должна идти изолированно. Плагин git-versioning не
  вычисляет версию в отдельном worktree, и каждый вызов Gradle там требует
  `-Dversioning.disable -Pversion=…`.
- **Решение.** Изоляцию даёт выделенная ветка в эфемерном контейнере облачной сессии; отдельный
  worktree не создаётся. Каждый пакет улучшений — отдельный коммит в этой ветке.
- **Последствия.** Сборка и тесты запускаются обычными командами из корневого `CLAUDE.md`.

## ADR-0002. `ConfigurationFiles` — единственное место поиска и чтения файла конфигурации

- **Контекст.** Порядок поиска `.bsl-language-server.json` (путь `app.configuration.path` →
  тот же путь от корня workspace → `app.globalConfiguration.path`) был написан трижды:
  `LanguageServerConfiguration.init()`, `LanguageServerConfigurationFactory`,
  `GlobalLanguageServerConfiguration.onApplicationReady()` — с расхождением `isFile()`/`exists()`.
  Чтение JSON с `ACCEPT_CASE_INSENSITIVE_ENUMS` — дважды, и оба места ловили только
  `IOException`. В Jackson 3 `JacksonException` — непроверяемое исключение, поэтому битый или
  неверно типизированный JSON ронял создание workspace-бина конфигурации, старт приложения и
  перечитывание файла наблюдателем.
- **Варианты.** (а) Расширить `catch` в двух местах — дубли остаются. (б) Spring-бин-локатор с
  `@Value`-путями — не встраивается в `LanguageServerConfiguration`, которую Jackson создаёт
  через конструктор без аргументов, а внедрение в поля запрещено архитектурным правилом.
  (в) Утилитный класс `ConfigurationFiles` с `locate(...)` и `read(...)`.
- **Решение.** (в). Пути по-прежнему приходят из `@Value` вызывающих бинов; порядок кандидатов,
  отбор только файлов, настройки маппера и политика ошибок (лог + пустой результат, текущие
  настройки сохраняются) — в одном месте.
- **Изменение контракта.** `update(File)` с отвергнутым файлом больше не бросает исключение
  разбора (раньше `CompletionContextFilteringTest` фиксировал `MismatchedInputException`): файл не
  применяется, прежние настройки сохраняются, ошибка пишется в лог. Исключению было некуда уйти —
  ни наблюдатель файлов, ни `@PostConstruct` его не обрабатывали. Событие об изменении
  конфигурации при этом публикуется, как и при любом вызове `update`. Ждёт подтверждения
  мейнтейнера (см. backlog).
- **Последствия.** Публичный API `BSLLSBinding.getLanguageServerConfigurationFactory()` сохранён:
  фабрика делегирует в `ConfigurationFiles`. Правило
  `configuration_files_are_parsed_only_by_configuration_files` запрещает классам пакета
  `configuration` зависеть от `tools.jackson.databind` в обход `ConfigurationFiles`.

## ADR-0003. Разрыв цикла `references` ↔ `types`

- **Контекст.** Карта слоёв допускала единственный цикл: `types` зависит от `references`
  (индекс ссылок, `ReferenceResolver`, модель `Reference`), а `references` — от `types` в двух
  местах: три `ReferenceFinder` (`Keyword`, `NewExpression`, `PlatformMember`), вся работа которых —
  резолв средствами системы типов, и `ReferenceIndexFiller`, спрашивающий у
  `GlobalScopeProvider`/`OScriptLibraryIndex` про глобальные имена и библиотеки OneScript.
- **Варианты.** (а) Оставить цикл. (б) Перенести индексатор в `types` — ломает
  ответственность пакета индекса ссылок. (в) Finder'ы, построенные на системе типов, — в `types`
  (они реализуют SPI `ReferenceFinder`, от которого `types` и так зависит); вопросы индексатора к
  системе типов — через узкий SPI в `references` с реализацией в `types`, как уже сделано для
  `SelfMemberResolver` (и `DiagnosticComputer` между `context` и `diagnostics`).
- **Решение.** (в): `types.references.{Keyword,NewExpression,PlatformMember}ReferenceFinder`,
  `references.WorkspaceNames` + `types.registry.WorkspaceNamesImpl`. Порядок finder'ов
  (`@Order`) и поведение не менялись.
- **Последствия.** `References` убран из числа зависимых от `Types` в карте слоёв; `references` и
  `types` добавлены в правило ацикличности срезов. Разрешённых циклов в карте не осталось.
