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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMessage;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class UnknownMemberDiagnosticTest extends AbstractDiagnosticTest<UnknownMemberDiagnostic> {
  UnknownMemberDiagnosticTest() {
    super(UnknownMemberDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();

    // Срабатывания: опечатка в члене типа Массив (Добвить) и неизвестный
    // голый вызов. Существующий Массив.Добавить, глобальный Сообщить и
    // литеральные ключи Структуры (ИмяОбъекта/СтароеИмяОбъекта/Успешно) — нет.
    assertThat(diagnostics).hasSize(2);

    var messages = diagnostics.stream()
      .map(d -> DiagnosticMessage.getStringValue(d.getMessage()))
      .toList();
    org.assertj.core.api.Assertions.assertThat(messages)
      // У типа "Массив" нет метода или свойства "Добвить" — подставлено имя
      // типа ресивера в сообщение (отдельный memberMessage от глобального).
      .anyMatch(m -> m.contains("Добвить") && m.contains("Массив"))
      .anyMatch(m -> m.contains("НесуществующийГлобальныйМетод"))
      .noneMatch(m -> m.contains("ИмяОбъекта") || m.contains("Успешно"));
  }
}
