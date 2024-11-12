/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableDescription;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

@Component
@RequiredArgsConstructor
public class VariableSymbolMarkupContentBuilder implements MarkupContentBuilder<VariableSymbol> {

  private static final String VARIABLE_KEY = "var";
  private static final String EXPORT_KEY = "export";

  private final LanguageServerConfiguration configuration;
  private final DescriptionFormatter descriptionFormatter;

  @Override
  public MarkupContent getContent(VariableSymbol symbol) {
    var markupBuilder = new StringJoiner("\n");

    // сигнатура
    // местоположение переменной
    // описание переменной

    // сигнатура
    String signature = descriptionFormatter.getSignature(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, signature);

    // местоположение переменной
    String location = descriptionFormatter.getLocation(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, location);

    // описание переменной
    symbol.getDescription()
      .map(VariableDescription::getPurposeDescription)
      .ifPresent(description -> descriptionFormatter.addSectionIfNotEmpty(markupBuilder, description));

    symbol.getDescription()
      .flatMap(VariableDescription::getTrailingDescription)
      .map(VariableDescription::getPurposeDescription)
      .ifPresent(trailingDescription -> descriptionFormatter.addSectionIfNotEmpty(markupBuilder, trailingDescription));

    String content = markupBuilder.toString();

    return new MarkupContent(MarkupKind.MARKDOWN, content);
  }

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Variable;
  }

}
