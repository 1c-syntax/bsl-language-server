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
package com.github._1c_syntax.bsl.languageserver.semantictokens.snapshots;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Рендерит исходный текст с семантической подсветкой в растровое изображение.
 */
public class SemanticTokensSnapshotRenderer {

  private final Font font;
  private final boolean antialiasing;

  /**
   * @param font         моноширинный шрифт текста
   * @param antialiasing включить ли сглаживание текста
   */
  public SemanticTokensSnapshotRenderer(Font font, boolean antialiasing) {
    this.font = font;
    this.antialiasing = antialiasing;
  }

  /**
   * Рендерит текст с подсветкой.
   *
   * @param text   исходный текст документа
   * @param tokens семантические токены документа
   * @param theme  цветовая тема
   * @return изображение
   */
  public BufferedImage render(String text, List<StyledToken> tokens, SemanticTokensTheme theme) {
    return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
  }
}
