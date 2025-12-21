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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сапплаер семантических токенов для функций НСтр (NStr) и СтрШаблон (StrTemplate).
 * <p>
 * Для НСтр: подсвечивает языковые ключи (ru=, en=) в строковых параметрах.
 * <p>
 * Для СтрШаблон: подсвечивает плейсхолдеры (%1, %2, %(1)) в строковых параметрах.
 */
@Component
@RequiredArgsConstructor
public class NStrAndStrTemplateSemanticTokensSupplier implements SemanticTokensSupplier {

  private static final Pattern NSTR_METHOD_PATTERN = CaseInsensitivePattern.compile("^(НСтр|NStr)$");
  private static final Pattern TEMPLATE_METHOD_PATTERN = CaseInsensitivePattern.compile("^(СтрШаблон|StrTemplate)$");

  // Pattern for NStr language keys like "ru='..." or "en='..."
  // Matches language code followed by = and either ' or "
  private static final Pattern NSTR_LANG_KEY_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*['\"]");

  // Pattern for StrTemplate placeholders: %1-%10 or %(1)-%(10)
  private static final Pattern STR_TEMPLATE_PLACEHOLDER_PATTERN = Pattern.compile("%(?:(10|[1-9])|\\((10|[1-9])\\))");

  private static final Set<Integer> STRING_TOKEN_TYPES = Set.of(
    BSLParser.STRING,
    BSLParser.STRINGPART,
    BSLParser.STRINGSTART,
    BSLParser.STRINGTAIL
  );

  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();

    var visitor = new NStrAndStrTemplateVisitor(entries, helper);
    visitor.visit(documentContext.getAst());

    return entries;
  }

  /**
   * Visitor for finding NStr and StrTemplate method calls.
   */
  private static class NStrAndStrTemplateVisitor extends BSLParserBaseVisitor<Void> {
    private final List<SemanticTokenEntry> entries;
    private final SemanticTokensHelper helper;

    public NStrAndStrTemplateVisitor(List<SemanticTokenEntry> entries, SemanticTokensHelper helper) {
      this.entries = entries;
      this.helper = helper;
    }

    @Override
    public Void visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
      String methodName = ctx.methodName().getText();

      if (NSTR_METHOD_PATTERN.matcher(methodName).matches()) {
        processNStrCall(ctx);
      } else if (TEMPLATE_METHOD_PATTERN.matcher(methodName).matches()) {
        processStrTemplateCall(ctx);
      }

      return super.visitGlobalMethodCall(ctx);
    }

    private void processNStrCall(BSLParser.GlobalMethodCallContext ctx) {
      var callParams = ctx.doCall().callParamList().callParam();
      if (callParams.isEmpty()) {
        return;
      }

      // Get the first parameter (the multilingual string)
      var firstParam = callParams.get(0);
      var stringTokens = getStringTokens(firstParam);

      for (Token token : stringTokens) {
        String tokenText = token.getText();
        int tokenLine = token.getLine() - 1; // Convert to 0-indexed
        int tokenStart = token.getCharPositionInLine();

        // Find language keys in the string
        Matcher matcher = NSTR_LANG_KEY_PATTERN.matcher(tokenText);
        while (matcher.find()) {
          String langKey = matcher.group(1);
          int keyStart = matcher.start(1);
          int keyLength = langKey.length();

          // Add semantic token for the language key
          helper.addEntry(
            entries,
            tokenLine,
            tokenStart + keyStart,
            keyLength,
            SemanticTokenTypes.Property
          );
        }
      }
    }

    private void processStrTemplateCall(BSLParser.GlobalMethodCallContext ctx) {
      var callParams = ctx.doCall().callParamList().callParam();
      if (callParams.isEmpty()) {
        return;
      }

      // Get the first parameter (the template string)
      var firstParam = callParams.get(0);
      var stringTokens = getStringTokens(firstParam);

      for (Token token : stringTokens) {
        String tokenText = token.getText();
        int tokenLine = token.getLine() - 1; // Convert to 0-indexed
        int tokenStart = token.getCharPositionInLine();

        // Find placeholders in the string
        Matcher matcher = STR_TEMPLATE_PLACEHOLDER_PATTERN.matcher(tokenText);
        while (matcher.find()) {
          int placeholderStart = matcher.start();
          int placeholderLength = matcher.group().length();

          // Add semantic token for the placeholder
          helper.addEntry(
            entries,
            tokenLine,
            tokenStart + placeholderStart,
            placeholderLength,
            SemanticTokenTypes.Parameter
          );
        }
      }
    }

    private List<Token> getStringTokens(BSLParser.CallParamContext callParam) {
      List<Token> stringTokens = new ArrayList<>();
      var tokens = Trees.getTokens(callParam);

      for (Token token : tokens) {
        if (STRING_TOKEN_TYPES.contains(token.getType())) {
          stringTokens.add(token);
        }
      }

      return stringTokens;
    }
  }
}
