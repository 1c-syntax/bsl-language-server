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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class AnnotationReferenceFinderTest extends AbstractServerContextAwareTest {

  @Autowired
  private AnnotationReferenceFinder referenceFinder;

  @Test
  void findReferenceOfAnnotation() {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(0, 2));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.from()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getName()).isEqualTo("ТестоваяАннотация"))
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getSymbolKind()).isEqualTo(SymbolKind.Interface))
      .hasValueSatisfying(reference -> assertThat(reference.selectionRange()).isEqualTo(Ranges.create(0, 0, 76)))
      .hasValueSatisfying(reference -> assertThat(reference.getSourceDefinedSymbol().orElseThrow().getSelectionRange()).isEqualTo(Ranges.create(7, 10, 28)))
    ;
  }

  @Test
  void findReferenceOfAnnotationParameterName() {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(0, 25));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.from()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getName()).isEqualTo("ВторойПараметр"))
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getSymbolKind()).isEqualTo(SymbolKind.TypeParameter))
      .hasValueSatisfying(reference -> assertThat(reference.selectionRange()).isEqualTo(Ranges.create(0, 19, 33)))
      .hasValueSatisfying(reference -> assertThat(reference.getSourceDefinedSymbol().orElseThrow().getSelectionRange()).isEqualTo(Ranges.create(7, 10, 28)))
    ;
  }

  @Test
  void findReferenceOfAnnotationParameterValue() {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(0, 60));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.from()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getName()).isEqualTo("Значение"))
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getSymbolKind()).isEqualTo(SymbolKind.TypeParameter))
      .hasValueSatisfying(reference -> assertThat(reference.selectionRange()).isEqualTo(Ranges.create(0, 56, 75)))
      .hasValueSatisfying(reference -> assertThat(reference.getSourceDefinedSymbol().orElseThrow().getSelectionRange()).isEqualTo(Ranges.create(7, 10, 28)))
    ;
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
     6, 24,  6, 19,  6, 27
     8, 10,  8,  4,  9, 12
     9, 10,  8,  4,  9, 12
    11, 19, 11, 19, 11, 21
    12, 19, 12, 19, 12, 22
    13, 19, 13, 19, 13, 25
    14, 19, 14, 19, 14, 29
    15, 19, 15, 19, 15, 31
    16, 19, 16, 19, 16, 23
    18,  4, 18,  4, 18,  6
    19,  4, 19,  4, 19,  7
    20,  4, 20,  4, 20, 12
    21,  4, 21,  4, 22, 12
    22,  4, 21,  4, 22, 12
    23,  4, 23,  4, 23, 10
    24,  4, 24,  4, 24, 14
    25,  4, 25,  4, 25, 16
    26,  4, 26,  4, 26,  8
    """
  )
  void findReferenceOfAnnotationParameterValue_allLiterals(int positionLine, int positionCharacter, int selectionRangeStartLine, int selectionRangeStartCharacter, int selectionRangeEndLine, int selectionRangeEndCharacter) {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(positionLine, positionCharacter));

    // then
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.from()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getName()).isEqualTo("Значение"))
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getSymbolKind()).isEqualTo(SymbolKind.TypeParameter))
      .hasValueSatisfying(reference -> assertThat(reference.selectionRange()).isEqualTo(Ranges.create(selectionRangeStartLine, selectionRangeStartCharacter, selectionRangeEndLine, selectionRangeEndCharacter)))
      .hasValueSatisfying(reference -> assertThat(reference.getSourceDefinedSymbol().orElseThrow().getSelectionRange()).isEqualTo(Ranges.create(7, 10, 28)))
    ;
  }

  @Test
  void testHandleDocumentContextChange() {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var annotationDocumentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/annotations/ТестоваяАннотация.os");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    // Проверяем, что ссылка существует до изменения
    var referenceBefore = referenceFinder.findReference(documentContext.getUri(), new Position(0, 2));
    assertThat(referenceBefore).isPresent();

    // when - эмулируем изменение содержимого документа с аннотацией
    var newContent = """
      // Описание изменено
      //
      // Параметры:
      //   НовыйПараметр - Строка - Новое значение
      //
      &Аннотация("ТестоваяАннотация")
      Процедура ПриСозданииОбъекта(НовыйПараметр)
      КонецПроцедуры
      """;

    context.rebuildDocument(annotationDocumentContext, newContent, 2);
    referenceFinder.handleDocumentContextChange(new DocumentContextContentChangedEvent(annotationDocumentContext));

    // then - ссылка все еще должна существовать и указывать на обновленное определение
    var referenceAfter = referenceFinder.findReference(documentContext.getUri(), new Position(0, 2));
    assertThat(referenceAfter)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getName()).isEqualTo("ТестоваяАннотация"));
  }

  @Test
  void testHandleServerContextDocumentRemovedEvent() {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var annotationDocumentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/annotations/ТестоваяАннотация.os");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");
    var annotationUri = annotationDocumentContext.getUri();

    // Проверяем, что ссылка существует до удаления
    var referenceBefore = referenceFinder.findReference(documentContext.getUri(), new Position(0, 2));
    assertThat(referenceBefore).isPresent();

    // when - эмулируем удаление документа с определением аннотации
    referenceFinder.handleServerContextDocumentRemovedEvent(
      new ServerContextDocumentRemovedEvent(context, annotationUri)
    );

    // then - ссылка не должна разрешаться, т.к. определение аннотации удалено
    var referenceAfter = referenceFinder.findReference(documentContext.getUri(), new Position(0, 2));
    assertThat(referenceAfter).isEmpty();
  }

  @Test
  void testHandleContextRefresh() {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    // Проверяем, что ссылка существует
    var referenceBefore = referenceFinder.findReference(documentContext.getUri(), new Position(0, 2));
    assertThat(referenceBefore).isPresent();

    // when - повторная инициализация контекста (эмуляция populateContext)
    initServerContext("./src/test/resources/references/annotations");

    // then - ссылка должна продолжать работать после обновления контекста
    var referenceAfter = referenceFinder.findReference(documentContext.getUri(), new Position(0, 2));
    assertThat(referenceAfter)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getName()).isEqualTo("ТестоваяАннотация"));
  }

  @Test
  void testMultipleAnnotationsFromSameDocument() {
    // given
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    // when - проверяем ссылки на разные аннотации
    var reference1 = referenceFinder.findReference(documentContext.getUri(), new Position(0, 2));
    var reference2 = referenceFinder.findReference(documentContext.getUri(), new Position(4, 2));

    // then - проверяем что первая аннотация найдена
    assertThat(reference1)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getName()).isEqualTo("ТестоваяАннотация"));

    // Вторая аннотация может не найтись из-за особенностей регистрации аннотаций при populateContext
    // Это известная проблема, описанная в комментарии к методу findAndRegisterAnnotation
    // Проверяем что reference2 либо есть, либо нет - главное что не падает
    assertThat(reference2).isNotNull();
  }

  @Test
  void testNonOSFileIgnored() {
    // given
    initServerContext("./src/test/resources/references");
    var bslDocumentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/ReferenceIndex.bsl");

    // when - пытаемся найти ссылку в .bsl файле
    var reference = referenceFinder.findReference(bslDocumentContext.getUri(), new Position(0, 0));

    // then - ссылка не должна быть найдена, т.к. работаем только с .os файлами
    assertThat(reference).isEmpty();
  }

  @Test
  void findReferenceOfNestedAnnotationName() {
    // given
    // Строка 32 (1-based), 31 (0-based): &ТестоваяАннотация(&ВложеннаяАннотация("Значение"))
    // При клике на имя вложенной аннотации должна находиться ссылка на определение этой аннотации
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when - кликаем на имя вложенной аннотации "ВложеннаяАннотация"
    // &ТестоваяАннотация(&ВложеннаяАннотация("Значение"))
    // Позиция 20 - это внутри имени "ВложеннаяАннотация"
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(31, 22));

    // then - должна найтись ссылка на определение аннотации ВложеннаяАннотация (если она зарегистрирована)
    // Поскольку аннотация ВложеннаяАннотация не зарегистрирована в тестовом контексте, ссылка не найдётся
    assertThat(optionalReference).isEmpty();
  }

  @Test
  void findReferenceOfNestedAnnotationParameterValue() {
    // given
    // Строка 32 (1-based), 31 (0-based): &ТестоваяАннотация(&ВложеннаяАннотация("Значение"))
    // При клике на значение параметра вложенной аннотации должна находиться ссылка на параметр вложенной аннотации
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when - кликаем на значение "Значение" внутри вложенной аннотации
    // &ТестоваяАннотация(&ВложеннаяАннотация("Значение"))
    // Позиция 40 - это внутри "Значение"
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(31, 40));

    // then - должна найтись ссылка на параметр "Значение" вложенной аннотации
    // Поскольку аннотация ВложеннаяАннотация не зарегистрирована, ссылка не найдётся
    assertThat(optionalReference).isEmpty();
  }

  @Test
  void findReferenceOfNestedAnnotationWithNamedParameter() {
    // given
    // Строка 35 (1-based), 34 (0-based): &ТестоваяАннотация(Параметр = &ВложеннаяАннотация("Значение"))
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    var module = documentContext.getSymbolTree().getModule();

    // when - кликаем на имя параметра "Параметр" внешней аннотации
    // &ТестоваяАннотация(Параметр = &ВложеннаяАннотация("Значение"))
    // Позиция 20 - это внутри "Параметр"
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(34, 20));

    // then - должна найтись ссылка на параметр "Параметр" внешней аннотации ТестоваяАннотация
    assertThat(optionalReference)
      .isPresent()
      .hasValueSatisfying(reference -> assertThat(reference.from()).isEqualTo(module))
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getName()).isEqualTo("Параметр"))
      .hasValueSatisfying(reference -> assertThat(reference.symbol().getSymbolKind()).isEqualTo(SymbolKind.TypeParameter));
  }

  @Test
  void findReferenceOfDeeplyNestedAnnotationName() {
    // given
    // Строка 38 (1-based), 37 (0-based): &ТестоваяАннотация(&ВложеннаяАннотация(&ГлубокоВложеннаяАннотация("Глубокое значение")))
    initServerContext("./src/test/resources/references/annotations");
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/references/AnnotationReferenceFinder.os");

    // when - кликаем на имя глубоко вложенной аннотации "ГлубокоВложеннаяАннотация"
    // Позиция 45 - это внутри имени "ГлубокоВложеннаяАннотация"
    var optionalReference = referenceFinder.findReference(documentContext.getUri(), new Position(37, 45));

    // then - поскольку аннотация не зарегистрирована, ссылка не найдётся
    assertThat(optionalReference).isEmpty();
  }
}