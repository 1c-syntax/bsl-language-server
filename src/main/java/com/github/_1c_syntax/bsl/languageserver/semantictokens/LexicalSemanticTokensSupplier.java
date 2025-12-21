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
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Сапплаер семантических токенов для лексических элементов: строк, чисел, операторов и ключевых слов.
 * <p>
 * Исключает строки, которые содержат запросы SDBL (они обрабатываются в {@link QuerySemanticTokensSupplier}).
 */
@Component
@RequiredArgsConstructor
public class LexicalSemanticTokensSupplier implements SemanticTokensSupplier {

  private static final Set<Integer> NUMBER_TYPES = Set.of(
    BSLLexer.DECIMAL,
    BSLLexer.FLOAT
  );

  private static final Set<Integer> STRING_TYPES = Set.of(
    BSLLexer.STRING,
    BSLLexer.STRINGPART,
    BSLLexer.STRINGSTART,
    BSLLexer.STRINGTAIL,
    BSLLexer.PREPROC_STRING
  );

  private static final Set<Integer> OPERATOR_TYPES = Set.of(
    BSLLexer.LPAREN,
    BSLLexer.RPAREN,
    BSLLexer.LBRACK,
    BSLLexer.RBRACK,
    BSLLexer.COMMA,
    BSLLexer.SEMICOLON,
    BSLLexer.COLON,
    BSLLexer.DOT,
    BSLLexer.PLUS,
    BSLLexer.MINUS,
    BSLLexer.MUL,
    BSLLexer.QUOTIENT,
    BSLLexer.MODULO,
    BSLLexer.ASSIGN,
    BSLLexer.NOT_EQUAL,
    BSLLexer.LESS,
    BSLLexer.LESS_OR_EQUAL,
    BSLLexer.GREATER,
    BSLLexer.GREATER_OR_EQUAL,
    BSLLexer.QUESTION,
    BSLLexer.TILDA
  );

  private static final Set<Integer> ANNOTATION_TOKENS = Set.of(
    BSLLexer.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENTATSERVER_SYMBOL,
    BSLLexer.ANNOTATION_ATCLIENT_SYMBOL,
    BSLLexer.ANNOTATION_ATSERVER_SYMBOL,
    BSLLexer.ANNOTATION_BEFORE_SYMBOL,
    BSLLexer.ANNOTATION_AFTER_SYMBOL,
    BSLLexer.ANNOTATION_AROUND_SYMBOL,
    BSLLexer.ANNOTATION_CHANGEANDVALIDATE_SYMBOL,
    BSLLexer.ANNOTATION_CUSTOM_SYMBOL
  );

  private static final Set<Integer> SPEC_LITERALS = Set.of(
    BSLLexer.UNDEFINED,
    BSLLexer.TRUE,
    BSLLexer.FALSE,
    BSLLexer.NULL
  );

  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();
    var tokensFromDefaultChannel = documentContext.getTokensFromDefaultChannel();
    var stringsWithQueries = collectStringsWithQueries(documentContext);

    for (Token token : tokensFromDefaultChannel) {
      var tokenType = token.getType();
      var tokenText = Objects.toString(token.getText(), "");
      if (!tokenText.isEmpty()) {
        // Skip string tokens that contain SDBL tokens - they'll be handled by QuerySemanticTokensSupplier
        if (STRING_TYPES.contains(tokenType) && stringsWithQueries.contains(token)) {
          continue;
        }
        selectAndAddSemanticToken(entries, token, tokenType);
      }
    }

    return entries;
  }

  private Set<Token> collectStringsWithQueries(DocumentContext documentContext) {
    var queries = documentContext.getQueries();
    if (queries.isEmpty()) {
      return Set.of();
    }

    var stringsToSkip = new HashSet<Token>();
    for (var query : queries) {
      for (Token queryToken : query.getTokens()) {
        if (queryToken.getChannel() != Token.DEFAULT_CHANNEL) {
          continue;
        }
        int queryLine = queryToken.getLine();
        for (var bslToken : documentContext.getTokensFromDefaultChannel()) {
          if (!STRING_TYPES.contains(bslToken.getType())) {
            continue;
          }
          if (bslToken.getLine() == queryLine) {
            var bslRange = Ranges.create(bslToken);
            int queryStart = queryToken.getCharPositionInLine();
            int queryEnd = queryStart + queryToken.getText().length();
            if (queryStart >= bslRange.getStart().getCharacter() && queryEnd <= bslRange.getEnd().getCharacter()) {
              stringsToSkip.add(bslToken);
            }
          }
        }
      }
    }
    return stringsToSkip;
  }

  private void selectAndAddSemanticToken(List<SemanticTokenEntry> entries, Token token, int tokenType) {
    // Skip '&' and all ANNOTATION_* symbol tokens here to avoid duplicate Decorator emission (handled via AST)
    if (tokenType == BSLLexer.AMPERSAND || ANNOTATION_TOKENS.contains(tokenType)) {
      return;
    }

    if (STRING_TYPES.contains(tokenType)) {
      helper.addRange(entries, Ranges.create(token), SemanticTokenTypes.String);
    } else if (tokenType == BSLLexer.DATETIME) {
      helper.addRange(entries, Ranges.create(token), SemanticTokenTypes.String);
    } else if (NUMBER_TYPES.contains(tokenType)) {
      helper.addRange(entries, Ranges.create(token), SemanticTokenTypes.Number);
    } else if (OPERATOR_TYPES.contains(tokenType)) {
      helper.addRange(entries, Ranges.create(token), SemanticTokenTypes.Operator);
    } else if (SPEC_LITERALS.contains(tokenType)) {
      helper.addRange(entries, Ranges.create(token), SemanticTokenTypes.Keyword);
    } else {
      String symbolicName = BSLLexer.VOCABULARY.getSymbolicName(tokenType);
      if (symbolicName != null && symbolicName.endsWith("_KEYWORD") && !symbolicName.startsWith("PREPROC_")) {
        helper.addRange(entries, Ranges.create(token), SemanticTokenTypes.Keyword);
      }
    }
  }
}

