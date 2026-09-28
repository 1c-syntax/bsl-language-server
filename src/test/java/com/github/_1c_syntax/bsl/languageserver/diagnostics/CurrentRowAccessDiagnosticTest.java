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

  private static TypeRef tableRef(String name) {
    return new TypeRef(TypeKind.CONFIGURATION, name);
  }

  private CurrentRowAccessDiagnostic diagnosticWith(TypeSet receiverTypes, TypeRef... extensions) {
    var typeService = mock(TypeService.class);
    when(typeService.receiverTypesAt(any(DocumentContext.class), any(Position.class)))
      .thenReturn(receiverTypes);
    for (var type : receiverTypes.refs()) {
      when(typeService.extensionsOf(type)).thenReturn(List.of(extensions));
    }
    var diagnostic = new CurrentRowAccessDiagnostic(typeService);
    diagnostic.setInfo(diagnosticInstance.getInfo());
    return diagnostic;
  }

  @Test
  void testDynamicListTableFires() {
    // Ресивер типизирован базовым видом данных (колонки строки неизвестны)
    var diagnostic = diagnosticWith(TypeSet.of(tableRef("ТаблицаФормы.ДинамическийСписок")));
    List<Diagnostic> diagnostics = diagnostic.getDiagnostics(getDocumentContext());

    // 3 обращения к .ТекущаяСтрока с dereference → все срабатывают;
    // Список.ТекущаяСтрока без dereference → молчит
    assertThat(diagnostics).hasSize(3);
  }

  @Test
  void testPerFormTableWithDynamicListExtensionFires() {
    // Реальный прод-кейс: пер-форма тип ТаблицаФормы.<mdoRef>.<имя элемента>,
    // наследующий вид данных через расширение
    var receiver = tableRef("ТаблицаФормы.Документ.Документ1.Форма.ФормаДокумента.Список");
    var diagnostic = diagnosticWith(TypeSet.of(receiver),
      tableRef("ТаблицаФормы"), tableRef("ТаблицаФормы.ДинамическийСписок"));
    List<Diagnostic> diagnostics = diagnostic.getDiagnostics(getDocumentContext());

    assertThat(diagnostics).hasSize(3);
  }

  @Test
  void testTabularSectionDoesNotFire() {
    var diagnostic = diagnosticWith(TypeSet.of(tableRef("ТаблицаФормы.ТабличнаяЧасть")));
    List<Diagnostic> diagnostics = diagnostic.getDiagnostics(getDocumentContext());

    // Таблица над табличной частью — базу не дёргает
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testPerFormTabularSectionDoesNotFire() {
    var receiver = tableRef("ТаблицаФормы.Документ.Документ1.Форма.ФормаДокумента.ТабличнаяЧасть1");
    var diagnostic = diagnosticWith(TypeSet.of(receiver),
      tableRef("ТаблицаФормы"), tableRef("ТаблицаФормы.ТабличнаяЧасть"));
    List<Diagnostic> diagnostics = diagnostic.getDiagnostics(getDocumentContext());

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
