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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ссылка {@code См.} на объект метаданных в нотации конфигуратора: табличную часть
 * объекта, её реквизит, реквизит самого объекта.
 */
@CleanupContextBeforeClassAndAfterClass
class MetadataSeeRefInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUpWorkspace() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
  }

  @Test
  void seeRefToTabularSection() {
    // given: «См. Справочник.Справочник1.ТабличнаяЧасть1» на параметре.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/MetadataSeeRef.bsl");

    // when
    var types = at(documentContext, "ЧастьТаблицы = ТабличнаяЧасть", "ЧастьТаблицы = ".length());

    // then
    assertThat(names(types)).containsExactly("СправочникТабличнаяЧасть.Справочник1.ТабличнаяЧасть1");
  }

  @Test
  void tabularSectionRowKnowsItsColumns() {
    // given: обход табличной части даёт её строку с колонками.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/MetadataSeeRef.bsl");

    // when
    var types = at(documentContext, "КолонкаСтроки = СтрокаТЧ.Реквизит1", "КолонкаСтроки = СтрокаТЧ.".length());

    // then
    assertThat(names(types)).containsExactly("Строка");
  }

  @Test
  void seeRefToTabularSectionAttribute() {
    // given: «См. Справочник.Справочник1.ТабличнаяЧасть1.Реквизит1» — тип реквизита.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/MetadataSeeRef.bsl");

    // when
    var types = at(documentContext, "РеквизитЧасти = РеквизитТЧ", "РеквизитЧасти = ".length());

    // then
    assertThat(names(types)).containsExactly("Строка");
  }

  @Test
  void seeRefToObjectAttribute() {
    // given: «См. Справочник.Справочник1.Реквизит2» — реквизит самого объекта.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/MetadataSeeRef.bsl");

    // when
    var types = at(documentContext, "РеквизитСправочника = РеквизитОбъекта", "РеквизитСправочника = ".length());

    // then
    assertThat(names(types)).containsExactly("Число");
  }

  @Test
  void seeRefWithExtraSegmentsResolvesToNothing() {
    // given: путь длиннее поддерживаемого — «…ТабличнаяЧасть1.Реквизит1.Лишнее».
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/MetadataSeeRef.bsl");

    // when
    var types = at(documentContext, "ЗначениеЛишнегоПути = ЛишнийПуть", "ЗначениеЛишнегоПути = ".length());

    // then: лишний хвост не отбрасывается — ссылка не разрешается вовсе.
    assertThat(names(types)).isEmpty();
  }

  private TypeSet at(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    var markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер '%s' найден в фикстуре", marker).isNotNegative();
    var targetOffset = markerStart + offsetInMarker;
    var lineStart = content.lastIndexOf('\n', targetOffset - 1) + 1;
    var line = content.substring(0, targetOffset).split("\n").length - 1;
    return typeService.expressionTypesAt(documentContext, new Position(line, targetOffset - lineStart + 1));
  }

  private static List<String> names(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
