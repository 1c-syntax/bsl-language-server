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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Платформенные свойства объекта отчёта и обработки, когда синтакс-помощника нет и типы
 * берутся из встроенного описания.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=false")
class ReportObjectFallbackPropertiesTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_CONFIGURATION = "src/test/resources/metadata/reportObjectFallback";
  private static final String REPORT_OBJECT_MODULE = "Reports/Отчет1/Ext/ObjectModule.bsl";
  private static final String DATA_PROCESSOR_OBJECT_MODULE = "DataProcessors/Обработка1/Ext/ObjectModule.bsl";

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUp() {
    initServerContext(PATH_TO_CONFIGURATION);
  }

  @Test
  void assignmentToSettingsComposerInModuleBodyCreatesNoVariable() {
    // given: в теле модуля отчёта присваивают его платформенному свойству.
    var objectModule = document(REPORT_OBJECT_MODULE);

    // when
    var variables = objectModule.getSymbolTree().getVariables().stream()
      .filter(variable -> variable.getName().equalsIgnoreCase("КомпоновщикНастроек"))
      .toList();

    // then: это свойство отчёта, а не своя переменная модуля.
    assertThat(variables).isEmpty();
  }

  @Test
  void reportObjectPropertiesAreTyped() {
    // given
    var objectModule = document(REPORT_OBJECT_MODULE);

    // when
    var composer = typeService.expressionTypesAt(objectModule,
      positionOf(objectModule, "ИзКомпоновщика = КомпоновщикНастроек"));
    var schema = typeService.expressionTypesAt(objectModule,
      positionOf(objectModule, "ИзСхемы = СхемаКомпоновкиДанных"));

    // then
    assertThat(composer.refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("КомпоновщикНастроекКомпоновкиДанных");
    assertThat(schema.refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("СхемаКомпоновкиДанных");
  }

  @Test
  void thisObjectOfDataProcessorIsTheDataProcessor() {
    // given
    var objectModule = document(DATA_PROCESSOR_OBJECT_MODULE);

    // when
    var types = typeService.expressionTypesAt(objectModule, positionOf(objectModule, "Сам = ЭтотОбъект"));

    // then
    assertThat(types.refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("ОбработкаОбъект.Обработка1");
  }

  private DocumentContext document(String relativePath) {
    var uri = Absolute.uri(Path.of(PATH_TO_CONFIGURATION, relativePath).toUri());
    var documentContext = context.getDocument(uri);
    assertThat(documentContext).as(relativePath).isNotNull();
    context.rebuildDocument(documentContext);
    return documentContext;
  }

  private static Position positionOf(DocumentContext documentContext, String marker) {
    var lines = documentContext.getContentList();
    for (var line = 0; line < lines.length; line++) {
      var column = lines[line].indexOf(marker);
      if (column >= 0) {
        return new Position(line, column + marker.length() - 1);
      }
    }
    throw new AssertionError("маркер '" + marker + "' не найден");
  }
}
