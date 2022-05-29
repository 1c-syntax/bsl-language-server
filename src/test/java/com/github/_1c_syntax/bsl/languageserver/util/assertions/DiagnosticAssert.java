/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.util.assertions;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.assertj.core.api.AbstractAssert;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.Objects;

public class DiagnosticAssert extends AbstractAssert<DiagnosticAssert, Diagnostic> {

  public DiagnosticAssert(Diagnostic actual) {
    super(actual, DiagnosticAssert.class);
  }

  public static DiagnosticAssert assertThat(Diagnostic actual) {
    return new DiagnosticAssert(actual);
  }

  public DiagnosticAssert hasRange(int startLine, int startChar, int endLine, int endChar) {
    // check that actual TolkienCharacter we want to make assertions on is not null.
    isNotNull();

    // check condition
    Range expectedRange = Ranges.create(startLine, startChar, endLine, endChar);
    Range actualRange = actual.getRange();
    if (!Objects.equals(actualRange, expectedRange)) {
      failWithMessage("Expected diagnostic's range to be <%s> but was <%s>", expectedRange.toString(), actualRange.toString());
    }

    // return the current assertion for method chaining
    return this;
  }

  /**
   * Проверка на совпадение сообщения диагностики и диапазона текста, где она обнаружена
   *
   * @param message   Сообщение диагностики
   * @param startLine Первая строка диапазона
   * @param startChar Первый символ диапазона
   * @param endLine   Последняя строка диапазона
   * @param endChar   Последний символ диапазона
   * @return Ссылка на объект для текучести
   */
  public DiagnosticAssert hasMessageOnRange(String message, int startLine, int startChar, int endLine, int endChar) {
    // check that actual TolkienCharacter we want to make assertions on is not null.
    isNotNull();

    // check condition
    Range expectedRange = Ranges.create(startLine, startChar, endLine, endChar);
    Range actualRange = actual.getRange();
    if (!Objects.equals(actualRange, expectedRange)) {
      failWithMessage("Expected diagnostic's range to be <%s> but was <%s>", expectedRange.toString(), actualRange.toString());
    }

    if (!Objects.equals(message, actual.getMessage())) {
      failWithMessage("Expected diagnostic's message to be <%s> but was <%s>", message, actual.getMessage());
    }

    // return the current assertion for method chaining
    return this;
  }

  /**
   * Проверка на совпадение сообщения диагностики и диапазона текста, где она обнаружена
   *
   * @param expectedRange Первая строка диапазона
   * @param expectedMessage   Сообщение диагностики
   * @param expectedRelatedInformation   Список связанных диапазонов
   * @return Ссылка на объект для текучести
   */
  public DiagnosticAssert hasIssueOnRange(Range expectedRange, String expectedMessage, List<Range> expectedRelatedInformation) {
    // check that actual TolkienCharacter we want to make assertions on is not null.
    isNotNull();

    // check condition
    Range actualRange = actual.getRange();
    if (!Objects.equals(actualRange, expectedRange)) {
      failWithMessage("Expected diagnostic's range to be <%s> but was <%s>", expectedRange.toString(), actualRange.toString());
    }

    if (!Objects.equals(expectedMessage, actual.getMessage())) {
      failWithMessage("Expected diagnostic's expectedMessage to be <%s> but was <%s>", expectedMessage, actual.getMessage());
    }

    List<DiagnosticRelatedInformation> actualRelatedInformation = actual.getRelatedInformation();
    if (expectedRelatedInformation.size() != actualRelatedInformation.size()){
      failWithMessage("Expected size of diagnostic's RelatedInformation to be <%d> but was <%d>",
        expectedRelatedInformation.size(), actualRelatedInformation.size());
  }

    for (int i = 0; i < expectedRelatedInformation.size(); i++) {
      var actualElem = actualRelatedInformation.get(i);
      final var actualRelatedRange = actualElem.getLocation().getRange();
      var expectedRelatedRange = expectedRelatedInformation.get(i);
      if (!Objects.equals(actualRelatedRange, expectedRelatedRange)) {
        failWithMessage("Expected diagnostic's actualRange to be <%s> but was <%s>", expectedRelatedRange.toString(), actualRelatedRange.toString());
      }
    }

    // return the current assertion for method chaining
    return this;
  }
}
