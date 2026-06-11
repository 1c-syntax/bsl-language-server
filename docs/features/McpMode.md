# Запуск в режиме MCP

BSL Language Server умеет работать как сервер [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) — открывать возможности анализа кода 1С (BSL) и OneScript AI-агентам и инструментам, которые поддерживают MCP.

Инструменты MCP работают поверх того же движка, что и LSP-режим: тот же разбор, те же провайдеры. Рабочие пространства задаются через [MCP roots](https://modelcontextprotocol.io/docs/concepts/roots) — прямой аналог workspace folders в LSP.

!!! warning "Экспериментальная возможность"
    Режим MCP основан на Spring AI 2.0 (на момент написания — milestone-версия). API и поведение могут меняться.

## Режимы запуска

MCP можно поднять несколькими способами.

### Отдельный MCP-сервер (команда `mcp`)

Транспорт выбирается параметром `--protocol`: `stdio` (по умолчанию) или `sse`. LSP при этом не запускается.

`stdio` — стандартный способ подключения локальных инструментов:

```sh
java -jar bsl-language-server.jar mcp
# эквивалентно: java -jar bsl-language-server.jar mcp --protocol stdio
```

`sse` — Server-Sent Events по HTTP на встроенном веб-сервере (эндпоинт `/sse`, сообщения на `/mcp/message`):

```sh
java -jar bsl-language-server.jar mcp --protocol sse --server.port=8080
```

### Рядом с LSP по stdio

LSP остаётся на `stdio`, а MCP дополнительно поднимается по [Streamable HTTP](https://modelcontextprotocol.io/) на встроенном веб-сервере. Включается флагом `--mcp`. Команда `lsp` — режим по умолчанию, поэтому её можно не указывать:

```sh
java -jar bsl-language-server.jar --mcp --server.port=8080
# эквивалентно: java -jar bsl-language-server.jar lsp --mcp --server.port=8080
```

### Рядом с LSP по websocket

LSP по websocket и MCP по Streamable HTTP на одном веб-сервере:

```sh
java -jar bsl-language-server.jar websocket --mcp --server.port=8080
```

## Рабочие пространства (MCP roots)

Каталоги исходников задаёт клиент через MCP roots — так же, как LSP-клиент передаёт workspace folders. Сервер индексирует объявленные корни в общий контекст и пересинхронизирует их при изменении (`roots/list_changed`). В комбинированных режимах (`lsp --mcp`, `websocket --mcp`) рабочие пространства дают как LSP-клиент (workspace folders), так и MCP-клиент (roots) — в один общий контекст.

## Доступные инструменты

| Инструмент | Назначение |
| --- | --- |
| `analyze_file` | Диагностики по файлу |
| `document_symbols` | Дерево символов файла (методы, области, переменные) |
| `find_references` | Все ссылки на символ в позиции |
| `call_hierarchy` | Входящие и исходящие вызовы метода/процедуры в позиции |
| `hover` | Подсказка по символу (сигнатура, тип, документация) |
| `definition` | Переход к объявлению символа |

Позиции (`line`, `character`) нумеруются с нуля, как в LSP.

## Параметры запуска

| Параметр | Режим | Назначение |
| --- | --- | --- |
| `-c`, `--configuration` `<path>` | все | Путь к глобальному конфигурационному файлу (см. [Конфигурационный файл](ConfigurationFile.md)) |
| `--protocol` `<stdio\|sse>` | `mcp` | Транспорт отдельного MCP-сервера: `stdio` (по умолчанию) или `sse` |
| `--mcp` | `lsp` (по умолчанию), `websocket` | Дополнительно поднять MCP по Streamable HTTP |
| `--mcp-path` `<path>` | `lsp --mcp`, `websocket --mcp` | Адрес MCP-эндпоинта (по умолчанию `/mcp`) |
| `--server.port=<port>` | `mcp --protocol sse`, `lsp --mcp`, `websocket --mcp` | Порт встроенного веб-сервера |

## Примеры конфигурации клиента

### stdio

Клиент сам запускает сервер и общается с ним по stdio (формат `mcpServers`):

```json
{
  "mcpServers": {
    "bsl-language-server": {
      "command": "java",
      "args": ["-jar", "/path/to/bsl-language-server.jar", "mcp"]
    }
  }
}
```

### Streamable HTTP

Сервер запущен отдельно (`--mcp` или `websocket --mcp`), клиент подключается к эндпоинту по URL:

```json
{
  "mcpServers": {
    "bsl-language-server": {
      "type": "streamable-http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

Адрес по умолчанию — `http://<host>:<port>/mcp`; путь меняется параметром `--mcp-path`, порт — `--server.port`.
