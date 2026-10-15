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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.lang.ref.WeakReference;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Записи о методах документа, перечитанного с тем же текстом, остаются в силе, находятся по
 * символам нового дерева и не держат прежнее дерево.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
class MethodKeyTest extends AbstractServerContextAwareTest {

  private static final String MODULE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String TEXT = """
    Функция Строка() Экспорт
    \tВозврат "";
    КонецФункции

    Процедура Действие()
    КонецПроцедуры
    """;

  @Autowired
  private SymbolTypeIndex symbolTypeIndex;

  @Autowired
  private MethodReturnTypeIndexer indexer;

  @Test
  void keysOfEqualSymbolsAreEqualAndHashedLikeSymbols() {
    // given
    var documentContext = document();
    var before = method(documentContext, "Строка");

    // when: тот же текст разобран заново — дерево новое.
    context.rebuildDocument(documentContext, TEXT, 2);
    var after = method(documentContext, "Строка");

    // then
    assertThat(after).isNotSameAs(before).isEqualTo(before);
    assertThat(MethodKey.of(after))
      .isEqualTo(MethodKey.of(before))
      .hasSameHashCodeAs(before)
      .isNotEqualTo(MethodKey.of(method(documentContext, "Действие")));
  }

  @Test
  void rereadDocumentKeepsValuesAndReleasesPreviousTree() {
    // given: значение функции посчитано по первому дереву.
    var documentContext = document();
    var value = symbolTypeIndex.getReturnTypes(method(documentContext, "Строка"));
    assertThat(value.isEmpty()).as("значение посчитано").isFalse();
    var previousTree = new WeakReference<ModuleSymbol>(documentContext.getSymbolTree().getModule());

    // when: тот же текст перечитан — дерево новое, записи о методах прежние.
    context.rebuildDocument(documentContext, TEXT, 2);

    // then: значение находится по символу нового дерева, а прежнее дерево больше ничем не
    // удерживается.
    var method = method(documentContext, "Строка");
    assertThat(symbolTypeIndex.getReturnTypes(method)).isEqualTo(value);
    assertThat(indexer.isIndexed(method)).isTrue();
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

  private static MethodSymbol method(DocumentContext documentContext, String name) {
    return documentContext.getSymbolTree().getMethodSymbol(name).orElseThrow();
  }
}
