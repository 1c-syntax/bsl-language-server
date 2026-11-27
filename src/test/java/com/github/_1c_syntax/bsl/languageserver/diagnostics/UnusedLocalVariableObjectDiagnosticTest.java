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

import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.Mode;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class UnusedLocalVariableObjectDiagnosticTest extends AbstractServerContextAwareTest {

  private static final Path METADATA = Path.of("src/test/resources/metadata/designer");
  private static final Path OBJECT_MODULE = METADATA.resolve("Catalogs/Справочник1/Ext/ObjectModule.bsl");

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void prepareConfiguration() {
    initServerContext(METADATA, false);
    context.getConfiguration();
    var diagnostics = context.getLanguageServerConfiguration().getDiagnosticsOptions();
    diagnostics.setMode(Mode.ONLY);
    diagnostics.setParameters(Map.of("UnusedLocalVariable", Either.forLeft(true)));
  }

  @Test
  void excludesObjectMembersAndAmbiguousWritesWithoutPlatform() {
    var document = objectDocument("""
      Процедура ИзменитьОбъект()
        Перем Явная;
        реквизит1 = "новое";
        Наименование = "объект";
        ТабличнаяЧасть1.Очистить();
        Локальная = 1;
      КонецПроцедуры
      """);

    assertThat(typeService.isBareSelfProperty(document, "Реквизит1")).isTrue();
    assertThat(typeService.isBareSelfProperty(document, "Наименование")).isTrue();
    assertThat(typeService.isBareSelfProperty(document, "ТабличнаяЧасть1")).isTrue();
    assertThat(document.getSymbolTree().getVariables()).extracting(VariableSymbol::getName)
      .containsExactly("Явная", "Локальная");
    var diagnostics = document.getDiagnostics();
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true).hasRange(1, 8, 13);
  }

  @Test
  void resolvesExplicitLocalAndParameterBeforeObjectAttribute() {
    var document = objectDocument("""
      Процедура Явная()
        Перем Реквизит1;
        реквизит1 = "новое";
        Сообщить(ЭтотОбъект.Реквизит1);
      КонецПроцедуры

      Процедура СПараметром(Реквизит1)
        Реквизит1 = "новое";
      КонецПроцедуры
      """);

    assertThat(document.getSymbolTree().getVariables())
      .filteredOn(variable -> variable.getName().equals("Реквизит1"))
      .extracting(VariableSymbol::getKind)
      .containsExactlyInAnyOrder(VariableKind.LOCAL, VariableKind.PARAMETER);
    var diagnostics = document.getDiagnostics();
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true).hasRange(1, 8, 17);
  }

  @Test
  void acceptsReadsReturnsAndPropertyReceivers() {
    var document = objectDocument("""
      Функция ВозвратЗначения()
        Результат = Реквизит1;
        Возврат Результат;
      КонецФункции

      Процедура ЧитатьИСвойство()
        Коллекция = Новый Структура;
        Коллекция.Поле = 1;
        Значение = Реквизит2;
        Сообщить(значение);
      КонецПроцедуры
      """);

    assertThat(document.getSymbolTree().getVariables()).extracting(VariableSymbol::getName)
      .containsExactlyInAnyOrder("Результат", "Коллекция", "Значение");
    assertThat(document.getDiagnostics()).isEmpty();
  }

  private DocumentContext objectDocument(String text) {
    var document = TestUtils.getDocumentContext(OBJECT_MODULE.toUri(), text, context);
    assertThat(document.getModuleType()).isEqualTo(ModuleType.ObjectModule);
    return document;
  }
}
