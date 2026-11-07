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

import com.github._1c_syntax.bsl.languageserver.semantictokens.CustomSemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

/**
 * Цветовая тема для рендеринга семантической подсветки в изображение.
 * <p>
 * Цвет текста определяется типом токена; модификаторы токена отображаются начертанием шрифта
 * и подчёркиванием. Модификаторы, не попавшие ни в один из наборов, на изображении не видны.
 *
 * @param name               имя темы; совпадает со значением {@code canvas_theme} Sentry Snapshots
 * @param background         цвет фона
 * @param foreground         цвет текста вне токенов и токенов неизвестного теме типа
 * @param lineNumber         цвет номеров строк
 * @param typeColors         цвета по типам токенов
 * @param boldModifiers      модификаторы, отображаемые полужирным начертанием
 * @param italicModifiers    модификаторы, отображаемые курсивом
 * @param underlineModifiers модификаторы, отображаемые подчёркиванием
 */
public record SemanticTokensTheme(
  String name,
  Color background,
  Color foreground,
  Color lineNumber,
  Map<String, Color> typeColors,
  Set<String> boldModifiers,
  Set<String> italicModifiers,
  Set<String> underlineModifiers
) {

  private static final Set<String> BOLD_MODIFIERS = Set.of(
    SemanticTokenModifiers.Definition,
    SemanticTokenModifiers.Declaration
  );
  private static final Set<String> ITALIC_MODIFIERS = Set.of(
    SemanticTokenModifiers.Documentation,
    SemanticTokenModifiers.Async
  );
  private static final Set<String> UNDERLINE_MODIFIERS = Set.of(
    SemanticTokenModifiers.DefaultLibrary,
    SemanticTokenModifiers.Static
  );

  /**
   * Светлая тема в духе VS Code Light+.
   */
  public static final SemanticTokensTheme LIGHT = new SemanticTokensTheme(
    "light",
    new Color(0xFFFFFF),
    new Color(0x000000),
    new Color(0x9E9E9E),
    Map.ofEntries(
      Map.entry(SemanticTokenTypes.Keyword, new Color(0x0000FF)),
      Map.entry(SemanticTokenTypes.String, new Color(0xA31515)),
      Map.entry(CustomSemanticTokenTypes.STRING_ESCAPE, new Color(0xEE0000)),
      Map.entry(SemanticTokenTypes.Number, new Color(0x098658)),
      Map.entry(SemanticTokenTypes.Comment, new Color(0x008000)),
      Map.entry(SemanticTokenTypes.Function, new Color(0x795E26)),
      Map.entry(SemanticTokenTypes.Method, new Color(0x6F42C1)),
      Map.entry(SemanticTokenTypes.Event, new Color(0xB05A00)),
      Map.entry(SemanticTokenTypes.Variable, new Color(0x001080)),
      Map.entry(SemanticTokenTypes.Parameter, new Color(0x5C6BC0)),
      Map.entry(SemanticTokenTypes.Macro, new Color(0xAF00DB)),
      Map.entry(SemanticTokenTypes.Decorator, new Color(0x808000)),
      Map.entry(SemanticTokenTypes.Operator, new Color(0x5F5F5F)),
      Map.entry(SemanticTokenTypes.Namespace, new Color(0x267F99)),
      Map.entry(SemanticTokenTypes.Type, new Color(0x2B91AF)),
      Map.entry(SemanticTokenTypes.Property, new Color(0x0070C1)),
      Map.entry(SemanticTokenTypes.Class, new Color(0x00838F)),
      Map.entry(SemanticTokenTypes.Enum, new Color(0x7E57C2)),
      Map.entry(SemanticTokenTypes.EnumMember, new Color(0xC2185B))
    ),
    BOLD_MODIFIERS,
    ITALIC_MODIFIERS,
    UNDERLINE_MODIFIERS
  );

  /**
   * Тёмная тема в духе VS Code Dark+.
   */
  public static final SemanticTokensTheme DARK = new SemanticTokensTheme(
    "dark",
    new Color(0x1E1E1E),
    new Color(0xD4D4D4),
    new Color(0x6E7681),
    Map.ofEntries(
      Map.entry(SemanticTokenTypes.Keyword, new Color(0x569CD6)),
      Map.entry(SemanticTokenTypes.String, new Color(0xCE9178)),
      Map.entry(CustomSemanticTokenTypes.STRING_ESCAPE, new Color(0xD7BA7D)),
      Map.entry(SemanticTokenTypes.Number, new Color(0xB5CEA8)),
      Map.entry(SemanticTokenTypes.Comment, new Color(0x6A9955)),
      Map.entry(SemanticTokenTypes.Function, new Color(0xDCDCAA)),
      Map.entry(SemanticTokenTypes.Method, new Color(0xC586C0)),
      Map.entry(SemanticTokenTypes.Event, new Color(0xFFB86C)),
      Map.entry(SemanticTokenTypes.Variable, new Color(0x9CDCFE)),
      Map.entry(SemanticTokenTypes.Parameter, new Color(0x8FB4FF)),
      Map.entry(SemanticTokenTypes.Macro, new Color(0xD670D6)),
      Map.entry(SemanticTokenTypes.Decorator, new Color(0xBDBD5C)),
      Map.entry(SemanticTokenTypes.Operator, new Color(0xA0A0A0)),
      Map.entry(SemanticTokenTypes.Namespace, new Color(0x4EC9B0)),
      Map.entry(SemanticTokenTypes.Type, new Color(0x3DC9B0)),
      Map.entry(SemanticTokenTypes.Property, new Color(0x75BEFF)),
      Map.entry(SemanticTokenTypes.Class, new Color(0x26C6DA)),
      Map.entry(SemanticTokenTypes.Enum, new Color(0xB39DDB)),
      Map.entry(SemanticTokenTypes.EnumMember, new Color(0xF48FB1))
    ),
    BOLD_MODIFIERS,
    ITALIC_MODIFIERS,
    UNDERLINE_MODIFIERS
  );

  /**
   * Цвет токена указанного типа.
   *
   * @param type тип токена из легенды
   * @return цвет типа или {@link #foreground()}, если тема тип не знает
   */
  public Color colorOf(String type) {
    return typeColors.getOrDefault(type, foreground);
  }
}
