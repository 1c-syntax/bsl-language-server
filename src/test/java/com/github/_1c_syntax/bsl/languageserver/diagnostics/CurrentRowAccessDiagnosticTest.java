/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentRowAccessDiagnosticTest
  extends AbstractDiagnosticTest<CurrentRowAccessDiagnostic> {

  CurrentRowAccessDiagnosticTest() {
    super(CurrentRowAccessDiagnostic.class);
  }

  private static TypeRef tableRef(String suffix) {
    return new TypeRef(TypeKind.CONFIGURATION, "ТаблицаФормы." + suffix);
  }

  private CurrentRowAccessDiagnostic diagnosticWith(TypeSet receiverTypes) {
    var typeService = mock(TypeService.class);
    when(typeService.receiverTypesAt(any(DocumentContext.class), any(Position.class)))
      .thenReturn(receiverTypes);
    var diagnostic = new CurrentRowAccessDiagnostic(typeService);
    diagnostic.setInfo(diagnosticInstance.getInfo());
    return diagnostic;
  }

  @Test
  void testDynamicListTableFires() {
    var diagnostic = diagnosticWith(TypeSet.of(tableRef("ДинамическийСписок")));
    List<Diagnostic> diagnostics = diagnostic.getDiagnostics(getDocumentContext());

    // 3 обращения к .ТекущаяСтрока с dereference → все срабатывают
    assertThat(diagnostics).hasSize(3);
  }

  @Test
  void testTabularSectionDoesNotFire() {
    var diagnostic = diagnosticWith(TypeSet.of(tableRef("ТабличнаяЧасть")));
    List<Diagnostic> diagnostics = diagnostic.getDiagnostics(getDocumentContext());

    // Таблица над табличной частью — базу не дёргает
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testUnknownReceiverDoesNotFire() {
    var diagnostic = diagnosticWith(TypeSet.EMPTY);
    List<Diagnostic> diagnostics = diagnostic.getDiagnostics(getDocumentContext());

    // Тип ресивера неизвестен — не подтверждаем динамический список
    assertThat(diagnostics).isEmpty();
  }
}
