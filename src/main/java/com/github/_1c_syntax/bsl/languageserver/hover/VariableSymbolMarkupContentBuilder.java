/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

  private static final String VARIABLE_LOCATION_KEY = "variableLocation";
  private static final String VARIABLE_KEY = "var";
  private static final String EXPORT_KEY = "export";

  private final LanguageServerConfiguration configuration;

  @Override
  public MarkupContent getContent(VariableSymbol symbol) {
    var markupBuilder = new StringJoiner("\n");

    // сигнатура
    // местоположение переменной
    // описание переменной

    // сигнатура
    String signature = getSignature(symbol);
    addSectionIfNotEmpty(markupBuilder, signature);

    // местоположение переменной
    String location = getLocation(symbol);
    addSectionIfNotEmpty(markupBuilder, location);

    // описание переменной
    symbol.getDescription()
      .map(VariableDescription::getPurposeDescription)
      .ifPresent(description -> addSectionIfNotEmpty(markupBuilder, description));

    symbol.getDescription()
      .flatMap(VariableDescription::getTrailingDescription)
      .map(VariableDescription::getPurposeDescription)
      .ifPresent(trailingDescription -> addSectionIfNotEmpty(markupBuilder, trailingDescription));

    String content = markupBuilder.toString();

    return new MarkupContent(MarkupKind.MARKDOWN, content);
  }

  @Override
  public Class<VariableSymbol> getType() {
    return VariableSymbol.class;
  }

  private String getSignature(VariableSymbol symbol) {
    String signatureTemplate = "```bsl\n%s %s%s\n```";

    String varKey = getResourceString(VARIABLE_KEY);
    String name = symbol.getName();
    String export = symbol.isExport() ? (" " + getResourceString(EXPORT_KEY)) : "";

    return String.format(
      signatureTemplate,
      varKey,
      name,
      export
    );
  }

  private String getLocation(VariableSymbol symbol) {
    String mdoRef = MdoRefBuilder.getMdoRef(symbol.getOwner());
    String parentPostfix = symbol.getRootParent(SymbolKind.Method)
      .map(sourceDefinedSymbol -> "." + sourceDefinedSymbol.getName())
      .orElse("");

    mdoRef += parentPostfix;
    return getResourceString(VARIABLE_LOCATION_KEY, mdoRef);
  }

  private static void addSectionIfNotEmpty(StringJoiner markupBuilder, String newContent) {
    if (!newContent.isEmpty()) {
      markupBuilder.add(newContent);
      markupBuilder.add("");
      markupBuilder.add("---");
    }
  }

  private String getResourceString(String key) {
    return Resources.getResourceString(configuration.getLanguage(), getClass(), key);
  }

  private String getResourceString(String key, Object... args) {
    return Resources.getResourceString(configuration.getLanguage(), getClass(), key, args);
  }
}
