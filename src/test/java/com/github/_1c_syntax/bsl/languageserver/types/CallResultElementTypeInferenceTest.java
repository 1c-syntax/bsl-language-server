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

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тип элемента коллекции у значения, возвращённого методом: обход результата вызова
 * должен давать тот же тип элемента, что и обход переменной с этим значением.
 */
@CleanupContextBeforeClassAndAfterClass
class CallResultElementTypeInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  @Test
  void forEachOverCallResultKnowsRowColumns() {
    // given: функция возвращает табличную часть справочника.
    var documentContext = TestUtils.getDocumentContext("""
      // Возвращаемое значение:
      //  СправочникТабличнаяЧасть.Справочник1.ТабличнаяЧасть1 -
      Функция ЧастьТовары()
      	Возврат Неопределено;
      КонецФункции

      Процедура Обход()
      	Для Каждого СтрокаЧасти Из ЧастьТовары() Цикл
      		КолонкаСтроки = СтрокаЧасти.Реквизит1;
      	КонецЦикла;
      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "КолонкаСтроки = СтрокаЧасти.Реквизит1", "КолонкаСтроки = СтрокаЧасти.".length());

    // then: колонка строки типизирована так же, как при обходе переменной.
    assertThat(names(types)).containsExactly("Строка");
  }

  @Test
  void forEachOverCallResultGivesPlatformElementType() {
    // given: функция возвращает соответствие — у него тип элемента известен реестру.
    var documentContext = TestUtils.getDocumentContext("""
      // Возвращаемое значение:
      //  Соответствие -
      Функция НовыеДанные()
      	Возврат Новый Соответствие;
      КонецФункции

      Процедура Обход()
      	Для Каждого КлючЗначение Из НовыеДанные() Цикл
      		ЭлементОбхода = КлючЗначение;
      	КонецЦикла;
      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "ЭлементОбхода = КлючЗначение", "ЭлементОбхода = ".length());

    // then
    assertThat(names(types)).containsExactly("КлючИЗначение");
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
