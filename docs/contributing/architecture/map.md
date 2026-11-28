# Карта архитектуры

Снимок фактической структуры: модули верхнего уровня пакета
`com.github._1c_syntax.bsl.languageserver`, их ответственность, направление зависимостей, владельцы
изменяемого состояния и машинно-проверяемые стандарты. Карта описывает код, а не намерения:
граф зависимостей получен из `import` (раунд 49); при расхождении прав код или карту, а
ограничения — в `ArchitectureTest`.

Как пересобрать граф: для каждого каталога верхнего уровня собрать уникальные
`import com.github._1c_syntax.bsl.languageserver.<пакет>` (см. команду в progress.md, раунд 49).

## Модули

| Пакет | Ответственность | Точка входа / публичный контракт |
|---|---|---|
| `cli` | подкоманды picocli (`lsp`, `analyze`, `format`, `mcp`, `websocket`, `version`) | `*Command` (`Callable<Integer>`) |
| `lsp` | обвязка LSP4J: `BSLLanguageServer`, `BSLTextDocumentService`, `BSLWorkspaceService` | `onDocument(uri, ifMissing, request)` — единственный вход документных запросов |
| `providers` | адаптация возможностей LSP к подсистемам, тонкий слой | `XxxProvider` по запросу LSP |
| `codeactions`, `codelenses`, `color`, `commands`, `completion`, `documenthighlight`, `documentlink`, `folding`, `formatting`, `hover`, `inlayhints`, `rename`, `semantictokens` | реализации отдельных возможностей (реестры supplier'ов) | `XxxSupplier`, вызываются только своим провайдером |
| `context` | рабочая область и документ: `ServerContext`, `DocumentContext`, символы, вычислители | `ServerContextProvider`, `DocumentContext`, `SymbolTree` |
| `references` | индекс вхождений и резолюция символа под курсором | `ReferenceIndex`, `ReferenceResolver`, SPI `ReferenceFinder`, `WorkspaceNames` |
| `types` | система типов (вывод, реестр, OneScript, формы) | `TypeService` (фасад) |
| `diagnostics` | движок диагностик и сами правила | `BSLDiagnostic`; словарь `diagnostics.metadata` — лист без обратных зависимостей |
| `cfg` | граф потока управления | `ControlFlowGraphIndex` |
| `configuration` | глобальная и per-workspace конфигурация, чтение файла | `LanguageServerConfiguration`, `ConfigurationFiles` |
| `reporters` | отчёты `analyze` (SARIF, JSON, generic…) | `XxxReporter`, `ReportContext` |
| `mcp` | инструменты Model Context Protocol | `McpWorkspaceFolders` — единственный разбор путей клиента |
| `index` | основа индексов, сбрасываемых по жизненному циклу документа | `AbstractDocumentLifecycleClearableIndex` |
| `events` | события жизненного цикла workspace и сервера (только URI, без контекста) | `ApplicationEvent`-наследники |
| `aop` | публикация событий, замеры, Sentry (AspectJ CTW) | `EventPublisherAspect`, `Pointcuts` |
| `infrastructure` | Spring-обвязка: скоупы, executor'ы, кеши | `WorkspaceScope`, `CacheConfiguration` |
| `client` | состояние подключённого клиента | `ClientCapabilitiesHolder`, `LanguageClientHolder` |
| `utils` | чистые утилиты без доменных зависимостей (лист) | `Trees`, `Ranges`, `Positions`, `Regions`, `Keywords`, `NavigationLinks`, … |
| `binding`, `databind`, `jsonrpc`, `recognizer`, `websocket` | узкая инфраструктура (привязка конфигурации, Jackson, JSON-RPC, распознаватель кода, транспорт) | — |

## Направление зависимостей

Фактический граф (раунд 49; стрелка — «импортирует»). Листья — без зависимостей от других
пакетов проекта: `utils`, `events`, `client`, `databind`, `jsonrpc`, `recognizer`, `websocket`.

```
cli         -> configuration context infrastructure mcp providers reporters utils
lsp         -> client configuration context events infrastructure jsonrpc providers utils
providers   -> client codeactions codelenses color commands completion configuration context
               documenthighlight documentlink events folding formatting hover inlayhints
               references rename semantictokens types utils
mcp         -> configuration context infrastructure providers types utils
reporters   -> configuration context diagnostics
diagnostics -> cfg configuration context formatting infrastructure recognizer references types utils
types       -> cfg client configuration context events index infrastructure references utils
references  -> configuration context infrastructure utils
cfg         -> context index infrastructure utils
index       -> context
context     -> client configuration diagnostics(.metadata) events infrastructure utils
configuration -> diagnostics(.metadata) events infrastructure utils
infrastructure -> client
```

Пояснения к неочевидным рёбрам:

- `context`/`configuration` → `diagnostics` — только словарь `diagnostics.metadata` (коды, типы,
  важность), который сам ни от чего не зависит; движок диагностик подключается к `context` через
  интерфейс `DiagnosticComputer` (инверсия зависимости). Цикла по коду нет.
- `references` ↛ `types` — разорвано ADR-0003: индексатор спрашивает систему типов через SPI
  `WorkspaceNames`, реализованный в `types`.

Закреплено: `ArchitectureTest.layer_dependencies_are_respected` (белый список слоёв) и
`acyclic_domains_stay_free_of_cycles` (ацикличность доменов `ACYCLIC_DOMAINS`).

## Владение изменяемым состоянием

Три уровня жизни (см. корневой `CLAUDE.md`): синглтоны приложения, `@WorkspaceScope` (по экземпляру
на рабочую область) и prototype `DocumentContext`. Производные данные сбрасываются **по событиям**,
которые публикует `aop/EventPublisherAspect`, а не вызовами из бизнес-логики.

| Состояние | Владелец (скоуп) | Чем сбрасывается |
|---|---|---|
| документы рабочей области, метаданные конфигурации | `ServerContext` (workspace) | мутаторы `ServerContext` → события `ServerContextDocument*`, `ServerContextCleared` |
| AST, токены, `SymbolTree`, вторичные данные файла | `DocumentContext` (prototype) | `rebuild`/`clearSecondaryData`; `freezeComputedData` |
| вхождения символов | `references.model.*Repository`, `ReferenceIndex` (workspace) | `ReferenceIndexFiller` по `DocumentContextContentChanged`/`…BindingChanged`/`…Removed` |
| реестр типов и члены | `TypeRegistry` (workspace) | epoch при (де)регистрации источников; `ConfigurationTypesRegisteredEvent` |
| индексы «документ → данные» | наследники `AbstractDocumentLifecycleClearableIndex` (workspace) | события жизненного цикла документа (общая база) |
| конфигурация | `GlobalLanguageServerConfiguration` (синглтон), per-workspace `LanguageServerConfiguration` | `*ConfigurationChangedEvent` |
| возможности клиента | `ClientCapabilitiesHolder` (синглтон) | `LanguageServerInitializedEvent` |

## Машинно-проверяемые стандарты

- `ArchitectureTest` (ArchUnit): именование и размещение диагностик, провайдеров, репортёров,
  команд CLI и событий; запрет `System.out/err` и `java.util.logging`; запрет `@Autowired` на
  полях; карта слоёв и ацикличность; единственные точки для чтения файла конфигурации, фильтра
  вида code action, документных запросов LSP, путей MCP-клиента; запрет ключа кеша
  `DocumentContext`.
- Контрактные тесты: `ConfigurationSchemaDefaultsTest` и `DiagnosticParametersSchemaTest` (код ↔
  JSON-схема), `DiagnosticDocsLayoutTest` и `DiagnosticResourcesParityTest` (документация и
  ресурсы диагностик в обеих локалях), `DiagnosticTestLayoutTest` (у каждой диагностики свой тест),
  `DiagnosticConfigurationContractTest` (настройка без параметров не ломает диагностику).
- Отдельный workflow CI `javadoc.yml` (`./gradlew javadoc`): `check` javadoc не запускает.
