/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Diagnostic;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE
  }
)
public class LineLengthDiagnostic implements BSLDiagnostic {

  private static final int MAX_LINE_LENGTH = 120;
  private int prevTokenType;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_LINE_LENGTH,
    description = "Максимальная длина строки в символах"
  )
  private int maxLineLength = MAX_LINE_LENGTH;

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }
    maxLineLength = (int) configuration.getOrDefault("maxLineLength", maxLineLength);
  }

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {

    List<Token> tokens = documentContext.getTokensFromDefaultChannel();
    List<Diagnostic> diagnostics = new ArrayList<>();
    Map<Integer, List<Integer>> tokensInOneLine = new HashMap<>();

    for (Token token : tokens) {
      if ((token.getType() != BSLLexer.STRINGPART
        && token.getType() != BSLLexer.STRINGTAIL)
        && ((prevTokenType != BSLLexer.STRINGPART && prevTokenType != BSLLexer.STRINGTAIL) && token.getType() == BSLLexer.SEMICOLON)) {
        putInCollection(tokensInOneLine, token);
      }
      prevTokenType = token.getType();
    }

    List<Token> comments = documentContext.getComments();
    comments.forEach((Token token) -> putInCollection(tokensInOneLine, token));

    tokensInOneLine.forEach((Integer key, List<Integer> value) -> {
      Optional<Integer> max = value.stream().max(Integer::compareTo);
      Integer maxCharPosition = max.orElse(0);
      if (maxCharPosition > maxLineLength) {
        Range range = RangeHelper.newRange(key, 0, key, maxCharPosition);
        diagnostics.add(BSLDiagnostic.createDiagnostic(
          this,
          range,
          getDiagnosticMessage(maxCharPosition, maxLineLength)));
      }
    });

    return diagnostics;
  }

  private void putInCollection(Map<Integer, List<Integer>> tokensInOneLine, Token token) {
    List<Integer> tokenList = tokensInOneLine.getOrDefault(token.getLine(), new ArrayList<>());
    tokenList.add(token.getCharPositionInLine() + token.getText().length());
    tokensInOneLine.put(token.getLine() - 1, tokenList);
  }

}
