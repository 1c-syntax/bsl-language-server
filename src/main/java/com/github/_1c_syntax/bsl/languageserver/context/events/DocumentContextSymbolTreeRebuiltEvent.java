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
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * Событие построения дерева символов документа заново по прежнему тексту.
 * <p>
 * Текст документа не менялся, но дерево символов построено заново: прежнее устарело, потому что
 * классификаторы теперь отвечают о методах и голых присваиваниях документа иначе, чем при его
 * построении, либо сменилась привязка документа к метаданным
 * ({@link DocumentContextConfigurationBindingChangedEvent}). Символы нового дерева не равны
 * прежним, если сменился их вид, поэтому всё, что посчитано по документу с опорой на его дерево
 * символов, надо пересчитать.
 */
public class DocumentContextSymbolTreeRebuiltEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = -2716385730496183652L;

  public DocumentContextSymbolTreeRebuiltEvent(DocumentContext source) {
    super(source);
  }

  @Override
  public DocumentContext getSource() {
    return (DocumentContext) super.getSource();
  }
}
