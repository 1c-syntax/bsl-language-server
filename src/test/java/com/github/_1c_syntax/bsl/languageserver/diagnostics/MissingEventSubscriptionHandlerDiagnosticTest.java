/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@DirtiesContext
class MissingEventSubscriptionHandlerDiagnosticTest extends AbstractDiagnosticTest<MissingEventSubscriptionHandlerDiagnostic> {
  MissingEventSubscriptionHandlerDiagnosticTest() {
    super(MissingEventSubscriptionHandlerDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  @Test
  void test() {

    initServerContext(Absolute.path(PATH_TO_METADATA));
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics)
      .hasSize(6)
      .allMatch(
        diagnostic -> diagnostic.getRange().equals(Ranges.create(0, 0, 7)))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Создайте модуль \"ОбщийПодпискиНаСобытия\" или исправьте некорректный обработчик подписки на событие \"ПриЗаписиСправочника\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Исправьте некорректный обработчик \"CommonModule.ОбщийПодпискиНаСобытия\" у подписки на событие \"ПриЗаписиДокумента\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Добавьте \"Сервер\" модулю \"КлиентскийОбщийМодуль\" или исправьте некорректный обработчик подписки на событие \"ПередЗаписьюДокумента\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Заполните обработчик подписки на событие \"ПередЗаписьюКонстанты\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Создайте процедуру \"ПервыйОбщийМодуль.ПодпискаНаСобытиеПриУстановкеНовогоКода\" или исправьте некорректный обработчик подписки на событие \"ПриУстановкеНовогоКода\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Добавьте \"Экспорт\" процедуре \"ПервыйОбщийМодуль.РегистрацияИзмененийПередУдалением\"  или исправьте некорректный обработчик подписки на событие \"РегистрацияИзмененийПередУдалением\""))

    ;
  }
}
