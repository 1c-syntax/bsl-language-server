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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class SymbolProvider {

  private final ServerContext context;

  private static final Set<VariableKind> supportedVariableKinds = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.GLOBAL
  );

  public List<? extends WorkspaceSymbol> getSymbols(WorkspaceSymbolParams params) {
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
      .flatMap(SymbolProvider::getSymbolPairs)
      .filter(symbolPair -> queryString.isEmpty() || pattern.matcher(symbolPair.getValue().getName()).find())
      .map(SymbolProvider::createWorkspaceSymbol)
      .collect(Collectors.toList());
  }

  private static Stream<Pair<URI, SourceDefinedSymbol>> getSymbolPairs(DocumentContext documentContext) {
    return documentContext.getSymbolTree().getChildrenFlat().stream()
      .filter(SymbolProvider::isSupported)
      .map(symbol -> Pair.of(documentContext.getUri(), symbol));
  }

  private static boolean isSupported(Symbol symbol) {
    var symbolKind = symbol.getSymbolKind();
    switch (symbolKind) {
      case Method:
        return true;
      case Variable:
        return supportedVariableKinds.contains(((VariableSymbol) symbol).getKind());
      default:
        return false;
    }
  }

  private static WorkspaceSymbol createWorkspaceSymbol(Pair<URI, SourceDefinedSymbol> symbolPair) {
    var uri = symbolPair.getKey();
    var symbol = symbolPair.getValue();
    var location = new Location(uri.toString(), symbol.getRange());

    var workspaceSymbol = new WorkspaceSymbol();
    workspaceSymbol.setName(symbol.getName());
    workspaceSymbol.setKind(symbol.getSymbolKind());
    workspaceSymbol.setLocation(Either.forLeft(location));
    workspaceSymbol.setTags(symbol.getTags());

    return workspaceSymbol;
  }
}
