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

import org.eclipse.lsp4j.Diagnostic;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;


class UsingObjectNotAvailableUnixDiagnosticTest extends AbstractDiagnosticTest<UsingObjectNotAvailableUnixDiagnostic> {
  UsingObjectNotAvailableUnixDiagnosticTest() {
    super(UsingObjectNotAvailableUnixDiagnostic.class);
  }

  @Autowired
  private TypeRegistry registry;

  @Test
  void htmlShellReceiverMustBeResolved() {
    initServerContext();
    // The minimal test platform package does not include HTMLDocumentShell.
    var shell = registry.registerConfigurationType("ОболочкаHTMLДокумента");
    var method = new MemberDescriptor(new BilingualString("ПолучитьCOMОбъект", "GetCOMObject"),
      MemberKind.METHOD, BilingualString.EMPTY, TypeSet.EMPTY, List.of(), null, false,
      PlatformMetadata.EMPTY, false, true);
    registry.registerMemberOverride(shell, () -> List.of(method), FileType.BSL);
    var document = TestUtils.getDocumentContext("""
      // Параметры:
      //  Оболочка - ОболочкаHTMLДокумента - оболочка документа.
      Процедура Проверка(Оболочка, Неизвестный)
        COM = Оболочка.ПолучитьCOMОбъект();
        COM = Оболочка.GetCOMObject();
        COM = Неизвестный.GetCOMObject();
        Пользовательский = Новый Структура("GetCOMObject", 1);
        COM = Пользовательский.GetCOMObject();
        Если ЭтоWindowsКлиент() Тогда
          COM = Оболочка.GetCOMObject();
        КонецЕсли;
      КонецПроцедуры
      """, context);
    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(3, 17, 36)
      .hasRange(4, 17, 31);
  }

  @Test
  void globalCOMCallsAndPlatformGuards() {
    initServerContext();
    var document = TestUtils.getDocumentContext("""
      Процедура Проверка()
        COM = ПолучитьCOMОбъект("", "Excel.Application");
        COM = getcomobject("", "Excel.Application");
        Если ЭтоWindowsКлиент() Тогда
          COM = ПолучитьCOMОбъект("", "Excel.Application");
        КонецЕсли;
        Если НужноОткрыть Тогда
          COM = ПолучитьCOMОбъект("", "WindowsInstaller.Installer");
        ИначеЕсли ЭтоWindowsКлиент() Тогда
          COM = GetCOMObject("", "Excel.Application");
        КонецЕсли;
        Если ПолучитьCOMОбъект("", "WindowsInstaller.Installer") = Неопределено Тогда
          Возврат;
        ИначеЕсли ПолучитьCOMОбъект("", "WindowsInstaller.Installer") = Неопределено Тогда
          Возврат;
        КонецЕсли;
        Если ЭтоWindowsКлиент() Тогда
          Если GetCOMObject("", "Excel.Application") = Неопределено Тогда
            Возврат;
          КонецЕсли;
        КонецЕсли;
        Неизвестный.ПолучитьCOMОбъект();
      КонецПроцедуры
      """, context);
    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(5);
    assertThat(diagnostics, true)
      .hasRange(1, 8, 50)
      .hasRange(2, 8, 45)
      .hasRange(7, 10, 61)
      .hasRange(11, 7, 58)
      .hasRange(13, 12, 63);
  }

  @Test
  void localMethodShadowsGlobalCOMFunction() {
    initServerContext();
    var document = TestUtils.getDocumentContext("""
      Функция ПолучитьCOMОбъект()
        Возврат Неопределено;
      КонецФункции
      Результат = ПолучитьCOMОбъект();
      """, context);
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void test() {

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);

    // then
    assertThat(diagnostics, true)
      .hasRange(3, 11, 3, 54)
      .hasRange(4, 11, 4, 83)
      .hasRange(20, 9, 20, 20);

  }
}
