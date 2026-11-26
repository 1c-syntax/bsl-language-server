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
package com.github._1c_syntax.bsl.languageserver.context.events;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * Событие, публикуемое при освобождении вторичных данных документа в контексте сервера.
 * <p>
 * Событие генерируется контекстом сервера {@link ServerContext} при вызове метода
 * {@link ServerContext#tryClearDocument(DocumentContext)}, и только когда тот реально
 * освободил данные (документ не был открыт в редакторе). Так batch-анализ
 * ({@code AnalyzeCommand}) и {@code populateContext} выбрасывают тяжёлый AST/токенайзер
 * после обработки каждого файла.
 * <p>
 * В отличие от {@link DocumentContextContentChangedEvent} событие не подразумевает
 * пересчёт — оно сигнализирует, что производные данные документа выброшены, и привязанные
 * к URI кэши (хранящие AST-узлы или выведенные типы) должны быть сброшены, иначе они
 * удерживали бы освобождённые данные и росли бы на весь прогон. Это отдельная от
 * {@link ServerContextDocumentClosedEvent} семантика: документ не закрывается, лишь
 * освобождает резидентные данные.
 *
 * @see ServerContext#tryClearDocument(DocumentContext)
 */
public class ServerContextDocumentClearedEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = 8516815391686789513L;

  /**
   * Документ, чьи вторичные данные были освобождены.
   */
  @Getter
  private final DocumentContext documentContext;

  /**
   * Создаёт новое событие освобождения вторичных данных документа.
   *
   * @param source          контекст сервера, в котором были освобождены данные документа
   * @param documentContext документ, чьи вторичные данные были освобождены
   */
  public ServerContextDocumentClearedEvent(ServerContext source, DocumentContext documentContext) {
    super(source);
    this.documentContext = documentContext;
  }

  @Override
  public ServerContext getSource() {
    return (ServerContext) super.getSource();
  }
}
