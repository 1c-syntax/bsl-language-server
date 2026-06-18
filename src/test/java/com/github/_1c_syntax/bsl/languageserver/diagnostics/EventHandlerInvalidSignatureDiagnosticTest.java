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

import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.EventHandlerResolver;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.assertj.core.api.Assertions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class EventHandlerInvalidSignatureDiagnosticTest
  extends AbstractDiagnosticTest<EventHandlerInvalidSignatureDiagnostic> {

  @MockitoBean
  EventHandlerResolver eventHandlerResolver;

  EventHandlerInvalidSignatureDiagnosticTest() {
    super(EventHandlerInvalidSignatureDiagnostic.class);
  }

  @BeforeEach
  void resetResolver() {
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(Optional.empty());
  }

  @Test
  void firesOnMismatch() {
    // Контракт требует 1 обязательный параметр Отказ.
    stubAsHandler("ПриЗаписи", contractWithCancel(true));
    stubAsHandler("ПередЗаписью", contractWithCancel(true));
    stubAsHandler("ПриУдалении", contractWithCancel(true));

    var documentContext = getDocumentContext();
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    // ПриЗаписи(Отказ) — fits, ПередЗаписью() — нет (0 vs 1 required),
    // ПриУдалении(Отказ, лишнее) — нет (2 > 1 max).
    assertThat(diagnostics).hasSize(2);
  }

  @Test
  void silentForVariadicContract() {
    // Конструктор OneScript-класса ПриСозданииОбъекта принимает переменное
    // число параметров — любое количество объявленных аргументов допустимо.
    var variadicParam = new ParameterDescriptor(
      BilingualString.of("Значение", "Value"),
      TypeSet.EMPTY, true, BilingualString.EMPTY, "", true);
    var contract = MemberDescriptor.event("ПриЗаписи", "",
      List.of(new SignatureDescriptor(List.of(variadicParam), TypeSet.EMPTY, "")));
    stubAsHandler("ПриЗаписи", contract);

    var src = """
      Процедура ПриЗаписи(КоордХ, КоордУ)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void silentWhenContractEmpty() {
    // Контракт без сигнатур — диагностика молчит (нечем сравнивать).
    stubAsHandler("ПередЗаписью", MemberDescriptor.event("ПередЗаписью", "", List.of()));

    var documentContext = getDocumentContext();
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  private void stubAsHandler(String methodName, MemberDescriptor contract) {
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq(methodName)))
      .thenReturn(Optional.of(contract));
  }

  private static MemberDescriptor contractWithCancel(boolean cancelRequired) {
    var param = new ParameterDescriptor("Отказ", TypeSet.of(TypeRef.UNKNOWN),
      !cancelRequired, "", "");
    var signature = new SignatureDescriptor(List.of(param), TypeSet.EMPTY, "");
    return MemberDescriptor.event("<event>", "", List.of(signature));
  }

  @Test
  void quickFixAddsMissingParametersFromContract() {
    var contract = MemberDescriptor.event("ПриЗаписи", "",
      List.of(new SignatureDescriptor(List.of(
        new ParameterDescriptor(BilingualString.of("Отказ", "Cancel"),
          TypeSet.EMPTY, false, BilingualString.EMPTY, ""),
        new ParameterDescriptor(BilingualString.of("ПараметрыЗаписи", "WriteParameters"),
          TypeSet.EMPTY, false, BilingualString.EMPTY, "")
      ), TypeSet.EMPTY, "")));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи()
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    Assertions.assertThat(diagnostics).hasSize(1);

    var fixes = getQuickFixes(diagnostics.get(0), documentContext);
    Assertions.assertThat(fixes).hasSize(1);

    var edit = fixes.get(0).getEdit().getChanges()
      .get(documentContext.getUri().toString()).get(0);
    Assertions.assertThat(edit.getNewText()).isEqualTo("Отказ, ПараметрыЗаписи");
  }

  @Test
  void quickFixDropsExtraParametersAndKeepsExistingNames() {
    // У метода 2 параметра (СвойОтказ, Лишний), у контракта 1 (Отказ).
    // Имя «СвойОтказ» сохраняется (позиция 0 совпадает), второй параметр срезается.
    var contract = MemberDescriptor.event("ПередЗаписью", "",
      List.of(new SignatureDescriptor(List.of(
        new ParameterDescriptor(BilingualString.of("Отказ", "Cancel"),
          TypeSet.EMPTY, false, BilingualString.EMPTY, "")
      ), TypeSet.EMPTY, "")));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПередЗаписью")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПередЗаписью(СвойОтказ, Лишний)
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    Assertions.assertThat(diagnostics).hasSize(1);

    var fixes = getQuickFixes(diagnostics.get(0), documentContext);
    var edit = fixes.get(0).getEdit().getChanges()
      .get(documentContext.getUri().toString()).get(0);
    Assertions.assertThat(edit.getNewText()).isEqualTo("СвойОтказ");
  }

  @Test
  void quickFixSilentWhenContractEmpty() {
    // Контракт без сигнатур — getQuickFixes должен вернуть пустой список.
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(MemberDescriptor.event("ПриЗаписи", "", List.of())));

    var src = "Процедура ПриЗаписи()\nКонецПроцедуры\n";
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    Assertions.assertThat(diagnostics).isEmpty();
  }

  @Test
  void quickFixUsesEnNameWhenRuMissing() {
    var contract = MemberDescriptor.event("Handler", "",
      List.of(new SignatureDescriptor(List.of(
        new ParameterDescriptor(BilingualString.of("", "Cancel"),
          TypeSet.EMPTY, false, BilingualString.EMPTY, "")
      ), TypeSet.EMPTY, "")));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("Handler")))
      .thenReturn(Optional.of(contract));

    var src = "Процедура Handler()\nКонецПроцедуры\n";
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    Assertions.assertThat(diagnostics).hasSize(1);

    var fixes = getQuickFixes(diagnostics.get(0), documentContext);
    var edit = fixes.get(0).getEdit().getChanges()
      .get(documentContext.getUri().toString()).get(0);
    Assertions.assertThat(edit.getNewText()).isEqualTo("Cancel");
  }

  @Test
  void quickFixUsesFallbackNameWhenBothNamesBlank() {
    var contract = MemberDescriptor.event("Handler", "",
      List.of(new SignatureDescriptor(List.of(
        new ParameterDescriptor(BilingualString.EMPTY, TypeSet.EMPTY, false, BilingualString.EMPTY, "")
      ), TypeSet.EMPTY, "")));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("Handler")))
      .thenReturn(Optional.of(contract));

    var src = "Процедура Handler()\nКонецПроцедуры\n";
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    var fixes = getQuickFixes(diagnostics.get(0), documentContext);
    var newText = fixes.get(0).getEdit().getChanges()
      .get(documentContext.getUri().toString()).get(0).getNewText();
    Assertions.assertThat(newText).isEqualTo("Параметр1");
  }

  @Test
  void quickFixHandlesDefaultValueWithNestedParens() {
    // Параметр со значением по умолчанию, содержащим скобки: matchingRparenIndex
    // должен учесть баланс LPAREN/RPAREN, а не остановиться на первой ).
    var contract = MemberDescriptor.event("ПриЗаписи", "",
      List.of(new SignatureDescriptor(List.of(
        new ParameterDescriptor(BilingualString.of("Отказ", "Cancel"),
          TypeSet.EMPTY, false, BilingualString.EMPTY, "")
      ), TypeSet.EMPTY, "")));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));

    var src = """
      Процедура ПриЗаписи(Отказ, Лишний = Новый Структура("К","З"))
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    Assertions.assertThat(diagnostics).hasSize(1);

    var fixes = getQuickFixes(diagnostics.get(0), documentContext);
    Assertions.assertThat(fixes).hasSize(1);
    var edit = fixes.get(0).getEdit().getChanges()
      .get(documentContext.getUri().toString()).get(0);
    Assertions.assertThat(edit.getNewText()).isEqualTo("Отказ");
  }

  @Test
  void quickFixDoesNothingForDiagnosticsOutsideMethod() {
    // Диагностика с произвольным range вне метода — quickfix не должен ничего
    // создавать. Используем существующую диагностику и подменяем range.
    var contract = contractWithCancel(true);
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(contract));
    var src = "Процедура ПриЗаписи()\nКонецПроцедуры\n";
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    Assertions.assertThat(diagnostics).hasSize(1);

    // подменим range на бессмысленный
    var diagnostic = diagnostics.get(0);
    diagnostic.setRange(new Range(new Position(100, 0), new Position(100, 0)));
    var fixes = getQuickFixes(diagnostic, documentContext);
    Assertions.assertThat(fixes).isEmpty();
  }
}
