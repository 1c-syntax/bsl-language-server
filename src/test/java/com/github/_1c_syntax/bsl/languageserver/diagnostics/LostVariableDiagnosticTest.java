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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class LostVariableDiagnosticTest extends AbstractDiagnosticTest<LostVariableDiagnostic> {
  LostVariableDiagnosticTest() {
    super(LostVariableDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasMessageOnRange(getMessage("Значение"), 2, 4, 12)
      .hasMessageOnRange(getMessageUnused("Значение"), 3, 4, 12)
      .hasMessageOnRange(getMessage("МояПеременная"), 4, 4, 17)
      .hasMessageOnRange(getMessageUnused("МояПеременная"), 5, 4, 17)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 9, 4, 16)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 23, 4, 16)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 31, 4, 16)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 53, 7, 19)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 69, 2, 14)
      .hasMessageOnRange(getMessageUnused("Запрос"), 82, 2, 8)
      .hasMessageOnRange(getMessageUnused("Запрос"), 95, 2, 8)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 99, 2, 14)
      .hasMessageOnRange(getMessageUnused("ТекстЗапроса"), 101, 6, 18)
      .hasMessageOnRange(getMessage("Файл"), 111, 4, 8)
      .hasMessageOnRange(getMessageUnused("Файл"), 116, 4, 8)
      .hasMessageOnRange(getMessageUnused("ЛокальнаяПеременная"), 127, 8, 27)
      .hasMessageOnRange(getMessage("Комментарий"), 139, 4, 15)
      .hasMessageOnRange(getMessageUnused("Комментарий"), 139, 21, 32)
      .hasMessageOnRange(getMessage("ВидПрава"), 159, 4, 12)
      .hasMessageOnRange(getMessageUnused("ВидПрава"), 160, 4, 12)
      .hasMessageOnRange(getMessage("НовыйПереход"), 163, 4, 16)
      .hasMessageOnRange(getMessageUnused("НовыйПереход"), 164, 4, 16)
      .hasMessageOnRange(getMessage("ЭтоОшибкаБлокировки"), 188, 8, 27)
      .hasMessageOnRange(getMessageUnused("ЭтоОшибкаБлокировки"), 210, 8, 27)
      .hasMessageOnRange(getMessageUnused("мСохраненныйДок"), 243, 4, 19)
      .hasMessageOnRange(getMessage("Представление"), 254, 4, 17)
      .hasMessageOnRange(getMessage("ЛишниеТэги"), 275, 8, 18)
      .hasMessageOnRange(getMessageUnused("ЛишниеТэги"), 276, 8, 18)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 297, 4, 16)
      .hasMessageOnRange(getMessage("ЗначениеМодуля"), 305, 4, 18)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 311, 4, 16)
      .hasMessageOnRange(getMessageUnused("ТекстЗапроса"), 314, 4, 16)
      .hasMessageOnRange(getMessage("ЗначениеМодуля"), 318, 4, 18)
      //.hasMessageOnRange(getMessageUnused("Значение"), 329, 8, 16) // TODO не ошибка
      .hasMessageOnRange(getMessage("Элем22"), 335, 16, 22)
      .hasMessageOnRange(getMessage("Значение23"), 344, 12, 22)
      .hasMessageOnRange(getMessageUnused("Значение23"), 345, 12, 22)
      .hasMessageOnRange(getMessage("Значение24"), 354, 12, 22)
      .hasMessageOnRange(getMessage("ТекстЗапросаВБлоке"), 376, 0, 18)
      .hasMessageOnRange(getMessage("ЗначениеМодуля"), 380, 0, 14)
      .hasSize(37);
  }

  String getMessage(String name){
    return String.format("Значение переменной <%s> не используется, переменная перезаписывается дальше по коду", name);
  }

  String getMessageUnused(String name){
    return String.format("Значение переменной <%s> не используется далее", name);
  }
}
