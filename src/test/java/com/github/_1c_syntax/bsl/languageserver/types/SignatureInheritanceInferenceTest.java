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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Метод с однострочным docblock'ом {@code // См. ДругойМетод} наследует типы
 * параметров от целевого метода — по имени параметра.
 */
@CleanupContextBeforeClassAndAfterClass
class SignatureInheritanceInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void parameterTypesInheritedFromLinkedMethod() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/SignatureInheritance.bsl");

    var aTypes = inferAtMarker(documentContext, "X = А", "X = ".length());
    assertThat(aTypes.refs())
      .as("А наследует тип Число от ИнтерфейсныйМетод.А")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Число");

    var bTypes = inferAtMarker(documentContext, "Y = Б", "Y = ".length());
    assertThat(bTypes.refs())
      .as("Б наследует тип Строка от ИнтерфейсныйМетод.Б")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Строка");
  }

  private TypeSet inferAtMarker(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine));
  }
}
