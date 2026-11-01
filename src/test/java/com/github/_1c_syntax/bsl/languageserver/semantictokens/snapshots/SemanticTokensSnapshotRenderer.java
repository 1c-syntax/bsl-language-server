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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Рендерит исходный текст с семантической подсветкой в растровое изображение.
 * <p>
 * Текст раскладывается по моноширинной сетке: каждый символ рисуется в своей ячейке,
 * поэтому позиции токенов на изображении не зависят от ширины глифов fallback-шрифтов.
 * Табуляция выравнивается до следующей позиции, кратной {@value #TAB_SIZE}.
 * Слева выводятся номера строк. Завершающий перевод строки отдельной строкой не рисуется.
 */
public class SemanticTokensSnapshotRenderer {

  private static final int TAB_SIZE = 4;
  private static final int PADDING = 8;
  private static final int GUTTER_GAP_CELLS = 2;

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
   * @param tokens семантические токены документа; позиции — в UTF-16 code units, как в LSP
   * @param theme  цветовая тема
   * @return изображение
   */
  public BufferedImage render(String text, List<StyledToken> tokens, SemanticTokensTheme theme) {
    var lines = splitLines(text);
    var styles = styles(lines, tokens, theme);
    var columns = lines.stream().map(SemanticTokensSnapshotRenderer::visualColumns).toList();

    var metrics = metrics();
    int cellWidth = metrics.charWidth('M');
    int lineHeight = metrics.getHeight();
    int gutterCells = String.valueOf(lines.size()).length() + GUTTER_GAP_CELLS;
    int maxColumns = columns.stream()
      .mapToInt(lineColumns -> lineColumns[lineColumns.length - 1])
      .max()
      .orElse(0);

    int width = 2 * PADDING + (gutterCells + Math.max(maxColumns, 1)) * cellWidth;
    int height = 2 * PADDING + lines.size() * lineHeight;
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

    var graphics = image.createGraphics();
    try {
      setupGraphics(graphics);
      graphics.setColor(theme.background());
      graphics.fillRect(0, 0, width, height);

      for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
        int baseline = PADDING + lineIndex * lineHeight + metrics.getAscent();
        drawLineNumber(graphics, theme, lineIndex, gutterCells, cellWidth, baseline);
        drawLine(
          graphics,
          lines.get(lineIndex),
          styles[lineIndex],
          columns.get(lineIndex),
          PADDING + gutterCells * cellWidth,
          cellWidth,
          baseline,
          baseline + metrics.getDescent() - 1
        );
      }
    } finally {
      graphics.dispose();
    }

    return image;
  }

  private FontMetrics metrics() {
    var probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    var graphics = probe.createGraphics();
    try {
      setupGraphics(graphics);
      return graphics.getFontMetrics(font);
    } finally {
      graphics.dispose();
    }
  }

  private void setupGraphics(Graphics2D graphics) {
    graphics.setFont(font);
    graphics.setRenderingHint(
      RenderingHints.KEY_TEXT_ANTIALIASING,
      antialiasing ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
    );
    graphics.setRenderingHint(
      RenderingHints.KEY_ANTIALIASING,
      antialiasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF
    );
    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
  }

  private void drawLineNumber(
    Graphics2D graphics,
    SemanticTokensTheme theme,
    int lineIndex,
    int gutterCells,
    int cellWidth,
    int baseline
  ) {
    var number = String.valueOf(lineIndex + 1);
    int right = PADDING + (gutterCells - GUTTER_GAP_CELLS) * cellWidth;
    graphics.setFont(font);
    graphics.setColor(theme.lineNumber());
    for (int i = 0; i < number.length(); i++) {
      int x = right - (number.length() - i) * cellWidth;
      graphics.drawString(String.valueOf(number.charAt(i)), x, baseline);
    }
  }

  private void drawLine(
    Graphics2D graphics,
    String line,
    CharStyle[] styles,
    int[] columns,
    int originX,
    int cellWidth,
    int baseline,
    int underlineY
  ) {
    int index = 0;
    while (index < line.length()) {
      int codePoint = line.codePointAt(index);
      int charCount = Character.charCount(codePoint);
      var style = styles[index];
      int x = originX + columns[index] * cellWidth;

      if (!Character.isWhitespace(codePoint)) {
        graphics.setFont(font.deriveFont(style.fontStyle()));
        graphics.setColor(style.color());
        graphics.drawString(new String(Character.toChars(codePoint)), x, baseline);
      }
      if (style.underline()) {
        int width = (columns[index + charCount] - columns[index]) * cellWidth;
        graphics.setColor(style.color());
        graphics.drawLine(x, underlineY, x + width - 1, underlineY);
      }

      index += charCount;
    }
  }

  private static List<String> splitLines(String text) {
    var lines = new ArrayList<>(List.of(text.split("\r\n|\r|\n", -1)));
    if (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
      lines.remove(lines.size() - 1);
    }
    return Collections.unmodifiableList(lines);
  }

  /**
   * Визуальные колонки начала каждого UTF-16 code unit строки; последний элемент —
   * колонка за концом строки.
   */
  private static int[] visualColumns(String line) {
    var columns = new int[line.length() + 1];
    int column = 0;
    for (int i = 0; i < line.length(); i++) {
      columns[i] = column;
      char ch = line.charAt(i);
      if (ch == '\t') {
        column = (column / TAB_SIZE + 1) * TAB_SIZE;
      } else if (!Character.isLowSurrogate(ch)) {
        column++;
      }
    }
    columns[line.length()] = column;
    return columns;
  }

  private static CharStyle[][] styles(List<String> lines, List<StyledToken> tokens, SemanticTokensTheme theme) {
    var defaultStyle = new CharStyle(theme.foreground(), Font.PLAIN, false);
    var styles = new CharStyle[lines.size()][];
    for (int i = 0; i < lines.size(); i++) {
      styles[i] = new CharStyle[lines.get(i).length()];
      Arrays.fill(styles[i], defaultStyle);
    }

    for (var token : tokens) {
      if (token.line() < 0 || token.line() >= lines.size()) {
        continue;
      }
      var lineStyles = styles[token.line()];
      var style = styleOf(token, theme);
      int from = Math.max(token.start(), 0);
      int to = Math.min(token.start() + token.length(), lineStyles.length);
      for (int i = from; i < to; i++) {
        lineStyles[i] = style;
      }
    }
    return styles;
  }

  private static CharStyle styleOf(StyledToken token, SemanticTokensTheme theme) {
    int fontStyle = Font.PLAIN;
    if (intersects(token.modifiers(), theme.boldModifiers())) {
      fontStyle |= Font.BOLD;
    }
    if (intersects(token.modifiers(), theme.italicModifiers())) {
      fontStyle |= Font.ITALIC;
    }
    boolean underline = intersects(token.modifiers(), theme.underlineModifiers());
    return new CharStyle(theme.colorOf(token.type()), fontStyle, underline);
  }

  private static boolean intersects(Set<String> left, Set<String> right) {
    return left.stream().anyMatch(right::contains);
  }

  private record CharStyle(Color color, int fontStyle, boolean underline) {
  }
}
