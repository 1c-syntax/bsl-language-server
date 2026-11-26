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
package com.github._1c_syntax.bsl.languageserver.types.oscript.events;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;
import java.nio.file.Path;
import java.util.List;

/**
 * Событие, публикуемое {@code EventPublisherAspect} после завершения
 * {@link OScriptLibraryIndex#reindex(ServerContext)}.
 * <p>
 * Содержит список найденных {@code lib.config}-манифестов — подписчики
 * (например, {@code OScriptLibraryFileSystemWatcher}) используют его, чтобы
 * актуализировать набор отслеживаемых директорий.
 */
@Getter
public class OScriptLibraryIndexedEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = 1L;

  private final transient ServerContext serverContext;
  private final transient List<Path> libConfigPaths;

  public OScriptLibraryIndexedEvent(OScriptLibraryIndex source,
                                    ServerContext serverContext,
                                    List<Path> libConfigPaths) {
    super(source);
    this.serverContext = serverContext;
    this.libConfigPaths = List.copyOf(libConfigPaths);
  }
}
