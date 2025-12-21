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
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableDescription;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Сапплаер семантических токенов для комментариев.
 * <p>
 * Добавляет токены для обычных комментариев, исключая комментарии-описания методов и переменных
 * (они обрабатываются в {@link BslDocSemanticTokensSupplier}).
 * <p>
 * При поддержке многострочных токенов клиентом последовательные комментарии объединяются в один токен.
 */
@Component
@RequiredArgsConstructor
public class CommentSemanticTokensSupplier implements SemanticTokensSupplier {

  private final SemanticTokensHelper helper;

  @Setter
  private boolean multilineTokenSupport;

  @EventListener
  public void onClientCapabilitiesChanged(LanguageServerInitializeRequestReceivedEvent event) {
    multilineTokenSupport = Optional.of(event)
      .map(LanguageServerInitializeRequestReceivedEvent::getParams)
      .map(InitializeParams::getCapabilities)
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getSemanticTokens)
      .map(SemanticTokensCapabilities::getMultilineTokenSupport)
      .orElse(false);
  }

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();
    var symbolTree = documentContext.getSymbolTree();

    // Collect description ranges for describable symbols
    List<Range> descriptionRanges = new ArrayList<>();

    for (var method : symbolTree.getMethods()) {
      method.getDescription().ifPresent((MethodDescription description) ->
        addDescriptionRange(descriptionRanges, description)
      );
    }

    for (var variableSymbol : symbolTree.getVariables()) {
      variableSymbol.getDescription().ifPresent((VariableDescription description) -> {
        addDescriptionRange(descriptionRanges, description);
        description.getTrailingDescription().ifPresent((VariableDescription trailingDescription) ->
          addDescriptionRange(descriptionRanges, trailingDescription)
        );
      });
    }

    // Filter comments (excluding those inside descriptions)
    List<Token> regularComments = new ArrayList<>();
    for (var commentToken : documentContext.getComments()) {
      var commentRange = Ranges.create(commentToken);

      // Skip comments that are inside method/variable descriptions - they are handled by BslDocSemanticTokensSupplier
      boolean insideDescription = descriptionRanges.stream().anyMatch(r -> Ranges.containsRange(r, commentRange));
      if (insideDescription) {
        continue;
      }

      regularComments.add(commentToken);
    }

    // Sort by line number
    regularComments.sort(Comparator.comparingInt(Token::getLine));

    if (multilineTokenSupport) {
      addMultilineCommentTokens(entries, regularComments);
    } else {
      addSingleLineCommentTokens(entries, regularComments);
    }

    return entries;
  }

  private void addSingleLineCommentTokens(List<SemanticTokenEntry> entries, List<Token> comments) {
    for (var commentToken : comments) {
      helper.addRange(entries, Ranges.create(commentToken), SemanticTokenTypes.Comment);
    }
  }

  private void addMultilineCommentTokens(List<SemanticTokenEntry> entries, List<Token> comments) {
    if (comments.isEmpty()) {
      return;
    }

    int commentTypeIdx = helper.getTypeIndex(SemanticTokenTypes.Comment);
    if (commentTypeIdx < 0) {
      return;
    }

    int i = 0;
    while (i < comments.size()) {
      var firstToken = comments.get(i);
      int startLine = firstToken.getLine() - 1; // 0-indexed
      int startChar = firstToken.getCharPositionInLine();

      // Find consecutive comments (on adjacent lines)
      int lastIdx = i;
      int expectedNextLine = firstToken.getLine() + 1;

      for (int j = i + 1; j < comments.size(); j++) {
        var nextToken = comments.get(j);
        if (nextToken.getLine() == expectedNextLine) {
          lastIdx = j;
          expectedNextLine = nextToken.getLine() + 1;
        } else {
          break;
        }
      }

      if (lastIdx == i) {
        // Single comment - emit as regular token
        helper.addRange(entries, Ranges.create(firstToken), SemanticTokenTypes.Comment);
      } else {
        // Multiple consecutive comments - emit as multiline token
        int totalLength = 0;
        for (int k = i; k <= lastIdx; k++) {
          var token = comments.get(k);
          totalLength += token.getText().length();
          if (k < lastIdx) {
            totalLength += 1; // newline character
          }
        }

        entries.add(new SemanticTokenEntry(startLine, startChar, totalLength, commentTypeIdx, 0));
      }

      i = lastIdx + 1;
    }
  }

  private void addDescriptionRange(List<Range> descriptionRanges, SourceDefinedSymbolDescription description) {
    var range = description.getRange();
    if (Ranges.isEmpty(range)) {
      return;
    }
    descriptionRanges.add(range);
  }
}

