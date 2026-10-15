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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.LazyTypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Objects;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ленивое поле, заданное {@code см.}-ссылкой на функцию модуля, переживает перечитывание
 * документа с тем же текстом и не держит прежнее дерево символов.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
class SeeReferenceReleaseTest extends AbstractServerContextAwareTest {

  private static final String MODULE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String TEXT = """
    // Возвращаемое значение:
    //  Структура:
    //   * Содержимое - см. Коробка
    Функция Контейнер() Экспорт
    \tВозврат Новый Структура("Содержимое");
    КонецФункции

    // Возвращаемое значение:
    //  Структура:
    //   * Описание - Строка
    Функция Коробка() Экспорт
    \tВозврат Новый Структура("Описание", "");
    КонецФункции
    """;

  @Autowired
  private SymbolTypeIndex symbolTypeIndex;

  @Test
  void lazyFieldSurvivesRereadWithoutPreviousTree() {
    // given: поле «Содержимое» ссылается на функцию «Коробка» того же модуля.
    var documentContext = document();
    var previousTree = new WeakReference<ModuleSymbol>(documentContext.getSymbolTree().getModule());
    var content = lazyField(documentContext, "Контейнер", "Содержимое");

    // when: тот же текст перечитан — дерево новое.
    context.rebuildDocument(documentContext, TEXT, 2);

    // then: поле по-прежнему разворачивается в значение функции, а прежнее дерево ничем не
    // удерживается, хотя само поле живо.
    assertThat(content.get().isEmpty()).as("поле разворачивается").isFalse();
    for (var attempt = 0; attempt < 10 && previousTree.get() != null; attempt++) {
      System.gc();
    }
    assertThat(previousTree.get()).as("прежнее дерево освобождено").isNull();
  }

  private DocumentContext document() {
    initServerContext(PATH_TO_METADATA);
    var documentContext = context.addDocument(Absolute.uri(new File(PATH_TO_METADATA, MODULE)));
    context.rebuildDocument(documentContext, TEXT, 1);
    return documentContext;
  }

  private LazyTypeSet lazyField(DocumentContext documentContext, String function, String field) {
    var method = documentContext.getSymbolTree().getMethodSymbol(function).orElseThrow();
    return symbolTypeIndex.getDeclaredReturnTypes(method).lazyFields().values().stream()
      .map(byName -> byName.get(field))
      .filter(Objects::nonNull)
      .findFirst()
      .orElseThrow()
      .types();
  }
}
