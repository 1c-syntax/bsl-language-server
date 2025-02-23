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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.recognizer.BSLFootprint;
import com.github._1c_syntax.bsl.languageserver.recognizer.CodeRecognizer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
public class CommentedCodeDiagnostic extends AbstractDiagnostic implements QuickFixProvider {

  private static final float COMMENTED_CODE_THRESHOLD = 0.9F;
  private static final String COMMENT_START = "//";
  private static final int MINIMAL_TOKEN_COUNT = 2;

  @DiagnosticParameter(
    type = Float.class,
    defaultValue = "" + COMMENTED_CODE_THRESHOLD
  )
  private float threshold = COMMENTED_CODE_THRESHOLD;

  @DiagnosticParameter(
    type = String.class
  )
  private Set<String> exclusionPrefixes = Collections.emptySet();

  private List<MethodDescription> methodDescriptions;
  private CodeRecognizer codeRecognizer;

  public CommentedCodeDiagnostic() {
    codeRecognizer = new CodeRecognizer(threshold, new BSLFootprint());
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    threshold = ((Number) configuration.getOrDefault("threshold", threshold)).floatValue();
    codeRecognizer = new CodeRecognizer(threshold, new BSLFootprint());

    var excludePrefixesString = (String) configuration.getOrDefault("exclusionPrefixes", "");
    exclusionPrefixes = Arrays.stream(excludePrefixesString.split(","))
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.toSet());
  }

  @Override
  public void check() {
    methodDescriptions = documentContext.getSymbolTree().getMethods()
      .stream()
      .map(MethodSymbol::getDescription)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());

    groupComments(documentContext.getComments())
      .stream()
      .filter(this::isCommentGroupNotMethodDescription)
      .forEach(this::checkCommentGroup);
  }

  private List<List<Token>> groupComments(List<Token> comments) {
    List<List<Token>> groups = new ArrayList<>();
    List<Token> currentGroup = null;

    for (Token comment : comments) {
      if (currentGroup == null) {
        currentGroup = initNewGroup(comment);
      } else if (isAdjacent(comment, currentGroup)) {
        currentGroup.add(comment);
      } else {
        groups.add(currentGroup);
        currentGroup = initNewGroup(comment);
      }
    }

    if (currentGroup != null) {
      groups.add(currentGroup);
    }

    return groups;
  }

  private static List<Token> initNewGroup(Token comment) {
    List<Token> group = new ArrayList<>();
    group.add(comment);
    return group;
  }

  private boolean isAdjacent(Token comment, List<Token> currentGroup) {
    var last = currentGroup.get(currentGroup.size() - 1);
    return last.getLine() + 1 == comment.getLine()
      && onlyEmptyDelimiters(last.getTokenIndex(), comment.getTokenIndex());
  }

  private boolean onlyEmptyDelimiters(int firstTokenIndex, int lastTokenIndex) {
    if (firstTokenIndex > lastTokenIndex) {
      return false;
    }

    for (int index = firstTokenIndex + 1; index < lastTokenIndex; index++) {
      int tokenType = documentContext.getTokens().get(index).getType();
      if (tokenType != BSLParser.WHITE_SPACE) {
        return false;
      }
    }

    return true;
  }

  private boolean isCommentGroupNotMethodDescription(List<Token> commentGroup) {
    if (methodDescriptions.isEmpty()) {
      return true;
    }

    final var first = commentGroup.get(0);
    final var last = commentGroup.get(commentGroup.size() - 1);

    return methodDescriptions.stream().noneMatch(methodDescription -> methodDescription.contains(first, last));
  }

  private void checkCommentGroup(List<Token> commentGroup) {
    var firstComment = commentGroup.get(0);
    var lastComment = commentGroup.get(commentGroup.size() - 1);

    for (var comment : commentGroup) {
      if (isTextParsedAsCode(comment.getText())) {
        diagnosticStorage.addDiagnostic(firstComment, lastComment);
        return;
      }
    }
  }

  private boolean isTextParsedAsCode(String text) {
    var uncommented = uncomment(text);

    for (var prefix : exclusionPrefixes) {
      if (uncommented.startsWith(prefix)) {
        return false;
      }
    }
    if (!codeRecognizer.meetsCondition(text)) {
      return false;
    }

    var tokenizer = new BSLTokenizer(uncommented);
    final var tokens = tokenizer.getTokens();

    // Если меньше двух токенов нет смысла анализировать - это код
    if (tokens.size() >= MINIMAL_TOKEN_COUNT) {

      var tokenTypes = tokens.stream()
        .map(Token::getType)
        .filter(t -> t != BSLParser.WHITE_SPACE)
        .toList();

      // Если два идентификатора идут подряд - это не код
      for (int i = 0; i < tokenTypes.size() - 1; i++) {
        if (tokenTypes.get(i) == BSLParser.IDENTIFIER && tokenTypes.get(i + 1) == BSLParser.IDENTIFIER) {
          return false;
        }
      }
    }

    return true;
  }

  private static String uncomment(String comment) {
    if (comment.startsWith(COMMENT_START)) {
      return uncomment(comment.substring(COMMENT_START.length()));
    }
    return comment;
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics, CodeActionParams params, DocumentContext documentContext
  ) {

    var textEdits = diagnostics.stream()
      .map(Diagnostic::getRange)
      .map(range -> new TextEdit(range, ""))
      .toList();

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }
}
