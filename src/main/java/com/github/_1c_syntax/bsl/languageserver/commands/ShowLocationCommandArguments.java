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
package com.github._1c_syntax.bsl.languageserver.commands;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.eclipse.lsp4j.Range;

import java.beans.ConstructorProperties;
import java.net.URI;

/**
 * Аргументы команды перехода к одной целевой локации.
 * <p>
 * Содержит URI документа, который нужно открыть, и диапазон, который должен быть выделен
 * и спозиционирован в редакторе.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ShowLocationCommandArguments extends DefaultCommandArguments {
  /**
   * Выделяемый диапазон в целевом документе.
   */
  Range range;

  /**
   * Конструктор аргументов команды перехода к одной целевой локации.
   *
   * @param uri   URI документа, который нужно открыть.
   * @param id    Идентификатор команды.
   * @param range Выделяемый диапазон в целевом документе.
   */
  @ConstructorProperties({"uri", "id", "range"})
  public ShowLocationCommandArguments(URI uri, String id, Range range) {
    super(uri, id);
    this.range = range;
  }
}
