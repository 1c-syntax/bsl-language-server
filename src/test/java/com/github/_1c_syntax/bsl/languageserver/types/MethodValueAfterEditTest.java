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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Значение функции, пересчитанное при правке её модуля, считается по новому тексту целиком.
 * <p>
 * Правка сдвигает строки, а часть записей, по которым считается значение, привязана к символу
 * переменной — у нового текста он равен прежнему, пока объявление на месте. Переживи такие
 * записи правку до пересчёта, позиции изменений переменной указали бы мимо операторов нового
 * текста, и переменная выпала бы из расчёта.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
class MethodValueAfterEditTest extends AbstractServerContextAwareTest {

  private static final String MODULE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";

  @Autowired
  private SymbolTypeIndex symbolTypeIndex;

  @Autowired
  private ExpressionTypeInferencer inferencer;

  @Test
  void valueRecomputedOnEditKeepsFieldsBelowInsertedLine() {
    // given: функция собирает структуру вставками, её значение посчитано.
    initServerContext(PATH_TO_METADATA);
    var documentContext = context.addDocument(Absolute.uri(new File(PATH_TO_METADATA, MODULE)));
    context.rebuildDocument(documentContext, source(""), 1);
    assertThat(fieldsOfValue(documentContext)).contains("Статус", "Вывод");
    // Между правками типы выводят запросы редактора — подсказки, диагностики, раскраска — и
    // заполняют кэши расчёта по текущему тексту.
    inferencer.computeReturnTypes(method(documentContext));

    // when: выше вставок добавлена строка — их позиции сдвинулись.
    context.rebuildDocument(documentContext, source("\t// строка выше вставок\n"), 2);

    // then: значение, пересчитанное при правке, по-прежнему со всеми полями.
    assertThat(fieldsOfValue(documentContext)).contains("Статус", "Вывод");
  }

  private static String source(String inserted) {
    return """
      Функция Запись() Экспорт
      \tЗапись = Новый Структура;
      """ + inserted + """
      \tЗапись.Вставить("Статус", 1);
      \tЕсли Истина Тогда
      \t\tЗапись.Вставить("Вывод", "");
      \tКонецЕсли;
      \tВозврат Запись;
      КонецФункции
      """;
  }

  private Set<String> fieldsOfValue(DocumentContext documentContext) {
    return symbolTypeIndex.getReturnTypes(method(documentContext)).getAllFieldNames();
  }

  private static MethodSymbol method(DocumentContext documentContext) {
    return documentContext.getSymbolTree().getMethodSymbol("Запись").orElseThrow();
  }
}
