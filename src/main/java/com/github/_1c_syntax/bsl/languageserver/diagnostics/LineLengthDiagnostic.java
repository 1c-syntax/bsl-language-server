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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE
  }
)
public class LineLengthDiagnostic extends AbstractDiagnostic {

  private static final int MAX_LINE_LENGTH = 120;
  private static final boolean CHECK_METHOD_DESCRIPTION = true;
  private static final boolean EXCLUDE_TRAILING_COMMENTS = false;

  private int prevTokenType;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_LINE_LENGTH
  )
  private int maxLineLength = MAX_LINE_LENGTH;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + CHECK_METHOD_DESCRIPTION
  )
  private boolean checkMethodDescription = CHECK_METHOD_DESCRIPTION;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + EXCLUDE_TRAILING_COMMENTS
  )
  private boolean excludeTrailingComments = EXCLUDE_TRAILING_COMMENTS;

  private final Map<Integer, List<Integer>> tokensInOneLine = new HashMap<>();

  @Override
  protected void check() {
    tokensInOneLine.clear();

    // First, process all tokens from default channel (code tokens)
    Set<Integer> linesWithCode = new HashSet<>();
    for (Token token : documentContext.getTokensFromDefaultChannel()) {
      if (mustBePutIn(token)) {
        putInCollection(token);
      }
      prevTokenType = token.getType();
      linesWithCode.add(token.getLine() - 1);
    }

    // Then process comments
    if (checkMethodDescription) {
      documentContext.getComments().stream()
        .filter(comment -> shouldIncludeComment(comment, linesWithCode))
        .forEach(this::putInCollection);
    } else {
      var descriptionRanges = documentContext.getSymbolTree().getMethods().stream()
        .map(MethodSymbol::getDescription)
        .flatMap(Optional::stream)
        .map(MethodDescription::getRange)
        .collect(Collectors.toList()); // список должен остаться модифицируемым!

      documentContext.getComments().stream()
        .filter(token -> !descriptionContainToken(descriptionRanges, token))
        .filter(comment -> shouldIncludeComment(comment, linesWithCode))
        .forEach(this::putInCollection);
    }

    tokensInOneLine.forEach((Integer key, List<Integer> value) -> {
      Integer maxCharPosition = value.stream().max(Integer::compareTo).orElse(0);
      if (maxCharPosition > maxLineLength) {
        diagnosticStorage.addDiagnostic(
          Ranges.create(key, 0, key, maxCharPosition),
          info.getMessage(maxCharPosition, maxLineLength)
        );
      }
    });
  }

  private boolean mustBePutIn(Token token) {

    boolean isStringPart = token.getType() == BSLLexer.STRINGPART
      || token.getType() == BSLLexer.STRINGTAIL;
    boolean prevIsStringPart = prevTokenType == BSLLexer.STRINGPART
      || prevTokenType == BSLLexer.STRINGTAIL;

    return !isStringPart && !(prevIsStringPart
      && token.getType() == BSLLexer.SEMICOLON);
  }

  private boolean shouldIncludeComment(Token comment, Set<Integer> linesWithCode) {
    // If excludeTrailingComments is enabled and this comment is on a line with code,
    // then it's a trailing comment and should be excluded
    return !excludeTrailingComments || !linesWithCode.contains(comment.getLine() - 1);
  }

  private void putInCollection(Token token) {
    List<Integer> tokenList = tokensInOneLine.getOrDefault(token.getLine() - 1, new ArrayList<>());
    tokenList.add(token.getCharPositionInLine() + token.getText().length());
    tokensInOneLine.put(token.getLine() - 1, tokenList);
  }

  private static boolean descriptionContainToken(List<Range> descriptionRanges, Token token) {
    if (descriptionRanges.isEmpty()) {
      return false;
    }

    var first = descriptionRanges.get(0);
    if (first.getStart().getLine() + 1 > token.getLine()) {
      return false;
    } else if (first.getEnd().getLine() + 1 < token.getLine()) {
      descriptionRanges.remove(first);
      return descriptionContainToken(descriptionRanges, token);
    } else {
      return Ranges.containsRange(first, Ranges.create(token));
    }
  }
}
