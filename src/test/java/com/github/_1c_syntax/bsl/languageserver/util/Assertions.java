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
package com.github._1c_syntax.bsl.languageserver.util;

import com.github._1c_syntax.bsl.languageserver.util.assertions.CodeActionAssert;
import com.github._1c_syntax.bsl.languageserver.util.assertions.ColorInformationsAssert;
import com.github._1c_syntax.bsl.languageserver.util.assertions.ColorPresentationsAssert;
import com.github._1c_syntax.bsl.languageserver.util.assertions.DiagnosticAssert;
import com.github._1c_syntax.bsl.languageserver.util.assertions.DiagnosticsAssert;
import com.github._1c_syntax.bsl.languageserver.util.assertions.FoldingRangeAssert;
import com.github._1c_syntax.bsl.languageserver.util.assertions.FoldingRangesAssert;
import com.github._1c_syntax.bsl.languageserver.util.assertions.SelectionRangesAssert;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.SelectionRange;

import java.util.List;

@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public class Assertions extends org.assertj.core.api.Assertions {

  public static DiagnosticAssert assertThat(Diagnostic actual) {
    return new DiagnosticAssert(actual);
  }

  public static CodeActionAssert assertThat(CodeAction actual) {
    return new CodeActionAssert(actual);
  }

  public static FoldingRangeAssert assertThat(FoldingRange actual) {
    return new FoldingRangeAssert(actual);
  }

  public static DiagnosticsAssert assertThat(List<Diagnostic> actual, Object ignored) {
    return new DiagnosticsAssert(actual);
  }

  public static FoldingRangesAssert assertThatFoldingRanges(List<FoldingRange> actual) {
    return new FoldingRangesAssert(actual);
  }

  public static SelectionRangesAssert assertThatSelectionRanges(List<SelectionRange> actual) {
    return new SelectionRangesAssert(actual);
  }

  public static ColorInformationsAssert assertThatColorInformations(List<ColorInformation> actual) {
    return new ColorInformationsAssert(actual);
  }

  public static ColorPresentationsAssert assertThatColorPresentations(List<ColorPresentation> actual) {
    return new ColorPresentationsAssert(actual);
  }
}
