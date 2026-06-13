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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClearedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClosedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import org.springframework.context.event.EventListener;

import java.net.URI;

/**
 * База для per-URI индексов, чьи записи привязаны к одному документу и должны
 * сбрасываться на событиях его жизненного цикла.
 * <p>
 * Содержит общие {@code @EventListener}-обработчики, сбрасывающие индекс по URI на:
 * <ul>
 *   <li>изменение содержимого — {@link DocumentContextContentChangedEvent};</li>
 *   <li>освобождение вторичных данных (batch-анализ выбрасывает AST после каждого
 *       файла) — {@link ServerContextDocumentClearedEvent};</li>
 *   <li>закрытие документа — {@link ServerContextDocumentClosedEvent};</li>
 *   <li>удаление документа/файла — {@link ServerContextDocumentRemovedEvent}.</li>
 * </ul>
 * Spring наследует {@code @EventListener} в конкретных подклассах-бинах, поэтому
 * наследникам достаточно реализовать {@link #clear(URI)}. Сама база бином не
 * является.
 */
abstract class AbstractDocumentLifecycleClearableIndex {

  /**
   * Удалить записи индекса, относящиеся к данному URI документа.
   *
   * @param uri URI документа.
   */
  public abstract void clear(URI uri);

  @EventListener
  public void handleContentChanged(DocumentContextContentChangedEvent event) {
    clear(event.getSource().getUri());
  }

  @EventListener
  public void handleDataCleared(ServerContextDocumentClearedEvent event) {
    clear(event.getDocumentContext().getUri());
  }

  @EventListener
  public void handleDocumentClosed(ServerContextDocumentClosedEvent event) {
    clear(event.getDocumentContext().getUri());
  }

  @EventListener
  public void handleDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    clear(event.getUri());
  }
}
