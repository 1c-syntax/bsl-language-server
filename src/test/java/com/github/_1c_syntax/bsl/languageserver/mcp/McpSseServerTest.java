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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Проверяет режим «MCP по SSE» ({@code mcp --protocol sse}): сервер поднимается на servlet-контейнере,
 * инструменты регистрируются, эндпоинт {@code /sse} отдаёт поток событий с адресом message-эндпоинта.
 */
@SpringBootTest(
  webEnvironment = RANDOM_PORT,
  properties = "spring.main.web-application-type=servlet")
@ActiveProfiles({"mcp", "mcp-sse"})
@CleanupContextBeforeClassAndAfterEachTestMethod
class McpSseServerTest {

  @LocalServerPort
  private int port;

  @Autowired
  private McpSyncServer mcpSyncServer;

  @Test
  void toolsAreRegisteredInTheMcpServer() {
    var toolNames = mcpSyncServer.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames)
      .containsExactlyInAnyOrder(
        "analyze_file", "document_symbols", "find_references", "call_hierarchy", "hover", "definition");
  }

  @Test
  void sseEndpointStreamsTheMessageEndpoint() throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/sse"))
      .timeout(Duration.ofSeconds(30))
      .GET()
      .build();

    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type"))
      .hasValueSatisfying(contentType -> assertThat(contentType).contains("text/event-stream"));

    // Сразу после подключения сервер шлёт событие `endpoint` с адресом message-эндпоинта.
    // Поток SSE держится открытым, поэтому чтение ограничиваем таймаутом, чтобы тест не завис.
    var endpointData = CompletableFuture.supplyAsync(() -> {
      try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("data:") && line.contains("/mcp/message")) {
            return line;
          }
        }
        return null;
      } catch (java.io.IOException e) {
        throw new UncheckedIOException(e);
      }
    }).get(15, TimeUnit.SECONDS);

    assertThat(endpointData).contains("/mcp/message");
  }
}
