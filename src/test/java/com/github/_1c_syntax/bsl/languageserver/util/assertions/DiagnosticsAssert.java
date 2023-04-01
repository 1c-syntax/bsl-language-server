/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.util.Lists;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;

import java.util.List;

public class DiagnosticsAssert extends AbstractListAssert<DiagnosticsAssert, List<Diagnostic>, Diagnostic, DiagnosticAssert> {

  private final DiagnosticAssertFactory assertFactory = new DiagnosticAssertFactory();

  public DiagnosticsAssert(List<Diagnostic> actual) {
    super(actual, DiagnosticsAssert.class);
  }

  public DiagnosticsAssert hasRange(int startLine, int startChar, int endLine, int endChar) {

    return anySatisfy(diagnostic ->
      assertFactory.createAssert(diagnostic).hasRange(startLine, startChar, endLine, endChar)
    );

  }

  public DiagnosticsAssert hasRange(int startLine, int startChar, int endChar) {

    return anySatisfy(diagnostic ->
      assertFactory.createAssert(diagnostic).hasRange(startLine, startChar, startLine, endChar)
    );

  }

  /**
   * Ассерт для проверки совпадения диапазона и сообщения
   *
   * @param message   Сообщение диагностики
   * @param startLine Первая строка диапазона
   * @param startChar Первый символ диапазона
   * @param endLine   Последняя строка диапазона
   * @param endChar   Последний символ диапазона
   * @return Ссылка на объект для текучести
   */
  public DiagnosticsAssert hasMessageOnRange(String message, int startLine, int startChar, int endLine, int endChar) {
    return anySatisfy(diagnostic ->
      assertFactory.createAssert(diagnostic).hasMessageOnRange(message, startLine, startChar, endLine, endChar)
    );
  }

  /**
   * Ассерт для проверки совпадения диапазона-строки и сообщения
   *
   * @param message   Сообщение диагностики
   * @param lineNo    Номер строки диапазона
   * @param startChar Первый символ диапазона
   * @param endChar   Последний символ диапазона
   * @return Ссылка на объект для текучести
   */
  public DiagnosticsAssert hasMessageOnRange(String message, int lineNo, int startChar, int endChar) {
    return anySatisfy(diagnostic ->
      assertFactory.createAssert(diagnostic).hasMessageOnRange(message, lineNo, startChar, lineNo, endChar)
    );
  }

  /**
   * Ассерт для проверки совпадения диапазона-строки и сообщения
   *
   * @param lineNo    Номер строки диапазона
   * @param startChar Первый символ диапазона
   * @param endChar   Последний символ диапазона
   * @param message   Сообщение диагностики
   * @param relatedLocationRanges   Список связанных диапазонов
   * @return Ссылка на объект для текучести
   */
  public DiagnosticsAssert hasIssueOnRange(int lineNo, int startChar, int endChar, String message,
                                           List<Range> relatedLocationRanges) {
    return hasIssueOnRange(lineNo, startChar, lineNo, endChar,
        message, relatedLocationRanges);
  }

  /**
   * Ассерт для проверки совпадения диапазона-строки и сообщения
   *
   * @param startLine    Номер строки диапазона
   * @param startChar Первый символ диапазона
   * @param endLine   Последняя строка диапазона
   * @param endChar   Последний символ диапазона
   * @param message   Сообщение диагностики
   * @param relatedLocationRanges   Список связанных диапазонов
   * @return Ссылка на объект для текучести
   */
  public DiagnosticsAssert hasIssueOnRange(int startLine, int startChar, int endLine, int endChar, String message,
                                           List<Range> relatedLocationRanges) {
    return anySatisfy(diagnostic ->
      assertFactory.createAssert(diagnostic).hasIssueOnRange(Ranges.create(startLine, startChar, endLine, endChar),
        message, relatedLocationRanges)
    );
  }

  @Override
  protected DiagnosticAssert toAssert(Diagnostic value, String description) {
    return assertFactory.createAssert(value);
  }

  @Override
  protected DiagnosticsAssert newAbstractIterableAssert(Iterable<? extends Diagnostic> iterable) {
    return new DiagnosticsAssert(Lists.newArrayList(iterable));
  }
}
