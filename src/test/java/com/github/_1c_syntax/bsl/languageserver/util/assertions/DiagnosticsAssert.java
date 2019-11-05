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
package com.github._1c_syntax.bsl.languageserver.util.assertions;

import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.util.Lists;
import org.eclipse.lsp4j.Diagnostic;

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

  @Override
  protected DiagnosticAssert toAssert(Diagnostic value, String description) {
    return assertFactory.createAssert(value);
  }

  @Override
  protected DiagnosticsAssert newAbstractIterableAssert(Iterable<? extends Diagnostic> iterable) {
    return new DiagnosticsAssert(Lists.newArrayList(iterable));
  }
}
