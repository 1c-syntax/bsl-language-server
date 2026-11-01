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

import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticTokensSnapshotRendererTest {

  private static final Color BACKGROUND = new Color(0xFFFFFF);
  private static final Color FOREGROUND = new Color(0x333333);
  private static final Color LINE_NUMBER = new Color(0x00FF00);
  private static final Color KEYWORD = new Color(0x0000FF);
  private static final Color STRING = new Color(0xFF0000);

  private static final SemanticTokensTheme THEME = new SemanticTokensTheme(
    "test",
    BACKGROUND,
    FOREGROUND,
    LINE_NUMBER,
    Map.of(
      SemanticTokenTypes.Keyword, KEYWORD,
      SemanticTokenTypes.String, STRING
    ),
    Set.of(SemanticTokenModifiers.Definition),
    Set.of(SemanticTokenModifiers.Documentation),
    Set.of(SemanticTokenModifiers.Static)
  );

  private final SemanticTokensSnapshotRenderer renderer =
    new SemanticTokensSnapshotRenderer(new Font(Font.MONOSPACED, Font.PLAIN, 16), false);

  @Test
  void fillsBackgroundWithThemeBackground() {
    // when
    var image = renderer.render("Если", List.of(), THEME);

    // then
    assertThat(new Color(image.getRGB(0, 0))).isEqualTo(BACKGROUND);
  }

  @Test
  void paintsTokenWithColorOfItsType() {
    // given
    var tokens = List.of(new StyledToken(0, 0, 4, SemanticTokenTypes.Keyword, Set.of()));

    // when
    var image = renderer.render("Если", tokens, THEME);

    // then
    assertThat(countPixels(image, KEYWORD)).isPositive();
    assertThat(countPixels(image, FOREGROUND)).isZero();
  }

  @Test
  void paintsTextOutsideTokensWithForeground() {
    // when
    var image = renderer.render("А = Б", List.of(), THEME);

    // then
    assertThat(countPixels(image, FOREGROUND)).isPositive();
    assertThat(countPixels(image, KEYWORD)).isZero();
  }

  @Test
  void paintsTokenOfUnknownTypeWithForeground() {
    // given
    var tokens = List.of(new StyledToken(0, 0, 4, SemanticTokenTypes.Regexp, Set.of()));

    // when
    var image = renderer.render("Если", tokens, THEME);

    // then
    assertThat(countPixels(image, FOREGROUND)).isPositive();
  }

  @Test
  void paintsLineNumbersInGutter() {
    // when
    var image = renderer.render("А", List.of(), THEME);

    // then
    assertThat(countPixels(image, LINE_NUMBER)).isPositive();
  }

  @Test
  void placesTokenOnItsLine() {
    // given
    var tokens = List.of(new StyledToken(1, 0, 1, SemanticTokenTypes.Keyword, Set.of()));

    // when
    var image = renderer.render("А\nБ", tokens, THEME);

    // then
    assertThat(minY(image, KEYWORD)).isGreaterThan(maxY(image, FOREGROUND));
  }

  @Test
  void placesTokenAtItsColumn() {
    // given
    var tokens = List.of(new StyledToken(0, 5, 1, SemanticTokenTypes.Keyword, Set.of()));

    // when
    var image = renderer.render("АААА Б", tokens, THEME);

    // then
    assertThat(minX(image, KEYWORD)).isGreaterThan(maxX(image, FOREGROUND));
  }

  @Test
  void expandsTabToNextTabStop() {
    // given
    var tabbed = renderer.render(
      "\tА",
      List.of(new StyledToken(0, 1, 1, SemanticTokenTypes.Keyword, Set.of())),
      THEME
    );
    var spaced = renderer.render(
      "    А",
      List.of(new StyledToken(0, 4, 1, SemanticTokenTypes.Keyword, Set.of())),
      THEME
    );

    // then
    assertThat(minX(tabbed, KEYWORD)).isEqualTo(minX(spaced, KEYWORD));
  }

  @Test
  void growsWithLongestLineAndLineCount() {
    // when
    var small = renderer.render("АА", List.of(), THEME);
    var wide = renderer.render("АА\nАААААААА", List.of(), THEME);

    // then
    assertThat(wide.getWidth()).isGreaterThan(small.getWidth());
    assertThat(wide.getHeight()).isGreaterThan(small.getHeight());
  }

  @Test
  void ignoresTrailingLineBreak() {
    // when
    var withoutBreak = renderer.render("А", List.of(), THEME);
    var withBreak = renderer.render("А\n", List.of(), THEME);

    // then
    assertThat(withBreak.getHeight()).isEqualTo(withoutBreak.getHeight());
  }

  @Test
  void rendersBoldModifierWithMoreInk() {
    // when
    var plain = renderer.render(
      "Процедура",
      List.of(new StyledToken(0, 0, 9, SemanticTokenTypes.Keyword, Set.of())),
      THEME
    );
    var bold = renderer.render(
      "Процедура",
      List.of(new StyledToken(0, 0, 9, SemanticTokenTypes.Keyword, Set.of(SemanticTokenModifiers.Definition))),
      THEME
    );

    // then
    assertThat(countPixels(bold, KEYWORD)).isGreaterThan(countPixels(plain, KEYWORD));
  }

  @Test
  void rendersUnderlineModifierBelowText() {
    // when
    var plain = renderer.render(
      "Процедура",
      List.of(new StyledToken(0, 0, 9, SemanticTokenTypes.Keyword, Set.of())),
      THEME
    );
    var underlined = renderer.render(
      "Процедура",
      List.of(new StyledToken(0, 0, 9, SemanticTokenTypes.Keyword, Set.of(SemanticTokenModifiers.Static))),
      THEME
    );

    // then
    assertThat(maxY(underlined, KEYWORD)).isGreaterThan(maxY(plain, KEYWORD));
  }

  @Test
  void rendersItalicModifierDifferently() {
    // when
    var plain = renderer.render(
      "Процедура",
      List.of(new StyledToken(0, 0, 9, SemanticTokenTypes.Keyword, Set.of())),
      THEME
    );
    var italic = renderer.render(
      "Процедура",
      List.of(new StyledToken(0, 0, 9, SemanticTokenTypes.Keyword, Set.of(SemanticTokenModifiers.Documentation))),
      THEME
    );

    // then
    assertThat(pixels(italic)).isNotEqualTo(pixels(plain));
  }

  private static long countPixels(BufferedImage image, Color color) {
    return coordinates(image, color).count();
  }

  private static int minX(BufferedImage image, Color color) {
    return coordinates(image, color).mapToInt(xy -> xy[0]).min().orElseThrow();
  }

  private static int maxX(BufferedImage image, Color color) {
    return coordinates(image, color).mapToInt(xy -> xy[0]).max().orElseThrow();
  }

  private static int minY(BufferedImage image, Color color) {
    return coordinates(image, color).mapToInt(xy -> xy[1]).min().orElseThrow();
  }

  private static int maxY(BufferedImage image, Color color) {
    return coordinates(image, color).mapToInt(xy -> xy[1]).max().orElseThrow();
  }

  private static Stream<int[]> coordinates(BufferedImage image, Color color) {
    int rgb = color.getRGB();
    return IntStream.range(0, image.getHeight()).boxed()
      .flatMap(y -> IntStream.range(0, image.getWidth())
        .filter(x -> image.getRGB(x, y) == rgb)
        .mapToObj(x -> new int[]{x, y}));
  }

  private static int[] pixels(BufferedImage image) {
    return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
  }
}
