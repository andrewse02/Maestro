# Maestro MCP Server

## Overview

The Maestro MCP (Model Context Protocol) server exposes a minimal tool surface for LLM agents:
- inspect the current view hierarchy
- inspect the current list of custom commands
- take a screenshot of the current device
- run Maestro flow files

The MCP server is designed to stay narrow and predictable. It runs as part of the Maestro CLI and communicates over a standardized protocol.

## Features

- Exposes `inspect_view_hierarchy`
- Exposes `inspect_custom_commands`
- Exposes `take_screenshot`
- Exposes `run_flow_files`
- Uses STDIO transport for MCP clients

## Running the MCP Server

To use the MCP server as an end user, after following the maestro install instructions run:

```
maestro mcp
```

This launches the MCP server via the Maestro CLI, exposing Maestro tools over STDIO for LLM agents and other clients.

## Developing

## Tool Surface

The server currently registers only:

- `inspect_view_hierarchy`
- `inspect_custom_commands`
- `take_screenshot`
- `run_flow_files`

Keep this list intentionally small unless there is a strong reason to expand it.

## Evals testing

When changing one of the MCP tools, test not only that it works correctly but that LLMs can call it correctly and use the output appropriately. Add relevant test cases to `./maestro-cli/src/test/mcp/maestro-evals.yaml`, and then run the eval test suite with:

```
ANTHROPIC_API_KEY=<your_key> ./maestro-cli/src/test/mcp/run-mcp-server-evals.sh
```

## Implementation Notes & Rationale

### Using forked version of official kotlin MCP SDK

The [official MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) can't be used directly because it requires Java 21 and Kotlin 2.x, while Maestro is built on Java 8 and Kotlin 1.8.x for broad compatibility. However, we want to be able to benefit from features added to the SDK since the MCP spec is changing rapidly. So we created a fork that "downgrades" the reference SDK to Java 8 and Kotlin 1.8.22.


### Why Integrate MCP Server Directly Into `maestro-cli`?

- **Dependency Management:** The MCP server needs access to abstractions like `MaestroSessionManager` and other CLI internals. Placing it in a separate module (e.g., `maestro-mcp`) would create a circular dependency between `maestro-cli` and the new module.
- **Simplicity:** Keeping all MCP logic within `maestro-cli` avoids complex build configurations and makes the integration easier to maintain and review.
- **Simplicity:** A small built-in tool surface is easier to maintain and reason about.

### Potential Future Improvements

- **Shared Abstractions:** If more MCP-related code or other integrations are needed, consider extracting shared abstractions (e.g., session management, tool interfaces) into a `common` or `core` module. This would allow for a clean separation and potentially enable a standalone `maestro-mcp` module.
- **Streamable HTTP:** This MCP server currently only uses STDIO for communication.
