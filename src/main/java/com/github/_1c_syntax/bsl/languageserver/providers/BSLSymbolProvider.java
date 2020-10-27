/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.BSLDocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.BSLServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.ls_core.providers.SymbolProvider;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
@Primary
public class BSLSymbolProvider implements SymbolProvider {

  private final BSLServerContext context;

  @Override
  public List<? extends SymbolInformation> getSymbols(WorkspaceSymbolParams params) {
    var queryString = Optional.ofNullable(params.getQuery())
      .orElse("");

    Pattern pattern;
    try {
      pattern = CaseInsensitivePattern.compile(queryString);
    } catch (PatternSyntaxException e) {
      LOGGER.debug(e.getMessage(), e);
      return Collections.emptyList();
    }

    return context.getDocuments().values().stream()
      .map(documentContext -> (BSLDocumentContext) documentContext)
      .flatMap(BSLSymbolProvider::getSymbolPairs)
      .filter(symbolPair -> queryString.isEmpty() || pattern.matcher(symbolPair.getValue().getName()).find())
      .map(BSLSymbolProvider::createSymbolInformation)
      .collect(Collectors.toList());
  }

  private static Stream<Pair<URI, Symbol>> getSymbolPairs(BSLDocumentContext documentContext) {
    return documentContext.getSymbolTree().getChildrenFlat().stream()
      .filter(BSLSymbolProvider::isSupported)
      .map(symbol -> Pair.of(documentContext.getUri(), symbol));
  }

  private static boolean isSupported(Symbol symbol) {
    var symbolKind = symbol.getSymbolKind();
    switch (symbolKind) {
      case Method:
        return true;
      case Variable:
        return ((VariableSymbol) symbol).getKind() != VariableKind.LOCAL;
      default:
        return false;
    }
  }

  private static SymbolInformation createSymbolInformation(Pair<URI, Symbol> symbolPair) {
    var uri = symbolPair.getKey();
    var symbol = symbolPair.getValue();
    var symbolInformation = new SymbolInformation(
      symbol.getName(),
      symbol.getSymbolKind(),
      new Location(uri.toString(), symbol.getRange())
    );
    symbolInformation.setDeprecated(symbol.isDeprecated());
    return symbolInformation;
  }
}
