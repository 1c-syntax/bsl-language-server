# Run in MCP mode

BSL Language Server can act as a [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server — exposing 1C (BSL) and OneScript code analysis to AI agents and tools that support MCP.

MCP tools run on top of the same engine as the LSP mode: the same parsing, the same providers. Workspaces are registered with the `register_workspace`/`list_workspaces` tools — see the "Workspaces" section below.

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

## Workspaces

Every analysis tool answers only inside a registered workspace — a 1C configuration or OneScript project whose sources are indexed. A file outside every registered workspace is not analysed, and the tools that are not bound to a file (`type_info`, `global_member_info`, `global_member_search`) require an explicit `root` argument.

The client workflow:

1. `list_workspaces` — see what is already registered and get the `root` values.
2. `register_workspace` with the project directory — if the project is not in the list yet. Pass the **workspace folder**: the directory an editor opens and an LSP client sends as a workspace folder, not a sources subfolder. It holds the sources (`src/cf` of a configuration, the OneScript sources) and, when present, the [configuration file](ConfigurationFile.md) `.bsl-language-server.json`, which is only read from the workspace root. The tool indexes the sources and returns the `root`; registering an already registered directory does not re-index it.
3. `unregister_workspace` — release the index when the project is no longer needed.

The error messages are self-contained: for an unknown or missing `root` the server lists the registered roots and names the tool that registers a new one, so an agent can recover without asking a human.

Additional sources of workspaces:

- **LSP.** In the combined modes (`lsp --mcp`, `websocket --mcp`) workspaces come from the LSP client (workspace folders) into the same shared context — there is no need to register them over MCP, they show up in `list_workspaces` right away.
- **MCP roots.** Roots declared by the client through [MCP roots](https://modelcontextprotocol.io/docs/concepts/roots) are still indexed automatically, including re-sync on `roots/list_changed`. This works as long as the server is built on the `2025-11-25` revision of the MCP SDK, where roots are still active.

!!! warning "MCP roots are deprecated"
    In the [2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28/changelog) revision of the specification the roots feature (together with sampling and logging) is marked deprecated, and the `roots/list_changed` notification is removed from the protocol. The suggested migration is to pass directories through tool parameters and server configuration — which is exactly what `register_workspace`/`list_workspaces` do. Roots support is kept for compatibility with older clients; under the MCP feature lifecycle policy it cannot be removed earlier than twelve months after that revision, and this server will drop it when it moves to the new revision.

## Available tools

| Tool | Purpose |
| --- | --- |
| `list_workspaces` | Registered workspaces: the `root` for the other tools and the name |
| `register_workspace` | Register a project directory as a workspace and index its sources; the name can be given explicitly, otherwise the directory name is used |
| `unregister_workspace` | Remove a workspace and release its index |
| `analyze_file` | Diagnostics for a file |
| `document_symbols` | Symbol tree of a file (methods, regions, variables) |
| `find_references` | All references to the symbol at a position |
| `call_hierarchy` | Incoming and outgoing calls of the method/procedure at a position |
| `hover` | Hover info for a symbol (signature, type, documentation) |
| `definition` | Go to the symbol declaration |
| `type_info` | Properties, methods, events and constructors of a type looked up by name (e.g. `Массив`/`Array`), with platform metadata of both the type itself and its members (since/deprecated versions, execution contexts, notes, examples, see-also) |
| `global_member_info` | A global member looked up by name (e.g. `Сообщить`/`Message`) — function, property or system enum with signatures and metadata |
| `global_member_search` | Search global context members — functions (`СтартовыйСценарий`/`StartupScript`), properties (`Метаданные`/`Metadata`) and system enums; fuzzy matching and relevance ranking like autocomplete, grouped by category, can be narrowed by categories |
| `type_at_position` | Inferred type of the expression under the cursor and its available methods and properties |

Positions (`line`, `character`) are zero-based, as in LSP.

No tool modifies files on disk. The analysis tools are marked read-only (`readOnlyHint`), so a client should not ask for confirmation on every call. The workspace management tools change server state and are therefore not read-only; `unregister_workspace` is additionally marked destructive (`destructiveHint`) because it throws away the index that was built, so a client may reasonably ask for confirmation on that one.

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
