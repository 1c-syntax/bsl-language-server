<!-- Nested CLAUDE.md: грузится лениво при работе с файлами этого каталога. Держи кратким и точным. -->

# mcp/ — сервер Model Context Protocol

Режим `mcp`: предоставляет анализ кода 1С внешним MCP-клиентам (ИИ-инструментам). **Это ещё одна
«голова» поверх того же ядра LSP** — инструменты переиспользуют провайдеры и `ServerContextProvider`,
а не дублируют логику. См. корневой [CLAUDE.md](../../../../../../../../../CLAUDE.md).

## Поднятие сервера

Spring AI MCP starter, активируется профилем `mcp`. Транспорт — **stdio** (`McpStdioConfiguration`:
общий `JsonMapper`, `EofSignalingInputStream` для graceful-shutdown по EOF). В сборке также
подключён webmvc-starter (HTTP-транспорт) — профили `application-*-mcp.properties`. Методы-инструменты
помечены `@McpTool`.

Инфраструктура: `McpServerInfoConfigurer` (имя/версия из `AutoServerInfo`),
`McpWorkspaceBootstrap`/`McpWorkspaceResolver` (MCP roots → workspace через `ServerContextProvider`),
`McpRootsBootstrapper`/`McpRootsChangeConsumer` (запрос/синхронизация `roots/list`),
`McpDocumentReader` (единый доступ к документу: `read()` — из кэша, `analyze()` — свежий AST + диагностики).

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
- Раздел помечен как прототип (см. `package-info`) — API может меняться.
