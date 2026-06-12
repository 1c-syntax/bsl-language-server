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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.EventHandlerResolver;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.assertj.core.api.Assertions;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

/**
 * Диагностика срабатывает по совпадению имени метода с событием owner-типа
 * модуля. Здесь подменяем {@link EventHandlerResolver}-бин на mock через
 * {@code @MockitoBean} и заглушаем ответы — это даёт unit-уровневую проверку
 * без необходимости поднимать полноценную тестовую конфигурацию с типами.
 */
class EventHandlerOutsideEventRegionDiagnosticTest
  extends AbstractDiagnosticTest<EventHandlerOutsideEventRegionDiagnostic> {

  private static final MemberDescriptor STUB_CONTRACT = MemberDescriptor.event(
    "<event>",
    "",
    List.of(new SignatureDescriptor(List.of(), TypeSet.EMPTY, ""))
  );

  @MockitoBean
  EventHandlerResolver eventHandlerResolver;

  EventHandlerOutsideEventRegionDiagnosticTest() {
    super(EventHandlerOutsideEventRegionDiagnostic.class);
  }

  @BeforeEach
  void resetResolver() {
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(Optional.empty());
  }

  @Test
  void firesOnHandlerOutsideEventRegion() {
    stubAsEventHandlers(Set.of("ПриЗаписи", "ПередЗаписью", "ПриУдалении"));

    var documentContext = getDocumentContext();
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    // ПриЗаписи — в СлужебныеПроцедурыИФункции (срабатывает),
    // ПередЗаписью — в ОбработчикиСобытий (не срабатывает),
    // ПриУдалении — без region (срабатывает).
    assertThat(diagnostics).hasSize(2);
  }

  @Test
  void silentOnMethodsWithoutEventContract() {
    // Ничего не заглушаем — resolver возвращает пустые контракты для всех.
    var documentContext = getDocumentContext();
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  private void stubAsEventHandlers(Set<String> methodNames) {
    methodNames.forEach(name -> Mockito.when(
        eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq(name)))
      .thenReturn(Optional.of(STUB_CONTRACT)));
  }

  @Test
  void quickFixMovesMethodIntoExistingRegion() {
    stubAsEventHandlers(Set.of("ПриЗаписи", "ПередЗаписью", "ПриУдалении"));
    var documentContext = getDocumentContext();
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    // Берём диагностику для ПриЗаписи — она в СлужебныеПроцедурыИФункции,
    // целевая область ОбработчикиСобытий в фикстуре уже есть.
    var diagnostic = diagnostics.stream()
      .filter(d -> d.getMessage().getLeft() != null && d.getMessage().getLeft().contains("ПриЗаписи"))
      .findFirst()
      .orElseThrow();

    List<CodeAction> quickFixes = getQuickFixes(diagnostic);

    Assertions.assertThat(quickFixes).hasSize(1);
    var edits = edits(quickFixes.get(0), documentContext);
    Assertions.assertThat(edits).hasSize(2);
  }

  @Test
  void quickFixCreatesRegionWhenAbsent() {
    // Метод не в нужной области и сама целевая область отсутствует.
    var src = """
      Процедура ПриЗаписи(Отказ) Экспорт
      КонецПроцедуры
      """;
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(STUB_CONTRACT));
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    Assertions.assertThat(diagnostics).hasSize(1);

    List<CodeAction> quickFixes = getQuickFixes(diagnostics.get(0), documentContext);

    Assertions.assertThat(quickFixes).hasSize(1);
    var edits = edits(quickFixes.get(0), documentContext);
    var inserted = edits.stream()
      .map(TextEdit::getNewText)
      .filter(text -> text.contains("#Область"))
      .findFirst()
      .orElseThrow();
    Assertions.assertThat(inserted)
      .contains("#Область ОбработчикиСобытий")
      .contains("ПриЗаписи")
      .contains("#КонецОбласти");
  }

  @Test
  void quickFixFixAllGroupsMethodsIntoSingleRegion() {
    var src = """
      Процедура ПриЗаписи(Отказ) Экспорт
      КонецПроцедуры

      Процедура ПриУдалении(Отказ) Экспорт
      КонецПроцедуры
      """;
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(STUB_CONTRACT));
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриУдалении")))
      .thenReturn(Optional.of(STUB_CONTRACT));
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    Assertions.assertThat(diagnostics).hasSize(2);

    var fixes = diagnosticInstance.getQuickFixes(diagnostics, fakeParams(documentContext), documentContext);

    Assertions.assertThat(fixes).hasSize(1);
    var edits = edits(fixes.get(0), documentContext);
    var inserts = edits.stream().map(TextEdit::getNewText).filter(t -> t.contains("#Область")).toList();
    // Одна вставка с одной #Область, в которой оба метода.
    Assertions.assertThat(inserts).hasSize(1);
    Assertions.assertThat(inserts.get(0))
      .contains("ПриЗаписи")
      .contains("ПриУдалении");
  }

  private static List<TextEdit> edits(CodeAction action,
                                      DocumentContext documentContext) {
    return action.getEdit().getChanges().get(documentContext.getUri().toString());
  }

  private static CodeActionParams fakeParams(
    DocumentContext documentContext
  ) {
    var params = new CodeActionParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setRange(new Range(
      new Position(0, 0), new Position(0, 0)));
    return params;
  }

  @Test
  void quickFixDoesNothingForDiagnosticsOutsideAnyMethod() {
    var src = """
      Процедура ПриЗаписи() Экспорт
      КонецПроцедуры
      """;
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(STUB_CONTRACT));
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    var diagnostic = diagnostics.get(0);
    // искусственно сместим range диагностики за пределы метода
    diagnostic.setRange(new Range(
      new Position(100, 0), new Position(100, 0)));
    Assertions.assertThat(getQuickFixes(diagnostic, documentContext)).isEmpty();
  }

  @Test
  void quickFixCreatesRegionAfterExistingRegionWhenTargetMissing() {
    // Уже есть какая-то область, но не «ОбработчикиСобытий» — новая создаётся
    // после неё (покрывает ветку newRegionInsertPosition с непустым списком).
    var src = """
      #Область Прочее
      #КонецОбласти

      Процедура ПриЗаписи(Отказ) Экспорт
      КонецПроцедуры
      """;
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(STUB_CONTRACT));
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    Assertions.assertThat(diagnostics).hasSize(1);

    var fixes = getQuickFixes(diagnostics.get(0), documentContext);
    var inserted = fixes.get(0).getEdit().getChanges()
      .get(documentContext.getUri().toString()).stream()
      .map(TextEdit::getNewText)
      .filter(t -> t.contains("#Область ОбработчикиСобытий"))
      .findFirst()
      .orElseThrow();
    Assertions.assertThat(inserted).contains("ПриЗаписи");
  }

  @Test
  void quickFixSkipsLeadingBlankWhenPreviousLineEmpty() {
    // Целевая область уже есть и перед #КонецОбласти — пустая строка:
    // leading "\n" должен НЕ добавляться (покрывает needsLeadingBlank=false).
    var src = """
      #Область ОбработчикиСобытий

      #КонецОбласти

      Процедура ПриЗаписи(Отказ) Экспорт
      КонецПроцедуры
      """;
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(STUB_CONTRACT));
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    var fixes = getQuickFixes(diagnostics.get(0), documentContext);

    // Текстовый блок вставки начинается с самого метода (без leading \n).
    var insertText = fixes.get(0).getEdit().getChanges()
      .get(documentContext.getUri().toString()).stream()
      .filter(e -> e.getNewText().contains("ПриЗаписи"))
      .findFirst()
      .orElseThrow()
      .getNewText();
    Assertions.assertThat(insertText).startsWith("Процедура");
  }

  @Test
  void quickFixCaptureLeadingDocComment() {
    // У метода прижатая шапка doc-комментарий — должна перенестись вместе с методом.
    var src = """
      // Шапка комментария.
      Процедура ПриЗаписи(Отказ) Экспорт
      КонецПроцедуры
      """;
    Mockito.when(eventHandlerResolver.lookupContract(ArgumentMatchers.any(), ArgumentMatchers.eq("ПриЗаписи")))
      .thenReturn(Optional.of(STUB_CONTRACT));
    var documentContext = TestUtils.getDocumentContext(src);
    var diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    var fixes = getQuickFixes(diagnostics.get(0), documentContext);
    var inserted = fixes.get(0).getEdit().getChanges()
      .get(documentContext.getUri().toString()).stream()
      .map(TextEdit::getNewText)
      .filter(t -> t.contains("#Область"))
      .findFirst()
      .orElseThrow();
    Assertions.assertThat(inserted).contains("Шапка комментария.");
  }
}
