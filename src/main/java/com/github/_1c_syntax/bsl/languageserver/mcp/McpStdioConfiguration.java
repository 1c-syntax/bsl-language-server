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

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.json.JsonMapper;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Конфигурация stdio-транспорта MCP-сервера для профиля {@code mcp}.
 * <p>
 * Подменяет stdio-транспорт, который по умолчанию создаёт автоконфигурация Spring AI
 * (её бин помечен {@code @ConditionalOnMissingBean}), чтобы:
 * <ul>
 *   <li>использовать штатный Jackson 3 {@link JsonMapper} приложения;</li>
 *   <li>отслеживать закрытие входного потока (EOF) и корректно завершать процесс;</li>
 *   <li>не терять сообщения, отправленные одновременно (см. {@link ConcurrentSendTransportProvider}).</li>
 * </ul>
 */
@Configuration
@Profile("mcp-stdio")
public class McpStdioConfiguration {

  @Bean
  public McpShutdownSignal mcpShutdownSignal() {
    return new McpShutdownSignal();
  }

  @Bean
  public McpServerTransportProviderBase stdioServerTransport(JsonMapper jsonMapper, McpShutdownSignal shutdownSignal) {
    var mcpJsonMapper = new JacksonMcpJsonMapper(jsonMapper);
    var stdin = new EofSignalingInputStream(System.in, shutdownSignal);
    return new ConcurrentSendTransportProvider(mcpJsonMapper, stdin, System.out);
  }

  // TODO: убрать вместе с McpEnqueueRetryingTransport, когда выйдет MCP Java SDK с исправлением
  //  одновременной отправки в stdio-транспорте: https://github.com/modelcontextprotocol/java-sdk/issues/686
  //  (исправлено в main SDK коммитом 2bb1481; в релизах по 2.0.1 включительно его нет).
  /**
   * Stdio-транспорт, сессия которого переносит одновременную отправку сообщений.
   * <p>
   * Транспорт SDK кладёт исходящее сообщение в приёмник, не допускающий одновременной записи:
   * из двух одновременных отправок одна получает отказ. Отказ не только теряет сообщение —
   * он обрывает обработку входящих сообщений, и сессия больше не отвечает ни на один запрос.
   * Поэтому транспорт сессии обёрнут в {@link McpEnqueueRetryingTransport}.
   */
  static final class ConcurrentSendTransportProvider extends StdioServerTransportProvider {

    ConcurrentSendTransportProvider(McpJsonMapper jsonMapper, InputStream inputStream, OutputStream outputStream) {
      super(jsonMapper, inputStream, outputStream);
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
      super.setSessionFactory(transport -> sessionFactory.create(new McpEnqueueRetryingTransport(transport)));
    }
  }

  /**
   * Обёртка над входным потоком, подающая сигнал завершения при достижении EOF.
   */
  static final class EofSignalingInputStream extends FilterInputStream {

    private final McpShutdownSignal shutdownSignal;

    EofSignalingInputStream(InputStream in, McpShutdownSignal shutdownSignal) {
      super(in);
      this.shutdownSignal = shutdownSignal;
    }

    @Override
    public int read() throws IOException {
      var read = super.read();
      if (read == -1) {
        shutdownSignal.signal();
      }
      return read;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      var read = super.read(b, off, len);
      if (read == -1) {
        shutdownSignal.signal();
      }
      return read;
    }
  }
}
