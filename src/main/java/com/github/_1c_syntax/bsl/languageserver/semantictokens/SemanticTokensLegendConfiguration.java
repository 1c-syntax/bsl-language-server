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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Конфигурация легенды семантических токенов для Language Server Protocol.
 * <p>
 * Определяет типы и модификаторы семантических токенов, которые поддерживаются
 * сервером для подсветки синтаксиса на основе семантического анализа кода.
 */
@Configuration
public class SemanticTokensLegendConfiguration {

  /**
   * Создает легенду семантических токенов для Language Server Protocol.
   *
   * @return легенда с поддерживаемыми типами токенов и модификаторами
   */
  @Bean
  public SemanticTokensLegend semanticTokensLegend() {

    List<String> tokenTypes = List.of(
      SemanticTokenTypes.Keyword,
      SemanticTokenTypes.String,
      SemanticTokenTypes.Number,
      SemanticTokenTypes.Comment,
      SemanticTokenTypes.Function,
      SemanticTokenTypes.Method,
      SemanticTokenTypes.Variable,
      SemanticTokenTypes.Parameter,
      SemanticTokenTypes.Macro,
      SemanticTokenTypes.Decorator,
      SemanticTokenTypes.Operator,
      SemanticTokenTypes.Namespace,
      SemanticTokenTypes.Type,  // Standard LSP token type for type names (identifiers of types)
      SemanticTokenTypes.Property,  // Added for SDBL field names
      SemanticTokenTypes.Class,  // Added for SDBL metadata object names (e.g. Справочник.Контрагенты, РегистрСведений.КурсыВалют)
      SemanticTokenTypes.Enum,  // Added for SDBL enum types (Перечисление.Пол)
      SemanticTokenTypes.EnumMember  // Added for predefined elements and enum values
    );

    List<String> tokenModifiers = List.of(
      SemanticTokenModifiers.Documentation,
      SemanticTokenModifiers.Definition,
      SemanticTokenModifiers.DefaultLibrary,  // Added for SDBL built-in functions and types
      SemanticTokenModifiers.Declaration,  // Added for SDBL alias declarations
      SemanticTokenModifiers.Readonly  // Added for SDBL parameters
    );

    return new SemanticTokensLegend(tokenTypes, tokenModifiers);
  }
}
