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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class DeprecatedHttpConnectionMethodDiagnosticTest
  extends AbstractDiagnosticTest<DeprecatedHttpConnectionMethodDiagnostic> {

  DeprecatedHttpConnectionMethodDiagnosticTest() {
    super(DeprecatedHttpConnectionMethodDiagnostic.class);
  }

  @Test
  void testOnArrayDoesNotFire() {
    initServerContext(TestUtils.PATH_TO_METADATA);
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    // Массив.Получить/Удалить совпадают по имени, но TypeService
    // резолвит владельца как Массив (не HTTPСоединение) → 0 срабатываний.
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testServerModuleDoesNotFire() {
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.ObjectModule);
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    // Серверный модуль — visitFile возвращает ctx без обхода → 0.
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testServerDirectiveDoesNotFireButClientFires() {
    var typeService = mock(TypeService.class);
    var owner = new TypeRef(TypeKind.PLATFORM, "HTTPСоединение");
    var typedMember = new TypeService.TypedMember(
      owner,
      MemberDescriptor.method("Получить"),
      Ranges.create(0, 0, 8),
      0
    );
    when(typeService.memberAt(any(DocumentContext.class), any(TerminalNode.class)))
      .thenReturn(Optional.of(typedMember));

    var diagnostic = new DeprecatedHttpConnectionMethodDiagnostic(typeService);
    diagnostic.setInfo(diagnosticInstance.getInfo());

    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);

    List<Diagnostic> diagnostics = diagnostic.getDiagnostics(documentContext);

    // Фикстура: &НаКлиенте метод (срабатывает), &НаСервере и
    // &НаСервереБезКонтекста (не срабатывают) → ровно 1 диагностика.
    assertThat(diagnostics).hasSize(1);
  }
}
