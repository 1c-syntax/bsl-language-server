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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.AllArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.apache.commons.lang3.Range;
import org.eclipse.lsp4j.Diagnostic;

import javax.annotation.CheckForNull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DiagnosticIgnoranceComputer implements Computer<DiagnosticIgnoranceComputer.Data> {

  private static final DiagnosticCode ALL_DIAGNOSTICS_KEY = new DiagnosticCode("all");

  private static final Pattern IGNORE_ALL_ON = CaseInsensitivePattern.compile(
    "BSLLS-(?:вкл|on)"
  );

  private static final Pattern IGNORE_ALL_OFF = CaseInsensitivePattern.compile(
    "BSLLS-(?:выкл|off)"
  );

  private static final Pattern IGNORE_DIAGNOSTIC_ON = CaseInsensitivePattern.compile(
    "BSLLS:(\\w+)-(?:вкл|on)"
  );

  private static final Pattern IGNORE_DIAGNOSTIC_OFF = CaseInsensitivePattern.compile(
    "BSLLS:(\\w+)-(?:выкл|off)"
  );

  private final DocumentContext documentContext;

  private final Map<DiagnosticCode, List<Range<Integer>>> diagnosticIgnorance = new HashMap<>();
  private final Map<DiagnosticCode, Deque<Integer>> ignoranceStack = new HashMap<>();

  public DiagnosticIgnoranceComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public Data compute() {

    diagnosticIgnorance.clear();
    ignoranceStack.clear();

    List<Token> codeTokens = documentContext.getTokensFromDefaultChannel();
    if (codeTokens.isEmpty()) {
      return new Data(diagnosticIgnorance);
    }
    Set<Integer> codeLines = codeTokens.stream().map(Token::getLine).collect(Collectors.toSet());

    List<Token> comments = documentContext.getComments();

    for (Token comment : comments) {

      // Variable is used for short circuit evaluation.
      //noinspection unused
      boolean ignored = checkTrailingComment(codeLines, comment)
        || checkIgnoreOff(IGNORE_ALL_OFF, comment) != null
        || checkIgnoreOn(IGNORE_ALL_ON, comment)
        || checkIgnoreOff(IGNORE_DIAGNOSTIC_OFF, comment) != null
        || checkIgnoreOn(IGNORE_DIAGNOSTIC_ON, comment);

    }

    int lastTokenLine = codeTokens.get(codeTokens.size() - 1).getLine();
    ignoranceStack.forEach((DiagnosticCode diagnosticKey, Deque<Integer> ignoreRangeStarts) ->
      ignoreRangeStarts.forEach(ignoreRangeStart -> addIgnoredRange(diagnosticKey, ignoreRangeStart, lastTokenLine))
    );

    return new Data(diagnosticIgnorance);
  }

  private boolean checkTrailingComment(Set<Integer> codeLines, Token comment) {
    int commentLine = comment.getLine();
    if (!codeLines.contains(commentLine)) {
      return false;
    }

    DiagnosticCode key = checkIgnoreOff(IGNORE_ALL_OFF, comment);
    if (key == null) {
      key = checkIgnoreOff(IGNORE_DIAGNOSTIC_OFF, comment);
    }
    if (key == null) {
      return false;
    }

    Deque<Integer> stack = ignoranceStack.get(key);
    stack.pop();

    addIgnoredRange(key, commentLine, commentLine);

    return true;
  }

  @CheckForNull
  private DiagnosticCode checkIgnoreOff(
    Pattern ignoreOff,
    Token comment
  ) {

    Matcher matcher = ignoreOff.matcher(comment.getText());
    if (!matcher.find()) {
      return null;
    }

    DiagnosticCode key = getKey(matcher);

    Deque<Integer> stack = ignoranceStack.computeIfAbsent(key, s -> new ArrayDeque<>());
    stack.push(comment.getLine());

    return key;
  }

  private boolean checkIgnoreOn(
    Pattern ignoreOn,
    Token comment
  ) {

    Matcher matcher = ignoreOn.matcher(comment.getText());
    if (!matcher.find()) {
      return false;
    }

    DiagnosticCode key = getKey(matcher);

    Deque<Integer> stack = ignoranceStack.computeIfAbsent(key, s -> new ArrayDeque<>());
    if (stack.isEmpty()) {
      return false;
    }

    int ignoreRangeStart = stack.pop();
    int ignoreRangeEnd = comment.getLine();

    addIgnoredRange(key, ignoreRangeStart, ignoreRangeEnd);

    return true;
  }

  private void addIgnoredRange(DiagnosticCode diagnosticKey, int ignoreRangeStart, int ignoreRangeEnd) {
    // convert antlr4 line numbers (1..n) to lsp (0..n)
    Range<Integer> ignoreRange = Range.between(ignoreRangeStart - 1, ignoreRangeEnd - 1);
    final List<Range<Integer>> ranges = diagnosticIgnorance.computeIfAbsent(diagnosticKey, s -> new ArrayList<>());
    ranges.add(ignoreRange);
  }

  private static DiagnosticCode getKey(Matcher matcher) {
    DiagnosticCode key;
    if (matcher.groupCount() != 0) {
      key = new DiagnosticCode(matcher.group(1));
    } else {
      key = ALL_DIAGNOSTICS_KEY;
    }
    return key;
  }

  @AllArgsConstructor
  public static class Data {
    private final Map<DiagnosticCode, List<Range<Integer>>> diagnosticIgnorance;

    public boolean diagnosticShouldBeIgnored(Diagnostic diagnostic) {
      if (diagnosticIgnorance.isEmpty()) {
        return false;
      }

      int line = diagnostic.getRange().getStart().getLine();

      Predicate<Map.Entry<DiagnosticCode, List<Range<Integer>>>> ignoreAll =
        entry -> entry.getKey().equals(ALL_DIAGNOSTICS_KEY);
      Predicate<Map.Entry<DiagnosticCode, List<Range<Integer>>>> ignoreConcreteDiagnostic =
        entry -> entry.getKey().equals(diagnostic.getCode());

      return diagnosticIgnorance.entrySet().stream()
        .filter(ignoreAll.or(ignoreConcreteDiagnostic))
        .map(Map.Entry::getValue)
        .flatMap(Collection::stream)
        .anyMatch(range -> range.contains(line));
    }
  }

}
