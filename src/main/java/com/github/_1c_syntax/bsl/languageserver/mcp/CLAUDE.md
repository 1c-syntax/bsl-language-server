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

Workspaces приходят от клиента через **MCP roots** (`McpRootsChangeConsumer`) — аналог LSP workspace
folders; при `--mcp` оба источника (LSP folders + MCP roots) питают общий `ServerContextProvider`.
Методы-инструменты помечены `@McpTool`.

Инфраструктура: `McpServerInfoConfigurer` (имя/версия из `AutoServerInfo`),
`McpWorkspaceBootstrap`/`McpWorkspaceResolver` (MCP roots → workspace), `McpRootsBootstrapper`/
`McpRootsChangeConsumer` (запрос/синхронизация `roots/list`), `McpDocumentReader` (единый доступ к
документу: `read()` — из кэша, `analyze()` — свежий AST + диагностики).

## Инструменты (`@McpTool`)

`AnalyzeFileTool` (диагностики файла) · `DocumentSymbolsTool` · `TypeInfoTool` (тип по имени:
члены, конструкторы) · `TypeAtPositionTool` (вывод типа в позиции) · `HoverTool` · `DefinitionTool` ·
`FindReferencesTool` · `CallHierarchyTool` · `GlobalMemberInfoTool` · `GlobalMemberSearchTool`
(нечёткий поиск по глобальному контексту). DTO — в `mcp/dto/`.

## Правки в этом каталоге

- Новый инструмент = метод `@McpTool`, делегирующий в существующий провайдер/подсистему через
  `McpDocumentReader`/`McpWorkspaceResolver`; бизнес-логику в `mcp/` не дублируй.
- Инструментам, которым нужен workspace, его выдаёт `McpWorkspaceResolver` — не полагайся на
  «текущий» неявно.
- Транспорт/профили задаёт `MainApplication` по аргументам — при добавлении транспорта правь и
  выбор профиля там, и соответствующий `application-*-mcp.properties`.
