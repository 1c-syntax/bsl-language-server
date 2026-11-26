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
import org.springframework.beans.factory.annotation.Autowired;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;


class UsingFindElementByStringDiagnosticTest extends AbstractDiagnosticTest<UsingFindElementByStringDiagnostic> {

  UsingFindElementByStringDiagnosticTest() {
    super(UsingFindElementByStringDiagnostic.class);
  }

  @Autowired
  private TypeRegistry registry;

  @Test
  void resolvedUserMethodDoesNotImpersonateManager() {
    initServerContext();
    var owner = registry.registerConfigurationType("ПользовательскийТип");
    var method = new MemberDescriptor(new BilingualString("НайтиПоНаименованию", "FindByDescription"),
      MemberKind.METHOD, BilingualString.EMPTY, TypeSet.EMPTY, List.of(), null, false,
      PlatformMetadata.EMPTY, false, false);
    registry.registerMemberOverride(owner, () -> List.of(method), FileType.BSL);
    var document = TestUtils.getDocumentContext("""
      // Параметры:
      //  Объект - ПользовательскийТип - пользовательский объект.
      Процедура Проверка(Объект)
        Объект.НайтиПоНаименованию("Литерал");
        Объект.FindByDescription("Literal");
      КонецПроцедуры
      """, context);
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void resolvedManagersRequireExplicitSearchOptions() {
    initServerContext(Path.of("src/test/resources/metadata/designer"));
    var document = TestUtils.getDocumentContext("""
      Процедура Проверка(Имя, Номер, Дата)
        Справочники.Справочник1.НайтиПоНаименованию(Имя);
        Справочники.Справочник1.НайтиПоНаименованию("Текст");
        Справочники.Справочник1.НайтиПоНаименованию(Имя, , , );
        Справочники.Справочник1.НайтиПоНаименованию(Имя, Истина);
        Справочники.Справочник1.НайтиПоНаименованию(Имя, Ложь);
        Документы.Документ1.НайтиПоНомеру(Номер);
        Документы.Документ1.НайтиПоНомеру(Номер, );
        Документы.Документ1.НайтиПоНомеру(Номер, Дата);
        Неизвестный.НайтиПоНаименованию(Имя);
        Справочники.Справочник1.НайтиПоНаименованию("Текст", Истина);
      КонецПроцедуры
      """, context);
    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(6);
    var info = diagnosticInstance.getInfo();
    var exactMatch = info.getResourceString("missingExactMatch", "НайтиПоНаименованию");
    var intervalDate = info.getResourceString("missingPeriod", "НайтиПоНомеру");
    assertThat(diagnostics, true)
      .hasMessageOnRange(exactMatch, 1, 26, 50)
      .hasMessageOnRange(exactMatch, 2, 26, 54)
      .hasMessageOnRange(exactMatch, 3, 26, 56)
      .hasMessageOnRange(intervalDate, 6, 22, 42)
      .hasMessageOnRange(intervalDate, 7, 22, 44)
      .hasMessageOnRange(info.getMessage("НайтиПоНаименованию"), 10, 26, 62);
  }

  @Test
  void englishCallsAndExplicitOptions() {
    initServerContext(Path.of("src/test/resources/metadata/designer"));
    // The minimal built-in context used by tests omits English method aliases.
    // Keep the real configuration owners and platform signatures.
    registerEnglishAlias("СправочникМенеджер.Справочник1", "НайтиПоНаименованию", "FindByDescription");
    registerEnglishAlias("ДокументМенеджер.Документ1", "НайтиПоНомеру", "FindByNumber");
    var document = TestUtils.getDocumentContext("""
      Procedure Check(Name, Number, Date)
        Catalogs.Справочник1.FindByDescription(Name);
        Documents.Документ1.FindByNumber(Number);
        Catalogs.Справочник1.FindByDescription(Name, False);
        Documents.Документ1.FindByNumber(Number, Date);
        Unknown.FindByDescription(Name);
        Unknown.FindByNumber(Number);
      EndProcedure
      """, context);
    var diagnostics = getDiagnostics(document);
    assertThat(diagnostics).hasSize(2);
    var info = diagnosticInstance.getInfo();
    assertThat(diagnostics, true)
      .hasMessageOnRange(info.getResourceString("missingExactMatch", "FindByDescription"), 1, 23, 46)
      .hasMessageOnRange(info.getResourceString("missingPeriod", "FindByNumber"), 2, 22, 42);
  }

  private void registerEnglishAlias(String ownerName, String nameRu, String nameEn) {
    var owner = registry.resolve(ownerName).orElseThrow();
    var method = registry.getMembers(owner, FileType.BSL).stream()
      .filter(member -> member.matches(nameRu)).findFirst().orElseThrow()
      .withBilingualName(new BilingualString(nameRu, nameEn));
    registry.registerMemberOverride(owner, () -> List.of(method), FileType.BSL);
  }

  @Test
  void runTest() {

    // When
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(9);
    assertThat(diagnostics, true)
      .hasRange(7, 38, 7, 78)
      .hasRange(9, 40, 9, 61)
      .hasRange(13, 27, 13, 59)
      .hasRange(24, 35, 24, 53)
      .hasRange(27, 35, 27, 51)
      .hasRange(30, 27, 30, 53)
      .hasRange(39, 67, 110)
      .hasRange(41, 35, 53)
      .hasRange(44, 29, 49)
    ;
  }
}
