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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Проверяет режим «отдельный MCP по Streamable HTTP» ({@code mcp --protocol streamable}):
 * сервер поднимается на servlet-контейнере, инструменты регистрируются, эндпоинт {@code /mcp}
 * отвечает на initialize.
 */
@SpringBootTest(
  webEnvironment = RANDOM_PORT,
  properties = "spring.main.web-application-type=servlet")
@ActiveProfiles({"mcp", "mcp-streamable"})
@CleanupContextBeforeClassAndAfterEachTestMethod
class McpStreamableServerTest {

  @LocalServerPort
  private int port;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void toolsAreRegisteredInTheMcpServer() {
    var toolNames = mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames)
      .containsExactlyInAnyOrder(
        "analyze_file", "document_symbols", "find_references", "call_hierarchy", "hover", "definition",
        "type_info", "type_at_position", "global_member_info", "global_member_search");
  }

  @Test
  void allToolsAreMarkedReadOnly() {
    // Все инструменты только читают код и ничего не меняют — клиент (например, Claude) не должен
    // считать их разрушающими и спрашивать подтверждение на каждый вызов.
    var tools = mcpSyncServer.listTools();

    assertThat(tools).isNotEmpty();
    assertThat(tools).allSatisfy(tool -> {
      assertThat(tool.annotations())
        .as("tool '%s' must carry read-only annotations", tool.name())
        .isNotNull();
      assertThat(tool.annotations().readOnlyHint())
        .as("tool '%s' must be read-only", tool.name())
        .isTrue();
      assertThat(tool.annotations().destructiveHint())
        .as("tool '%s' must not be destructive", tool.name())
        .isFalse();
    });
  }

  @Test
  void streamableHttpEndpointHandlesInitialize() throws Exception {
    var requestBody = """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26",\
      "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
      .timeout(Duration.ofSeconds(30))
      .header("Content-Type", "application/json")
      .header("Accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString(requestBody))
      .build();

    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Mcp-Session-Id")).isPresent();
    assertThat(response.body()).contains("\"serverInfo\"").contains("BSL Language Server");
  }
}
