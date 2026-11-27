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
import com.github._1c_syntax.bsl.languageserver.context.computer.SymbolTreeComputer;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class UnusedLocalVariableFormDiagnosticTest extends AbstractServerContextAwareTest {

  private static final Path METADATA = Path.of("src/test/resources/metadata/designer");
  private static final Path FORM_MODULE =
    METADATA.resolve("Catalogs/Справочник1/Forms/ФормаЭлемента/Ext/Form/Module.bsl");

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
  void checksLocalsButNotFormAttributesInContextMethods() {
    var document = formDocument("""
      &НаКлиенте
      Перем НеиспользуемаяМодуля;

      &НаКлиенте
      Процедура ИзменитьФорму()
        другойобъект = Неопределено;
        Локальная = 1;
      КонецПроцедуры

      &НаСервере
      Процедура ЛокальнаяСРеквизитом()
        Перем ДругойОбъект;
        ДругойОбъект = Новый Структура;
      КонецПроцедуры

      &НаСервере
      Процедура ПараметрСРеквизитом(ДругойОбъект)
        ДругойОбъект = Неопределено;
      КонецПроцедуры

      Процедура ИзменитьФормуПоУмолчанию()
        ДругойОбъект = Неопределено;
      КонецПроцедуры
      """);

    assertThat(document.getDiagnostics(), true)
      .hasRange(11, 8, 20);
    assertThat(document.getDiagnostics()).hasSize(1);
    assertThat(document.getSymbolTree().getVariables())
      .filteredOn(variable -> variable.getName().equalsIgnoreCase("ДругойОбъект"))
      .extracting(VariableSymbol::getKind)
      .containsExactlyInAnyOrder(VariableKind.LOCAL, VariableKind.PARAMETER);
  }

  @ParameterizedTest
  @ValueSource(strings = {"НаСервереБезКонтекста", "AtClientAtServerNoContext"})
  void checksImplicitLocalsWithAttributeNamesInNoContextMethods(String directive) {
    var document = formDocument("""
      &%1$s
      Процедура ЛокальнаяСРеквизитом(Форма)
        ДругойОбъект = Форма.ДругойОбъект;
        Сообщить(Форма.ДругойОбъект);
      КонецПроцедуры

      &%1$s
      Функция ЧтениеИВозврат(Форма)
        ДругойОбъект = Форма.ДругойОбъект;
        Сообщить(другойобъект);
        Объект = Форма.Объект;
        Возврат Объект;
      КонецФункции

      &%1$s
      Процедура ИзменитьСвойство(Форма)
        Элементы = Форма.Элементы;
        Элементы.Поле.Видимость = Ложь;
      КонецПроцедуры
      """.formatted(directive));

    assertThat(document.getDiagnostics()).hasSize(1);
    assertThat(document.getDiagnostics(), true).hasRange(2, 2, 14);
    assertThat(document.getSymbolTree().getVariables())
      .filteredOn(variable -> variable.getName().equals("ДругойОбъект"))
      .extracting(VariableSymbol::getKind)
      .containsExactly(VariableKind.DYNAMIC, VariableKind.DYNAMIC);
  }

  @Test
  void ignoresEvalResultAssignedToLocalWithFormAttributeName() {
    var document = formDocument("""
      &НаСервереБезКонтекста
      Процедура Проверить(Выражение)
        ДругойОбъект = Вычислить(Выражение);
        Локальная = 1;
      КонецПроцедуры
      """);

    assertThat(document.getDiagnostics()).hasSize(1);
    assertThat(document.getDiagnostics(), true).hasRange(3, 2, 11);
    assertThat(document.getSymbolTree().getVariables())
      .filteredOn(variable -> variable.getName().equals("ДругойОбъект"))
      .extracting(VariableSymbol::getKind)
      .containsExactly(VariableKind.DYNAMIC);
  }

  @Test
  void keepsSymbolTreeWithNoContextLocalMatchingFormAttribute() {
    var document = formDocument("""
      &НаКлиенте
      Процедура Контекст()
        ДругойОбъект = Неопределено;
      КонецПроцедуры

      &НаКлиентеНаСервереБезКонтекста
      Процедура БезКонтекста()
        ДругойОбъект = Неопределено;
      КонецПроцедуры
      """);
    var computer = new SymbolTreeComputer(document, typeService, (doc, name) -> false);
    var tree = computer.compute();

    assertThat(tree.getBasis().bareSelfProperties()).containsExactly("ДругойОбъект");
    assertThat(tree.getVariables()).hasSize(1);
    assertThat(computer.isUpToDate(tree)).isTrue();
  }

  private DocumentContext formDocument(String text) {
    var document = TestUtils.getDocumentContext(FORM_MODULE.toUri(), text, context);
    assertThat(document.getModuleType()).isEqualTo(ModuleType.FormModule);
    assertThat(typeService.isBareSelfProperty(document, "ДругойОбъект")).isTrue();
    return document;
  }
}
