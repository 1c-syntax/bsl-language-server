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
package com.github._1c_syntax.bsl.languageserver.references;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Переменная, созданная присваиванием в теле модуля без {@code Перем}, живёт в теле модуля:
 * из методов её не видно — видны только переменные, объявленные {@code Перем}.
 */
@CleanupContextBeforeClassAndAfterClass
class ModuleBodyImplicitVariableScopeTest extends AbstractServerContextAwareTest {

  private static final String BSL_MODULE = "./src/test/resources/references/ModuleBodyImplicitVariable.bsl";
  private static final String OS_MODULE = "./src/test/resources/references/ModuleBodyImplicitVariable.os";

  @Autowired
  private ReferenceIndex referenceIndex;

  @Autowired
  private ReferenceResolver referenceResolver;

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUp() {
    initServerContext();
  }

  @Test
  void readFromMethodIsNotLinkedToVariableOfModuleBody() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(BSL_MODULE, context);
    var use = positionOf(documentContext, "ИзМетода = ВерсияФормы", "ВерсияФормы");

    // when
    var reference = referenceResolver.findReference(documentContext.getUri(), use);

    // then
    assertThat(reference.flatMap(Reference::getSourceDefinedSymbol))
      .isEmpty();
    assertThat(referenceIndex.getReferencesTo(moduleVariable(documentContext, "ВерсияФормы")))
      .extracting(found -> found.selectionRange().getStart().getLine())
      .doesNotContain(use.getLine());
  }

  @Test
  void readFromModuleBodyIsLinkedToItsVariable() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(BSL_MODULE, context);
    var use = positionOf(documentContext, "ИзТелаМодуля = ВерсияФормы", "ВерсияФормы");

    // when
    var reference = referenceResolver.findReference(documentContext.getUri(), use);

    // then
    assertThat(reference.flatMap(Reference::getSourceDefinedSymbol))
      .contains(moduleVariable(documentContext, "ВерсияФормы"));
  }

  @Test
  void readFromMethodIsLinkedToVariableDeclaredWithVar() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(BSL_MODULE, context);
    var use = positionOf(documentContext, "ИзМетодаПерем = ОбъявленнаяПерем", "ОбъявленнаяПерем");

    // when
    var reference = referenceResolver.findReference(documentContext.getUri(), use);

    // then
    assertThat(reference.flatMap(Reference::getSourceDefinedSymbol))
      .contains(moduleVariable(documentContext, "ОбъявленнаяПерем"));
  }

  @Test
  void typeOfReadFromMethodIsNotReportedAsUnplacedInFlow() {
    // given: раньше обращение из метода сопоставлялось с переменной тела модуля, а расчёт
    // по потоку в теле метода её не находил и писал ошибку.
    var documentContext = TestUtils.getDocumentContextFromFile(BSL_MODULE, context);
    var use = positionOf(documentContext, "ИзМетода = ВерсияФормы", "ВерсияФормы");
    var logger = (Logger) LoggerFactory.getLogger(ExpressionTypeInferencer.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    // when
    try {
      typeService.expressionTypesAt(documentContext, use);
    } finally {
      logger.detachAppender(appender);
    }

    // then
    assertThat(appender.list)
      .extracting(ILoggingEvent::getFormattedMessage)
      .noneMatch(message -> message.contains("не размещено в расчёте по потоку"));
  }

  @Test
  void assignmentInMethodAfterModuleBodyCreatesOwnVariable() {
    // given: в OneScript тело модуля стоит до методов, и одноимённая переменная тела к этому
    // моменту уже заведена.
    var documentContext = TestUtils.getDocumentContextFromFile(OS_MODULE, context);
    var use = positionOf(documentContext, "ИзМетода = Значение", "Значение");

    // when
    var reference = referenceResolver.findReference(documentContext.getUri(), use);

    // then: присваивание в методе заводит переменную метода.
    var variable = reference.flatMap(Reference::getSourceDefinedSymbol).orElseThrow();
    assertThat(variable).isInstanceOf(VariableSymbol.class);
    assertThat(((VariableSymbol) variable).getScope()).isInstanceOf(MethodSymbol.class);
  }

  private static VariableSymbol moduleVariable(DocumentContext documentContext, String name) {
    var symbolTree = documentContext.getSymbolTree();
    var variable = symbolTree.getVariableSymbol(name, symbolTree.getModule()).orElseThrow();
    assertThat(variable.getKind()).isIn(VariableKind.DYNAMIC, VariableKind.MODULE);
    return variable;
  }

  private static Position positionOf(DocumentContext documentContext, String line, String identifier) {
    var lines = documentContext.getContentList();
    for (var index = 0; index < lines.length; index++) {
      var start = lines[index].indexOf(line);
      if (start >= 0) {
        return new Position(index, start + line.indexOf(identifier) + 1);
      }
    }
    throw new AssertionError("строка '" + line + "' не найдена");
  }
}
