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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: вызов common-module-метода с описанием возвращаемого значения
 * должен передать тип переменной слева от присваивания (bug report:
 * {@code Владельцы = ОбщегоНазначения.ЗначениеВМассиве(Владелец)} → Массив).
 */
@CleanupContextBeforeClassAndAfterClass
class CommonModuleCallInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void assignmentFromCommonModuleMethodGetsReturnType() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();

    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/CommonModuleCall.bsl");

    var content = documentContext.getContent();
    var line = content.substring(0, content.indexOf("ЗначениеВМассиве")).split("\n").length - 1;
    int lineStart = content.lastIndexOf('\n', content.indexOf("ЗначениеВМассиве")) + 1;
    int pos = content.indexOf("ЗначениеВМассиве") - lineStart + 1;

    var types = typeService.expressionTypesAt(documentContext, new Position(line, pos));
    assertThat(types.refs()).as("method call return type").hasSize(1);
    assertThat(types.refs().iterator().next().qualifiedName()).isEqualTo("Массив");
  }

  @Test
  void receiverTypesAtCommonModuleMemberYieldsModuleType() {
    // #3991: тип ресивера-общего-модуля (ОбщегоНазначения.ОбщийМодуль()) должен
    // выводиться как конфигурационный тип модуля.
    // given
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/CommonModuleMidCallCompletion.bsl");

    var content = documentContext.getContent();
    int idx = content.indexOf("ОбщийМодуль");
    int line = content.substring(0, idx).split("\n").length - 1;
    int lineStart = content.lastIndexOf('\n', idx) + 1;
    int col = idx - lineStart + 1;

    // when — позиция на члене ОбщийМодуль, ресивер слева — ОбщегоНазначения
    var types = typeService.receiverTypesAt(documentContext, new Position(line, col));

    // then
    assertThat(types.refs())
      .as("ресивер-общий-модуль должен резолвиться в тип модуля, а не пусто")
      .hasSize(1);
    assertThat(types.refs().iterator().next().qualifiedName()).isEqualTo("ОбщегоНазначения");
  }
}
