/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.mcp;

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Минимальный сервер Model Context Protocol поверх stdio.
 * <p>
 * Реализует подмножество JSON-RPC 2.0, достаточное для работы с инструментами:
 * {@code initialize}, {@code tools/list}, {@code tools/call}, {@code ping}.
 * Сообщения разделяются переводом строки (newline-delimited JSON) — стандартный
 * stdio-транспорт MCP для локальных инструментов.
 * <p>
 * Это прототип: транспорт реализован вручную, чтобы не тянуть зависимость с
 * конфликтующей версией Jackson. В дальнейшем легко заменить на официальный MCP SDK.
 */
@Slf4j
@Component
public class McpServer {

  private static final String PROTOCOL_VERSION = "2024-11-05";
  private static final String SERVER_NAME = "bsl-language-server";

  private static final int ERROR_METHOD_NOT_FOUND = -32601;
  private static final int ERROR_INTERNAL = -32603;

  private final JsonMapper jsonMapper;
  private final McpWorkspace workspace;
  private final Map<String, McpTool> tools;

  public McpServer(JsonMapper jsonMapper, McpWorkspace workspace, List<McpTool> tools) {
    this.jsonMapper = jsonMapper;
    this.workspace = workspace;
    this.tools = tools.stream().collect(Collectors.toMap(
      McpTool::name,
      tool -> tool,
      (first, second) -> first,
      LinkedHashMap::new
    ));
  }

  /**
   * Запустить цикл обработки сообщений до закрытия входного потока.
   *
   * @param in Входной поток (stdin).
   * @param out Выходной поток (исходный stdout, до перенаправления логов).
   */
  public void serve(InputStream in, OutputStream out) {
    var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

    LOGGER.info("MCP server started. Tools: {}", tools.keySet());

    try {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        handleLine(line, writer);
      }
    } catch (Exception e) {
      LOGGER.error("MCP server loop terminated with error", e);
    }

    LOGGER.info("MCP server stopped.");
  }

  private void handleLine(String line, Writer writer) {
    JsonNode request;
    try {
      request = jsonMapper.readTree(line);
    } catch (Exception e) {
      LOGGER.warn("Failed to parse incoming MCP message: {}", line, e);
      return;
    }

    var method = request.path("method").asString();
    var idNode = request.get("id");
    var isNotification = idNode == null || idNode.isNull();

    try {
      switch (method) {
        case "initialize" -> respond(writer, idNode, handleInitialize());
        case "tools/list" -> respond(writer, idNode, handleToolsList());
        case "tools/call" -> respond(writer, idNode, handleToolsCall(request.path("params")));
        case "ping" -> respond(writer, idNode, Map.of());
        case "notifications/initialized", "notifications/cancelled" -> {
          // уведомления без ответа
        }
        default -> {
          if (!isNotification) {
            respondError(writer, idNode, ERROR_METHOD_NOT_FOUND, "Method not found: " + method);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error handling MCP method '{}'", method, e);
      if (!isNotification) {
        respondError(writer, idNode, ERROR_INTERNAL, e.getMessage());
      }
    }
  }

  private Map<String, Object> handleInitialize() {
    return Map.of(
      "protocolVersion", PROTOCOL_VERSION,
      "capabilities", Map.of("tools", Map.of()),
      "serverInfo", Map.of("name", SERVER_NAME, "version", "prototype")
    );
  }

  private Map<String, Object> handleToolsList() {
    var toolSpecs = tools.values().stream()
      .map(tool -> Map.<String, Object>of(
        "name", tool.name(),
        "description", tool.description(),
        "inputSchema", tool.inputSchema()
      ))
      .toList();
    return Map.of("tools", toolSpecs);
  }

  private Map<String, Object> handleToolsCall(JsonNode params) {
    var name = params.path("name").asString();
    var tool = tools.get(name);
    if (tool == null) {
      return toolError("Unknown tool: " + name);
    }

    var arguments = params.path("arguments");

    try (var ignored = WorkspaceContextHolder.forUri(workspace.getUri())) {
      var result = tool.call(arguments);
      var text = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
      return Map.of("content", List.of(Map.of("type", "text", "text", text)));
    } catch (Exception e) {
      LOGGER.warn("Tool '{}' failed", name, e);
      return toolError(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private static Map<String, Object> toolError(String message) {
    return Map.of(
      "isError", true,
      "content", List.of(Map.of("type", "text", "text", message))
    );
  }

  private void respond(Writer writer, JsonNode idNode, Object result) {
    var message = new LinkedHashMap<String, Object>();
    message.put("jsonrpc", "2.0");
    message.put("id", idNode);
    message.put("result", result);
    writeMessage(writer, message);
  }

  private void respondError(Writer writer, JsonNode idNode, int code, String message) {
    var envelope = new LinkedHashMap<String, Object>();
    envelope.put("jsonrpc", "2.0");
    envelope.put("id", idNode);
    envelope.put("error", Map.of("code", code, "message", message == null ? "" : message));
    writeMessage(writer, envelope);
  }

  private void writeMessage(Writer writer, Map<String, Object> message) {
    try {
      writer.write(jsonMapper.writeValueAsString(message));
      writer.write('\n');
      writer.flush();
    } catch (Exception e) {
      LOGGER.error("Failed to write MCP message", e);
    }
  }
}
