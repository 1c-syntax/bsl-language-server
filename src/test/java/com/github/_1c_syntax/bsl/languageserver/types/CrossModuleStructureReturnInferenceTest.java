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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Межмодульный вызов common-module-функции, возвращающей описанную в JsDoc
 * {@code Структура} с полями (ОбщегоНазначения.НовыеСвойстваПодписи()): тип
 * переменной слева должен нести {@code localFields}, чтобы автокомплит/hover
 * по точке видели поля. Регресс: member-метод модуля отдавал лишь головной
 * тип без полей — поля резолвились только для вызова внутри того же модуля.
 */
@CleanupContextBeforeClassAndAfterClass
class CrossModuleStructureReturnInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void crossModuleStructureReturnCarriesFields() {
    // given
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/CrossModuleStructureReturn.bsl");

    // when: тип переменной, присвоенной из межмодульного вызова.
    var types = at(documentContext, "Свойства = ОбщегоНазначения", 1);

    // then: Структура с полями из JsDoc (включая поле с многострочным типом и вложенное).
    assertThat(types.refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Структура");
    var structureRef = types.refs().iterator().next();
    assertThat(types.getLocalFields(structureRef).keySet())
      .contains("Подпись", "Комментарий", "ДатаПодписи", "ОписаниеСертификата");
  }

  private com.github._1c_syntax.bsl.languageserver.types.model.TypeSet at(
    DocumentContext dc, String marker, int offsetInMarker) {
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(dc, new Position(line, charInLine));
  }
}
