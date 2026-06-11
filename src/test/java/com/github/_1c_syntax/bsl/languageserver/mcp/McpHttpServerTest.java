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

import com.github._1c_syntax.bsl.languageserver.mcp.tools.AnalyzeFileTool;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Проверяет режим «MCP по Streamable HTTP рядом с LSP» ({@code websocket --mcp}):
 * MCP-сервер поднимается на том же servlet-контейнере и работает над общим контекстом.
 */
@SpringBootTest(
  webEnvironment = RANDOM_PORT,
  properties = "spring.main.web-application-type=servlet")
@ActiveProfiles({"mcp", "mcp-http"})
@CleanupContextBeforeClassAndAfterEachTestMethod
class McpHttpServerTest {

  private static final String SRC_DIR = "src/test/resources/cli";

  @LocalServerPort
  private int port;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Autowired
  private McpWorkspaceBootstrap workspaceBootstrap;

  @Autowired
  private AnalyzeFileTool analyzeFileTool;

  @Test
  void toolsAreRegisteredInTheMcpServer() {
    var toolNames = mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames)
      .containsExactlyInAnyOrder(
        "analyze_file", "document_symbols", "find_references", "call_hierarchy", "hover", "definition");
  }

  @Test
  void streamableHttpEndpointHandlesInitialize() throws Exception {
    var requestBody = """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26",\
      "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
      .header("Content-Type", "application/json")
      .header("Accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString(requestBody))
      .build();

    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Mcp-Session-Id")).isPresent();
    assertThat(response.body()).contains("\"serverInfo\"").contains("bsl-language-server");
  }

  @Test
  void analyzeFileToolReadsFromSharedServerContext() {
    workspaceBootstrap.index(Absolute.path(SRC_DIR));

    var result = analyzeFileTool.analyzeFile(SRC_DIR + "/test.bsl");

    assertThat(result.file()).isEqualTo(SRC_DIR + "/test.bsl");
    assertThat(result.diagnostics()).isNotEmpty();
  }
}
