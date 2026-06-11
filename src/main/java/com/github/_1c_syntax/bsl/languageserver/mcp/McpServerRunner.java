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
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Запуск MCP-сервера на официальном MCP Java SDK поверх stdio.
 * <p>
 * SDK берёт на себя транспорт и протокол JSON-RPC; здесь — только сборка сервера,
 * регистрация инструментов ({@link McpTool}) и ожидание завершения сессии.
 * JSON-сериализация выполняется тем же Jackson 3 {@link JsonMapper}, что и в
 * остальном приложении ({@link JacksonMcpJsonMapper} оборачивает штатный бин).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpServerRunner {

  private final JsonMapper jsonMapper;
  private final McpWorkspace workspace;
  private final List<McpTool> tools;

  /**
   * Запустить сервер и блокировать поток до закрытия входного потока (EOF).
   *
   * @param in Входной поток (stdin).
   * @param out Выходной поток (исходный stdout, до перенаправления логов).
   */
  public void run(InputStream in, OutputStream out) {
    var mcpJsonMapper = new JacksonMcpJsonMapper(jsonMapper);
    var shutdownLatch = new CountDownLatch(1);
    var stdin = new EofSignalingInputStream(in, shutdownLatch);

    var transport = new StdioServerTransportProvider(mcpJsonMapper, stdin, out);

    var server = McpServer.sync(transport)
      .serverInfo("bsl-language-server", "prototype")
      .capabilities(ServerCapabilities.builder().tools(true).build())
      .tools(tools.stream().map(tool -> toSpecification(tool, mcpJsonMapper)).toList())
      .build();

    LOGGER.info("MCP server started. Tools: {}", tools.stream().map(McpTool::name).toList());

    try {
      shutdownLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      server.closeGracefully();
      LOGGER.info("MCP server stopped.");
    }
  }

  private SyncToolSpecification toSpecification(McpTool tool, JacksonMcpJsonMapper mcpJsonMapper) {
    String schema;
    try {
      schema = jsonMapper.writeValueAsString(tool.inputSchema());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize input schema for tool " + tool.name(), e);
    }

    var toolDefinition = Tool.builder()
      .name(tool.name())
      .description(tool.description())
      .inputSchema(mcpJsonMapper, schema)
      .build();

    return SyncToolSpecification.builder()
      .tool(toolDefinition)
      .callHandler((exchange, request) -> handleCall(tool, request.arguments()))
      .build();
  }

  private CallToolResult handleCall(McpTool tool, Map<String, Object> arguments) {
    // Каждый вызов выполняется в контексте рабочего пространства, как и LSP-запросы.
    try (var ignored = WorkspaceContextHolder.forUri(workspace.getUri())) {
      var result = tool.call(arguments);
      var text = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
      return CallToolResult.builder().addTextContent(text).build();
    } catch (Exception e) {
      LOGGER.warn("Tool '{}' failed", tool.name(), e);
      return CallToolResult.builder()
        .isError(true)
        .addTextContent(e.getClass().getSimpleName() + ": " + e.getMessage())
        .build();
    }
  }

  /**
   * Обёртка над входным потоком, сигнализирующая о EOF через защёлку,
   * чтобы основной поток мог корректно завершить работу при отключении клиента.
   */
  private static final class EofSignalingInputStream extends FilterInputStream {

    private final CountDownLatch latch;

    private EofSignalingInputStream(InputStream in, CountDownLatch latch) {
      super(in);
      this.latch = latch;
    }

    @Override
    public int read() throws IOException {
      var read = super.read();
      if (read == -1) {
        latch.countDown();
      }
      return read;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      var read = super.read(b, off, len);
      if (read == -1) {
        latch.countDown();
      }
      return read;
    }
  }
}
