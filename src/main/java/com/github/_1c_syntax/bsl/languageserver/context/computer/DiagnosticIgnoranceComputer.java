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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import lombok.AllArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.apache.commons.lang3.Range;
import org.eclipse.lsp4j.Diagnostic;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiagnosticIgnoranceComputer implements Computer<DiagnosticIgnoranceComputer.Data> {

  private static final String ALL_DIAGNOSTICS_KEY = "all";

  private static final Pattern IGNORE_ALL_ON = Pattern.compile(
    "(?:АПК-вкл)|(?:ACC-on)"
  );

  private static final Pattern IGNORE_ALL_OFF = Pattern.compile(
    "(?:АПК-выкл)|(?:ACC-off)"
  );

  private static final Pattern IGNORE_DIAGNOSTIC_ON = Pattern.compile(
    "(?:АПК:(\\w+)-вкл)|(?:ACC:(\\w+)-on)"
  );

  private static final Pattern IGNORE_DIAGNOSTIC_OFF = Pattern.compile(
    "(?:АПК:(\\w+)-выкл)|(?:ACC:(\\w+)-off)"
  );

  private final DocumentContext documentContext;

  private Map<String, List<Range<Integer>>> diagnosticIgnorance = new HashMap<>();
  private Map<String, Deque<Integer>> ignoranceStack = new HashMap<>();

  public DiagnosticIgnoranceComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public Data compute() {

    diagnosticIgnorance.clear();
    ignoranceStack.clear();

    List<Token> comments = documentContext.getComments();

    for (Token comment : comments) {
      checkIgnoreOff(IGNORE_ALL_OFF, comment);
      checkIgnoreOn(IGNORE_ALL_ON, comment);

      checkIgnoreOff(IGNORE_DIAGNOSTIC_OFF, comment);
      checkIgnoreOn(IGNORE_DIAGNOSTIC_ON, comment);
    }

    return new Data(diagnosticIgnorance);
  }

  private void checkIgnoreOff(
    Pattern ignoreOff,
    Token comment) {

    Matcher matcher = ignoreOff.matcher(comment.getText());
    if (!matcher.find()) {
      return;
    }

    String key = getKey(matcher);

    Deque<Integer> stack = ignoranceStack.computeIfAbsent(key, s -> new ArrayDeque<>());
    stack.push(comment.getLine());

  }

  private void checkIgnoreOn(
    Pattern ignoreOn,
    Token comment
  ) {

    Matcher matcher = ignoreOn.matcher(comment.getText());
    if (!matcher.find()) {
      return;
    }

    String key = getKey(matcher);

    Deque<Integer> stack = ignoranceStack.computeIfAbsent(key, s -> new ArrayDeque<>());
    if (stack.isEmpty()) {
      return;
    }

    int ignoreRangeStart = stack.pop();
    int ignoreRangeEnd = comment.getLine();

    Range<Integer> ignoreRange = Range.between(ignoreRangeStart, ignoreRangeEnd);
    final List<Range<Integer>> ranges = diagnosticIgnorance.computeIfAbsent(key, s -> new ArrayList<>());
    ranges.add(ignoreRange);

  }

  private static String getKey(Matcher matcher) {
    String key;
    if (matcher.groupCount() != 0) {
      key = matcher.group(1);
    } else {
      key = ALL_DIAGNOSTICS_KEY;
    }
    return key;
  }

  @AllArgsConstructor
  public static class Data {
    private final Map<String, List<Range<Integer>>> diagnosticIgnorance;

    public boolean diagnosticShouldBeIgnored(Diagnostic diagnostic) {
      int line = diagnostic.getRange().getStart().getLine();

      Predicate<Map.Entry<String, List<Range<Integer>>>> ignoreAll =
        entry -> entry.getKey().equals(ALL_DIAGNOSTICS_KEY);
      Predicate<Map.Entry<String, List<Range<Integer>>>> ignoreConcreteDiagnostic =
        entry -> entry.getKey().equals(diagnostic.getCode());

      return diagnosticIgnorance.entrySet().stream()
        .filter(ignoreAll.or(ignoreConcreteDiagnostic))
        .map(Map.Entry::getValue)
        .flatMap(Collection::stream)
        .anyMatch(range -> range.contains(line));
    }
  }

}
