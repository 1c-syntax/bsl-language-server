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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Изменения на месте ищутся по имени получателя, а одноимённые локальные переменные разных
 * методов — разные переменные: каждой достаются только изменения из её области видимости.
 */
@CleanupContextBeforeClassAndAfterClass
class MutationsWithinVariableScopeTest extends AbstractServerContextAwareTest {

  private static final String MODULE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";

  @Autowired
  private OpenDataObjectInference openDataObjectInference;

  @Autowired
  private FormExpressionInference formExpressionInference;

  @Test
  void mutatorsOfLocalVariableComeOnlyFromItsMethod() {
    // given
    var documentContext = moduleWith("""
      Процедура Первая()
        Заголовки = Новый Структура;
        Заголовки.Вставить("А", 1);
      КонецПроцедуры

      Процедура Вторая()
        Заголовки = Новый Структура;
        Заголовки.Вставить("Б", 2);
        Заголовки.Вставить("В", 3);
      КонецПроцедуры
      """);

    // when
    var ofFirst = openDataObjectInference.mutatorsOf(localVariable(documentContext, "Первая", "Заголовки"));
    var ofSecond = openDataObjectInference.mutatorsOf(localVariable(documentContext, "Вторая", "Заголовки"));

    // then
    assertThat(ofFirst.keySet()).containsExactly(new Position(2, 2));
    assertThat(ofSecond.keySet()).containsExactly(new Position(7, 2), new Position(8, 2));
  }

  @Test
  void mutatorsOfModuleVariableComeFromAllMethods() {
    // given
    var documentContext = moduleWith("""
      Перем Заголовки;

      Процедура Первая()
        Заголовки.Вставить("А", 1);
      КонецПроцедуры

      Процедура Вторая()
        Заголовки.Вставить("Б", 2);
      КонецПроцедуры
      """);
    var symbolTree = documentContext.getSymbolTree();
    var variable = symbolTree.getVariableSymbol("Заголовки", symbolTree.getModule()).orElseThrow();

    // when
    var mutators = openDataObjectInference.mutatorsOf(variable);

    // then
    assertThat(mutators.keySet()).containsExactly(new Position(3, 2), new Position(7, 2));
  }

  @Test
  void kindAssignmentsOfLocalVariableComeOnlyFromItsMethod() {
    // given
    var documentContext = moduleWith("""
      Процедура Первая()
        Группа = Неопределено;
        Группа.Вид = ВидГруппыФормы.ОбычнаяГруппа;
      КонецПроцедуры

      Процедура Вторая()
        Группа = Неопределено;
        Группа.Вид = ВидГруппыФормы.Страницы;
      КонецПроцедуры
      """);

    // when
    var ofFirst = formExpressionInference.kindAssignmentsOf(localVariable(documentContext, "Первая", "Группа"));

    // then
    assertThat(ofFirst.keySet()).containsExactly(new Position(2, 2));
  }

  private DocumentContext moduleWith(String content) {
    initServerContext(PATH_TO_METADATA);
    var uri = Absolute.uri(new File(PATH_TO_METADATA, MODULE));
    var documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext, content, 1);
    return documentContext;
  }

  private static VariableSymbol localVariable(DocumentContext documentContext, String method, String name) {
    var symbolTree = documentContext.getSymbolTree();
    var methodSymbol = symbolTree.getMethodSymbol(method).orElseThrow();
    return symbolTree.getVariableSymbol(name, methodSymbol).orElseThrow();
  }
}
