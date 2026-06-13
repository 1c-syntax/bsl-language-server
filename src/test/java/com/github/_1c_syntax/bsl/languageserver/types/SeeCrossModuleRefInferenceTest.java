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
 * Cross-module ссылка {@code См. ОбщийМодуль.Метод} в описании параметра
 * должна резолвиться в тип возвращаемого значения целевого метода — через
 * {@link com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry}
 * +{@code getMembers}.
 */
@CleanupContextBeforeClassAndAfterClass
class SeeCrossModuleRefInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void parameterTypeFromCrossModuleConstructorReturn() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();

    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/SeeCrossModuleRef.bsl");

    var content = documentContext.getContent();
    var marker = "X = Список";
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + "X = ".length();
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;

    var types = typeService.expressionTypesAt(documentContext, new Position(line, charInLine));
    assertThat(types.refs())
      .as("параметр через `См. ОбщегоНазначения.ЗначениеВМассиве` получает return-type метода (Массив)")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Массив");
  }
}
