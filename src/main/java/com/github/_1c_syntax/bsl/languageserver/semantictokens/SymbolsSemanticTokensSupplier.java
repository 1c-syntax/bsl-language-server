/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Сапплаер семантических токенов для символов: методов, переменных и параметров.
 */
@Component
@RequiredArgsConstructor
public class SymbolsSemanticTokensSupplier implements SemanticTokensSupplier {

  private final ReferenceIndex referenceIndex;
  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();
    var symbolTree = documentContext.getSymbolTree();
    var uri = documentContext.getUri();

    // Add method symbols (functions and procedures)
    for (var method : symbolTree.getMethods()) {
      var semanticTokenType = method.isFunction() ? SemanticTokenTypes.Function : SemanticTokenTypes.Method;
      helper.addRange(entries, method.getSubNameRange(), semanticTokenType);
      for (ParameterDefinition parameter : method.getParameters()) {
        helper.addRange(entries, parameter.getRange(), SemanticTokenTypes.Parameter, SemanticTokenModifiers.Definition);
      }
    }

    // Add explicit variable declarations from SymbolTree
    for (var variableSymbol : symbolTree.getVariables()) {
      if (variableSymbol.getKind() == VariableKind.PARAMETER) {
        continue;
      }
      var nameRange = variableSymbol.getVariableNameRange();
      if (!Ranges.isEmpty(nameRange)) {
        helper.addRange(entries, nameRange, SemanticTokenTypes.Variable, SemanticTokenModifiers.Definition);
      }
    }

    // Add variable references from ReferenceIndex (includes both definitions and usages)
    var references = referenceIndex.getReferencesFrom(uri, SymbolKind.Variable);
    references.stream()
      .filter(Reference::isSourceDefinedSymbolReference)
      .forEach(reference -> reference.getSourceDefinedSymbol()
        .filter(VariableSymbol.class::isInstance)
        .map(VariableSymbol.class::cast)
        .ifPresent(variableSymbol -> {
          var tokenType = variableSymbol.getKind() == VariableKind.PARAMETER
            ? SemanticTokenTypes.Parameter
            : SemanticTokenTypes.Variable;

          if (reference.getOccurrenceType() == OccurrenceType.DEFINITION) {
            helper.addRange(entries, reference.getSelectionRange(), tokenType, SemanticTokenModifiers.Definition);
          } else {
            helper.addRange(entries, reference.getSelectionRange(), tokenType);
          }
        }));

    return entries;
  }
}

