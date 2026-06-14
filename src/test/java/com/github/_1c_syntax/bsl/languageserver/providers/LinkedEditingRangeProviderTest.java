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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.LinkedEditingRangeParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class LinkedEditingRangeProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private LinkedEditingRangeProvider linkedEditingRangeProvider;

  private static final String PATH_TO_FILE = "./src/test/resources/providers/linkedEditingRange.bsl";

  @BeforeEach
  void prepareServerContext() {
    initServerContextOnce(Path.of(PATH_TO_METADATA));
  }

  @Test
  void testLocalVariable() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    // курсор на объявлении локальной переменной "ЛокальнаяПеременная"
    var params = new LinkedEditingRangeParams();
    params.setPosition(new Position(4, 8));

    // when
    var ranges = linkedEditingRangeProvider.getLinkedEditingRanges(documentContext, params);

    // then
    assertThat(ranges).isNotNull();
    assertThat(ranges.getRanges())
      .containsExactlyInAnyOrder(
        Ranges.create(4, 4, 23),
        Ranges.create(5, 4, 23),
        Ranges.create(5, 26, 45),
        Ranges.create(6, 26, 45)
      );
  }

  @Test
  void testLocalVariableWhenCaretRightAfterIdentifier() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    // курсор сразу ПОСЛЕ объявления локальной переменной "ЛокальнаяПеременная" (на её конце),
    // воспроизводит дребезг VS Code при наборе символов во время переименования
    var params = new LinkedEditingRangeParams();
    params.setPosition(new Position(4, 23));

    // when
    var ranges = linkedEditingRangeProvider.getLinkedEditingRanges(documentContext, params);

    // then
    assertThat(ranges).isNotNull();
    assertThat(ranges.getRanges())
      .containsExactlyInAnyOrder(
        Ranges.create(4, 4, 23),
        Ranges.create(5, 4, 23),
        Ranges.create(5, 26, 45),
        Ranges.create(6, 26, 45)
      );
  }

  @Test
  void testWordPatternIsProvided() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    // курсор на объявлении локальной переменной "ЛокальнаяПеременная"
    var params = new LinkedEditingRangeParams();
    params.setPosition(new Position(4, 8));

    // when
    var ranges = linkedEditingRangeProvider.getLinkedEditingRanges(documentContext, params);

    // then
    assertThat(ranges).isNotNull();
    assertThat(ranges.getWordPattern()).isNotNull();
    assertThat("ЛокальнаяПеременная".matches(ranges.getWordPattern())).isTrue();
    // wordPattern должен распознавать кириллический идентификатор с Ё/ё
    assertThat("СчётчикЦикла".matches(ranges.getWordPattern())).isTrue();
    // VS Code компилирует wordPattern без флага u, поэтому свойства Unicode \p{...}
    // молча перестают работать для кириллицы — защищаемся от регресса к сломанной форме
    assertThat(ranges.getWordPattern()).doesNotContain("\\p");
  }

  @Test
  void testParameter() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    // курсор на использовании параметра "Параметр"
    var params = new LinkedEditingRangeParams();
    params.setPosition(new Position(5, 50));

    // when
    var ranges = linkedEditingRangeProvider.getLinkedEditingRanges(documentContext, params);

    // then
    assertThat(ranges).isNotNull();
    assertThat(ranges.getRanges())
      .containsExactlyInAnyOrder(
        Ranges.create(2, 28, 36),
        Ranges.create(5, 48, 56)
      );
  }

  @Test
  void testMethodReturnsNull() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    // курсор на имени метода "ТестоваяПроцедура"
    var params = new LinkedEditingRangeParams();
    params.setPosition(new Position(10, 5));

    // when
    var ranges = linkedEditingRangeProvider.getLinkedEditingRanges(documentContext, params);

    // then
    assertThat(ranges).isNull();
  }

  @Test
  void testCommonModuleReturnsNull() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    // курсор на обращении к общему модулю / менеджеру справочника
    var params = new LinkedEditingRangeParams();
    params.setPosition(new Position(12, 30));

    // when
    var ranges = linkedEditingRangeProvider.getLinkedEditingRanges(documentContext, params);

    // then
    assertThat(ranges).isNull();
  }

  @Test
  void testKeywordReturnsNull() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    // курсор на ключевом слове "Процедура"
    var params = new LinkedEditingRangeParams();
    params.setPosition(new Position(2, 3));

    // when
    var ranges = linkedEditingRangeProvider.getLinkedEditingRanges(documentContext, params);

    // then
    assertThat(ranges).isNull();
  }
}
