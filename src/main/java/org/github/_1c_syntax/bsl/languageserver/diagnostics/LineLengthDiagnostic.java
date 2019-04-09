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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLLexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1
)
public class LineLengthDiagnostic implements BSLDiagnostic {

  private static final int MAX_LINE_LENGTH = 120;

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
    maxLineLength = (Integer) configuration.get("maxLineLength");
  }

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {

    List<Token> tokens = documentContext.getTokensFromDefaultChannel();

    List<Diagnostic> diagnostics = new ArrayList<>();

    Map<Integer, List<Integer>> tokensInOneLine = new HashMap<>();
    tokens.forEach((Token token) -> {
        List<Integer> tokenList = tokensInOneLine.getOrDefault(token.getLine(), new ArrayList<>());
        if ((token.getType() != BSLLexer.STRINGPART)
        && (token.getType() != BSLLexer.STRINGTAIL)) {
          tokenList.add(token.getCharPositionInLine() + token.getText().length());
          tokensInOneLine.put(token.getLine() - 1, tokenList);
        }
      });

    tokensInOneLine.forEach((Integer key, List<Integer> value) -> {
      Optional<Integer> max = value.stream().max(Integer::compareTo);
      Integer maxCharPosition = max.orElse(0);
      if (maxCharPosition > maxLineLength) {
        diagnostics.add(BSLDiagnostic.createDiagnostic(this, key, 0, key, maxCharPosition));
      }
    });

    return diagnostics;
  }

}
