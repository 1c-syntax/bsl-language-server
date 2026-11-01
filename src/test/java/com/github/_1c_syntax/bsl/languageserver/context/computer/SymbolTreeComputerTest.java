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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.EventHandlerClassifier;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SelfMemberClassifier;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class SymbolTreeComputerTest {

  @Autowired
  private com.github._1c_syntax.bsl.languageserver.context.symbol.EventHandlerClassifier eventHandlerClassifier;

  @Test
  void loopDeclarationsDoNotDependOnSelfPropertyClassification() {
    var document = TestUtils.getDocumentContext("""
      Процедура Циклы()
        Для Свойство = 1 По 2 Цикл
        КонецЦикла;
        Для Каждого ДругоеСвойство Из Массив Цикл
        КонецЦикла;
      КонецПроцедуры
      """);
    var selfProperty = new AtomicBoolean(true);
    var computer = new SymbolTreeComputer(document, (doc, name) -> selfProperty.get(),
      (doc, name) -> false);

    var tree = computer.compute();
    assertThat(tree.getVariables()).extracting(VariableSymbol::getName)
      .containsExactlyInAnyOrder("Свойство", "ДругоеСвойство");
    assertThat(computer.isUpToDate(tree)).isTrue();

    selfProperty.set(false);
    assertThat(computer.isUpToDate(tree)).isTrue();
    selfProperty.set(true);
    assertThat(computer.isUpToDate(tree)).isTrue();
  }

  @Test
  void loopNameInAnotherMethodDoesNotHideAssignmentClassificationChanges() {
    var document = TestUtils.getDocumentContext("""
      Процедура Цикл()
        Для Свойство = 1 По 2 Цикл
        КонецЦикла;
      КонецПроцедуры
      Процедура Присваивание()
        Свойство = 0;
      КонецПроцедуры
      """);
    var selfProperty = new AtomicBoolean(false);
    var computer = new SymbolTreeComputer(document, (doc, name) -> selfProperty.get(),
      (doc, name) -> false);
    var tree = computer.compute();
    assertThat(tree.getVariables()).hasSize(2);

    selfProperty.set(true);
    assertThat(computer.isUpToDate(tree)).isFalse();
    var refreshed = computer.compute();
    assertThat(refreshed.getVariables()).hasSize(1);
    assertThat(computer.isUpToDate(refreshed)).isTrue();
  }

  @Test
  void assignmentBeforeLoopStillDependsOnSelfPropertyClassification() {
    var document = TestUtils.getDocumentContext("""
      Процедура Цикл()
        Свойство = 0;
        Для Свойство = 1 По 2 Цикл
        КонецЦикла;
      КонецПроцедуры
      """);
    var selfProperty = new AtomicBoolean(false);
    var computer = new SymbolTreeComputer(document, (doc, name) -> selfProperty.get(),
      (doc, name) -> false);
    var assignmentTree = computer.compute();
    assertThat(assignmentTree.getVariables().getFirst().getVariableNameRange().getStart().getLine()).isEqualTo(1);

    selfProperty.set(true);
    assertThat(computer.isUpToDate(assignmentTree)).isFalse();
    var loopTree = computer.compute();
    assertThat(loopTree.getVariables().getFirst().getVariableNameRange().getStart().getLine()).isEqualTo(2);
    assertThat(computer.isUpToDate(loopTree)).isTrue();

    selfProperty.set(false);
    assertThat(computer.isUpToDate(loopTree)).isFalse();
  }

  @Test
  void buildingAndRevalidatingUseDocumentClassifiers() {
    var document = TestUtils.getDocumentContext("""
      Процедура Первая()
        Свойство = 1;
        Переменная = 2;
      КонецПроцедуры
      Процедура Вторая()
        ДругаяПеременная = 3;
      КонецПроцедуры
      """);
    var properties = mock(SelfMemberClassifier.class);
    var handlers = mock(EventHandlerClassifier.class);
    when(properties.bareSelfPropertiesOf(document)).thenReturn("Свойство"::equalsIgnoreCase);
    when(handlers.eventHandlersOf(document)).thenReturn("Первая"::equalsIgnoreCase);
    var computer = new SymbolTreeComputer(document, properties, handlers);

    var tree = computer.compute();
    assertThat(tree.getBasis().bareSelfProperties()).containsExactly("Свойство");
    assertThat(computer.isUpToDate(tree)).isTrue();
    verify(properties, times(2)).bareSelfPropertiesOf(document);
    verify(handlers, times(2)).eventHandlersOf(document);
    verify(properties, never()).isBareSelfProperty(any(), anyString());
    verify(handlers, never()).isEventHandler(any(), anyString());

    when(properties.bareSelfPropertiesOf(document)).thenReturn(name -> false);
    assertThat(computer.isUpToDate(tree)).isFalse();
  }

  @Test
  void testModule() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/symbol/SymbolTreeComputer.bsl");

    var symbolTreeComputer = new SymbolTreeComputer(documentContext, (doc, name) -> false, eventHandlerClassifier);

    // when
    var symbolTree = symbolTreeComputer.compute();

    // then
    var module = symbolTree.getModule();
    assertThat(module.getOwner()).isEqualTo(documentContext);
    assertThat(module.getParent()).isEmpty();
    assertThat(module.getChildren()).hasSize(2);

    for (SourceDefinedSymbol child : module.getChildren()) {
      assertThat(child.getParent()).hasValue(module);
    }
  }

}
