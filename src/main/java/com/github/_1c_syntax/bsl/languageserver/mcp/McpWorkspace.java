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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.utils.Absolute;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;

/**
 * Хранилище активного рабочего пространства MCP-сервера.
 * <p>
 * Инициализируется командой запуска ({@code mcp}) единожды на старте и далее
 * используется инструментами для доступа к общему
 * {@link ServerContext}. Благодаря тому, что это singleton-бин Spring,
 * MCP-инструменты работают над тем же индексом, что и остальной движок.
 */
@Component
@Getter
public class McpWorkspace {

  private ServerContext serverContext;
  private URI uri;

  /**
   * Привязать рабочее пространство к серверу.
   *
   * @param serverContext Контекст сервера, уже наполненный документами.
   * @param uri URI корня рабочего пространства.
   */
  public void bind(ServerContext serverContext, URI uri) {
    this.serverContext = serverContext;
    this.uri = uri;
  }

  /**
   * Получить контекст документа по пути к файлу, гарантированно пересобрав AST.
   * <p>
   * Если документ ещё не зарегистрирован в контексте — он будет добавлен.
   *
   * @param path Путь к файлу (абсолютный или относительный).
   * @return Готовый к запросам контекст документа.
   */
  public DocumentContext resolveDocument(String path) {
    if (serverContext == null) {
      throw new IllegalStateException("MCP workspace is not initialized");
    }

    var fileUri = Absolute.uri(new File(path));
    var documentContext = serverContext.addDocument(fileUri);
    serverContext.rebuildDocument(documentContext);
    return documentContext;
  }
}
