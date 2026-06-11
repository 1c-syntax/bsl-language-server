# Run in MCP mode

BSL Language Server can act as a [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server — exposing 1C (BSL) and OneScript code analysis to AI agents and tools that support MCP.

MCP tools run on top of the same engine as the LSP mode: the same parsing, the same providers. Workspaces are provided through [MCP roots](https://modelcontextprotocol.io/docs/concepts/roots) — the direct analog of LSP workspace folders.

!!! warning "Experimental feature"
    The MCP mode is built on Spring AI 2.0 (a milestone version at the time of writing). The API and behavior may change.

## Run modes

MCP can be exposed in three ways.

### Standalone MCP server over stdio

The MCP transport is `stdio` (the standard way to connect local tools). LSP is not started.

```sh
java -jar bsl-language-server.jar mcp
```

### Next to LSP over stdio

LSP stays on `stdio`, while MCP is additionally exposed over [Streamable HTTP](https://modelcontextprotocol.io/) on a built-in web server. Enabled with the `--mcp` flag of the `lsp` command:

```sh
java -jar bsl-language-server.jar lsp --mcp --server.port=8080
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

Positions (`line`, `character`) are zero-based, as in LSP.

## Launch options

| Option | Mode | Purpose |
| --- | --- | --- |
| `-c`, `--configuration` `<path>` | all | Path to the global configuration file (see [Configuration file](ConfigurationFile.md)) |
| `--mcp` | `lsp`, `websocket` | Also expose MCP over Streamable HTTP |
| `--mcp-path` `<path>` | `lsp --mcp`, `websocket --mcp` | MCP endpoint path (default `/mcp`) |
| `--server.port=<port>` | `lsp --mcp`, `websocket --mcp` | Port of the built-in web server |

## Connection example

For a client with the stdio transport (`mcpServers` format):

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

For the Streamable HTTP mode the client connects to `http://<host>:<port>/mcp` (or the path set by `--mcp-path`).
