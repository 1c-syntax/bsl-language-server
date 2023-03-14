/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

public enum DiagnosticTag {
  STANDARD("Нарушение стандартов 1С"),
  LOCKINOS("Не будет работать в другой ОС"),
  SQL("Проблема с запросом"),
  PERFORMANCE("Проблема производительности"),
  BRAINOVERLOAD("Непонятный код"),
  BADPRACTICE("Плохая практика программирования"),
  CLUMSY("Излишние действия"),
  DESIGN("Ошибка в проектировании"),
  SUSPICIOUS("Подозрительный код"),
  UNPREDICTABLE("Непредсказуемо работающий код"),
  DEPRECATED("Устаревшая функциональность"),
  UNUSED("Неиспользуемый код"),
  ERROR("Ошибочная конструкция"),
  LOCALIZE("Проблемы локализации");

  private final String description;

  DiagnosticTag(String descriptionRu) {
    this.description = descriptionRu;
  }

  public String getDescription() {
    return description;
  }
}
