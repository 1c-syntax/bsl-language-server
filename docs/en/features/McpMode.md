# Run in MCP mode

BSL Language Server can act as a [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server — exposing 1C (BSL) and OneScript code analysis to AI agents and tools that support MCP.

MCP tools run on top of the same engine as the LSP mode: the same parsing, the same providers. Workspaces are provided through [MCP roots](https://modelcontextprotocol.io/docs/concepts/roots) — the direct analog of LSP workspace folders.

!!! warning "Experimental feature"
    The MCP mode is built on Spring AI 2.0 (a milestone version at the time of writing). The API and behavior may change.

## Run modes

MCP can be exposed in several ways.

### Standalone MCP server (the `mcp` command)

The transport is selected with `--protocol`: `stdio` (default), `sse` or `streamable`. LSP is not started.

`stdio` — the standard way to connect local tools:

```sh
java -jar bsl-language-server.jar mcp
# same as: java -jar bsl-language-server.jar mcp --protocol stdio
```

`sse` — Server-Sent Events over HTTP on a built-in web server (endpoint `/sse`, messages at `/mcp/message`):

```sh
java -jar bsl-language-server.jar mcp --protocol sse --server.port=8080
```

`streamable` — Streamable HTTP over HTTP on a built-in web server (endpoint `/mcp`):

```sh
java -jar bsl-language-server.jar mcp --protocol streamable --server.port=8080
```

### Next to LSP over stdio

LSP stays on `stdio`, while MCP is additionally exposed over [Streamable HTTP](https://modelcontextprotocol.io/) on a built-in web server. Enabled with the `--mcp` flag. The `lsp` command is the default mode, so it can be omitted:

```sh
java -jar bsl-language-server.jar --mcp --server.port=8080
# same as: java -jar bsl-language-server.jar lsp --mcp --server.port=8080
```

### Next to LSP over websocket

LSP over websocket and MCP over Streamable HTTP on the same web server:

```sh
java -jar bsl-language-server.jar websocket --mcp --server.port=8080
```

## Workspaces (MCP roots)

Source directories are provided by the client through MCP roots — just like an LSP client sends workspace folders. The server indexes the declared roots into the shared context and re-syncs them on change (`roots/list_changed`). In the combined modes (`lsp --mcp`, `websocket --mcp`) workspaces come from both the LSP client (workspace folders) and the MCP client (roots) into one shared context.

## Available tools

| Tool | Purpose |
| --- | --- |
| `analyze_file` | Diagnostics for a file |
| `document_symbols` | Symbol tree of a file (methods, regions, variables) |
| `find_references` | All references to the symbol at a position |
| `call_hierarchy` | Incoming and outgoing calls of the method/procedure at a position |
| `hover` | Hover info for a symbol (signature, type, documentation) |
| `definition` | Go to the symbol declaration |
| `type_info` | Properties and methods of a type looked up by name (e.g. `Массив`/`Array`) |
| `global_member_info` | A global member looked up by name (e.g. `Сообщить`/`Message`) — function, property or system enum with signatures and metadata |
| `global_member_search` | Search global context members — functions (`СтартовыйСценарий`/`StartupScript`), properties (`Метаданные`/`Metadata`) and system enums; fuzzy matching and relevance ranking like autocomplete, grouped by category, can be narrowed by categories |
| `type_at_position` | Inferred type of the expression under the cursor and its available methods and properties |

Positions (`line`, `character`) are zero-based, as in LSP.

## Launch options

| Option | Mode | Purpose |
| --- | --- | --- |
| `-c`, `--configuration` `<path>` | all | Path to the global configuration file (see [Configuration file](ConfigurationFile.md)) |
| `--protocol` `<stdio\|sse\|streamable>` | `mcp` | Transport of the standalone MCP server: `stdio` (default), `sse` or `streamable` |
| `--mcp` | `lsp` (default), `websocket` | Also expose MCP over Streamable HTTP |
| `--mcp-path` `<path>` | `lsp --mcp`, `websocket --mcp` | MCP endpoint path (default `/mcp`) |
| `--server.port=<port>` | `mcp --protocol sse\|streamable`, `lsp --mcp`, `websocket --mcp` | Port of the built-in web server |

## Client configuration examples

### stdio

The client launches the server itself and talks to it over stdio (`mcpServers` format):

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

The server is started separately (`--mcp` or `websocket --mcp`), and the client connects to the endpoint by URL:

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

The default address is `http://<host>:<port>/mcp`; the path is changed with `--mcp-path`, the port with `--server.port`.
