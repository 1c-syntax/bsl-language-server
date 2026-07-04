<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# configuration/ — конфигурация сервера

Конфигурирование BSL Language Server из `.bsl-language-server.json`. Сквозная подсистема: её
читают диагностики и провайдеры. См. корневой [CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Два уровня конфигурации

- **`LanguageServerConfiguration`** — **на каждый workspace** (`@Scope("workspace")`). Держит
  `language`, пути (`configurationRoot`, `excludePaths`, `siteRoot`) и опции фич (см. подпакеты).
  `@PostConstruct init()` ищет файл по порядку: путь из `app.configuration.path` → корень
  workspace + `.bsl-language-server.json` → глобальный `~/.bsl-language-server.json`. `update(File)`
  перечитывает с диска, `reset()` откатывает к снимку после Spring-инициализации (не к «голым»
  дефолтам). Публикует `LanguageServerConfigurationChangedEvent` (через AOP).
- **`GlobalLanguageServerConfiguration`** — **глобальный синглтон**: `language`, `sendErrors`
  (Sentry), `traceLog`, `capabilities` (text document sync), `workspaceSymbol`. Грузится на
  `ContextRefreshedEvent`; событие `GlobalLanguageServerConfigurationChangedEvent`.
- **`LanguageServerConfigurationFactory`** — создаёт per-workspace экземпляры (через
  `ObjectProvider`, чтобы работал AOP/scope-proxy).

Энумы: `Language` (RU/EN), `SendErrorsMode` (SEND/NEVER/ASK), `WorkspaceSymbolOptions`.

## Опции по фичам (подпакеты)

Каждая фича читает свой срез: `configuration.getXxxOptions()`.

- **`diagnostics/`** — `DiagnosticsOptions`: `computeTrigger` (ONTYPE/ONSAVE/NEVER), `mode`,
  `analyzeOnStart`, `skipSupport`, `subsystemsFilter`, фильтры LSP-severity, `ignoredAuthors`,
  и главное — **`parameters`** (`Map<String, Either<Boolean, Map>>`: вкл/выкл и настройки правил)
  и `metadata` (переопределение `@DiagnosticMetadata`).
- **`codelens/`** (`CodeLensOptions` + `TestRunnerAdapterOptions`), **`inlayhints/`**,
  **`semantictokens/`** (`strTemplateMethods`), **`references/`** (`commonModuleAccessors`),
  **`documentlink/`**, **`formating/`** (так в коде: `FormattingOptions` — регистр ключевых слов,
  on-type), **`oscript/`** (`libRoots`, `useEnvLibLocation`), **`platform/`** (`V8PlatformOptions`:
  `binPath`, `targetVersion` для диагностик совместимости), **`capabilities/`** (text document sync).

Паттерн «parameters» у фич с suppliers (codelens/inlayhints): включение supplier'а проверяется по
его id в `Map<String, Either<Boolean, Map>>` (`XxxConfiguration.supplierIsEnabled(...)`).

## Перезагрузка с диска — `watcher/`

`ConfigurationFileSystemWatcher` (NIO WatchService, опрос `@Scheduled` каждые 5 c) следит за
глобальным и per-workspace файлами; регистрирует слежение по `WorkspaceAddedEvent`, снимает по
`BeforeWorkspaceRemovedEvent`. `ConfigurationFileChangeListener` на CREATE/MODIFY вызывает
`update(file)`, на DELETE — `reset()`. Дальше публикуется change-событие, и фичи реагируют.

## Десериализация — `databind/`

Кастомные Jackson-десериализаторы: `ParametersDeserializer` (→ `Either<Boolean, Map>`),
`AnnotationsDeserializer`, `DiagnosticMetadataMapDeserializer` (строки → энумы + прокси аннотации
`@DiagnosticMetadata` через geantyref). `events/` — `*ConfigurationChangedEvent`.

## Правки в этом каталоге

- Новая настройка фичи — поле в соответствующем `XxxOptions` + дефолт; для диагностик настройки
  идут через `@DiagnosticParameter` (см. [diagnostics/](../diagnostics/CLAUDE.md)), не сюда.
- При новых полях проверь Jackson-маппинг и обнови JSON-схему/документацию настроек (обе локали).
- Реакция фичи на смену конфига — `@EventListener` на `*ConfigurationChangedEvent`.
