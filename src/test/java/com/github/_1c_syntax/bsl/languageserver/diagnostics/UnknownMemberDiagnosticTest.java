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

    // Позитивы: опечатка в члене типа Массив (Добвить), неизвестный голый вызов
    // и несуществующий член конкретного ресивера внутри сравнения (НетТакогоМетода).
    // Негативы (молчит): существующий Массив.Добавить, глобальный Сообщить,
    // литеральные ключи Структуры (ИмяОбъекта/СтароеИмяОбъекта/Успешно) и —
    // ключевое для fix ложных срабатываний — обращение к члену у ресивера без
    // выводимого типа внутри сравнения (Параметр.НекийМетод/НекоеСвойство): тип
    // ресивера НЕ должен подменяться типом охватывающего выражения (Булево).
    assertThat(diagnostics).hasSize(3);

    var messages = diagnostics.stream()
      .map(d -> DiagnosticMessage.getStringValue(d.getMessage()))
      .toList();
    org.assertj.core.api.Assertions.assertThat(messages)
      // У типа "Массив" нет метода или свойства "Добвить" — подставлено имя
      // типа ресивера в сообщение (отдельный memberMessage от глобального).
      .anyMatch(m -> m.contains("Добвить") && m.contains("Массив"))
      .anyMatch(m -> m.contains("НесуществующийГлобальныйМетод"))
      // Позитив: несуществующий член конкретного ресивера в контексте сравнения
      // ловится (сравнение не прячет реальный unknown).
      .anyMatch(m -> m.contains("НетТакогоМетода") && m.contains("Массив"))
      .noneMatch(m -> m.contains("ИмяОбъекта") || m.contains("Успешно"))
      // Негатив (fix ложных срабатываний): нетипизированный ресивер в сравнении.
      .noneMatch(m -> m.contains("НекийМетод") || m.contains("НекоеСвойство"));
  }
}
