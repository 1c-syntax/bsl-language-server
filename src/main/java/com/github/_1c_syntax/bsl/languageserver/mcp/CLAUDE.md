<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# mcp/ — сервер Model Context Protocol

Предоставляет анализ кода 1С внешним MCP-клиентам (ИИ-инструментам). **Это ещё одна «голова»
поверх того же ядра LSP** — инструменты переиспользуют провайдеры и `ServerContextProvider`, а не
дублируют логику. См. корневой [CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Способы запуска и транспорты

Транспорт и Spring-профили выбирает **`MainApplication`** по аргументам **до** старта контекста
(`getActiveProfiles`/`getWebApplicationType`/`applyMcpEndpointPath`); сам MCP-сервер поднимает
автоконфигурация Spring AI. Два способа:

1. **Отдельная подкоманда `mcp`** (`McpCommand`) — транспорт по `--protocol`:
   - `stdio` (по умолчанию) — профили `mcp,mcp-stdio`; `McpStdioConfiguration` (общий `JsonMapper`,
     `EofSignalingInputStream`); процесс блокируется до EOF stdin через `McpShutdownSignal`.
   - `sse` — `mcp,mcp-sse` (Server-Sent Events по HTTP); `streamable` — `mcp,mcp-streamable`
     (Streamable HTTP). HTTP-транспорты требуют servlet-контейнера, процесс жив за счёт веб-сервера.
2. **Флаг `--mcp` к `lsp` (по умолчанию) или `websocket`** — поднимает MCP по **Streamable HTTP**
   рядом с LSP на том же процессе; эндпоинт `/mcp` (меняется `--mcp-path` → системное свойство
   `spring.ai.mcp.server.streamable-http.mcp-endpoint`). Профили: `mcp` + `lsp-mcp` (LSP по stdio,
   stdout занят каналом LSP) либо `mcp` + `websocket-mcp` (рядом с LSP-WebSocket, тот же порт).

**Терминология — из LSP:** регистрируется *рабочая папка* (workspace folder) — один корень проекта;
множество папок и есть *рабочая область* (workspace). В текстах для клиента и в javadoc пиши
«рабочая папка», не «рабочее пространство» — иначе термины разъезжаются с протоколом.

Папки клиент регистрирует **сам, инструментами** `register_workspace_folder`/`list_workspace_folders`/
`unregister_workspace_folder` — это основной путь. Дополнительные источники: LSP workspace folders (при
`--mcp` оба источника питают общий `ServerContextProvider`) и **MCP roots** (`McpRootsChangeConsumer`).
Roots объявлены deprecated в спеке MCP 2026-07-28 (`roots/list_changed` оттуда уже удалён) —
поддерживаются как совместимость, новую функциональность на них не завязывай.
Методы-инструменты помечены `@McpTool`.

Инфраструктура: `McpServerInfoConfigurer` (имя/версия из бина `ServerInfo`),
`McpWorkspaceBootstrap` (регистрация + индексация каталога), `McpWorkspaceResolver`
(`workspaceFolder` → рабочая папка), `McpWorkspaceFolders` (нормализация `workspaceFolder`: URI или
путь; общий текст подсказки о регистрации),
`McpRootsBootstrapper`/`McpRootsChangeConsumer` (запрос/синхронизация `roots/list`),
`McpDocumentReader` (единый доступ к документу: `read()` — из кэша, `analyze()` — свежий AST +
диагностики).

**Ошибки — часть API для агента.** Сообщение о незарегистрированной рабочей папке обязано перечислять
доступные корни и называть инструмент регистрации: агент исправляется сам, без человека. Единый
текст — `McpWorkspaceFolders.registrationHint`, не пиши свой.

## Инструменты (`@McpTool`)

`ListWorkspaceFoldersTool`/`RegisterWorkspaceFolderTool`/`UnregisterWorkspaceFolderTool` (управление
рабочими папками) · `AnalyzeFileTool` (диагностики файла) · `DocumentSymbolsTool` · `TypeInfoTool` (тип по имени:
члены, конструкторы, метаданные СП самого типа и его членов — `ApiMetadataDto`) ·
`TypeAtPositionTool` (вывод типа в позиции) · `HoverTool` · `DefinitionTool` ·
`FindReferencesTool` · `CallHierarchyTool` · `GlobalMemberInfoTool` · `GlobalMemberSearchTool`
(нечёткий поиск по глобальному контексту). DTO — в `mcp/dto/`.

## Правки в этом каталоге

- Новый инструмент = метод `@McpTool`, делегирующий в существующий провайдер/подсистему через
  `McpDocumentReader`/`McpWorkspaceResolver`; бизнес-логику в `mcp/` не дублируй.
- Инструментам, которым нужна рабочая папка, её выдаёт `McpWorkspaceResolver` — не полагайся на
  «текущую» неявно.
- Транспорт/профили задаёт `MainApplication` по аргументам — при добавлении транспорта правь и
  выбор профиля там, и соответствующий `application-*-mcp.properties`.
