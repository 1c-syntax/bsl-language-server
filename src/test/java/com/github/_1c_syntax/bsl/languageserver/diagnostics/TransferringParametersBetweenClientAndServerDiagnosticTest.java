/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class TransferringParametersBetweenClientAndServerDiagnosticTest extends AbstractDiagnosticTest<TransferringParametersBetweenClientAndServerDiagnostic> {
  TransferringParametersBetweenClientAndServerDiagnosticTest() {
    super(TransferringParametersBetweenClientAndServerDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasMessageOnRange(getMessage("Парам1", "Сервер1"), 6, 18, 24)
      .hasSize(1);
  }

  @Test
  void testWithCachedValues() {
    var code = """
      &НаКлиенте
      Перем КэшированныеЗначения;
      
      &НаКлиенте
      Процедура Клиент1()
          Сервер1(2);
      КонецПроцедуры
      
      &НаСервере
      Процедура Сервер1(КэшированныеЗначения) // не ошибка - есть переменная &НаКлиенте
          Метод(КэшированныеЗначения);
      КонецПроцедуры
      
      &НаКлиенте
      Процедура Клиент2()
          Сервер2(2);
      КонецПроцедуры
      
      &НаСервере
      Процедура Сервер2(ДругойПарам) // ошибка - нет переменной с таким именем
          Метод(ДругойПарам);
      КонецПроцедуры
      
      &НаСервере
      Процедура Метод(Парам)
      КонецПроцедуры
      """;
    
    var documentContext = TestUtils.getDocumentContext(code);
    
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("cachedValueNames", "КэшированныеЗначения");
    diagnosticInstance.configure(configuration);
    
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .hasMessageOnRange(getMessage("ДругойПарам", "Сервер2"), 19, 18, 29)
      .hasSize(1);
  }

  @Test
  void testWithCachedValuesButNoVariable() {
    var code = """
      &НаКлиенте
      Процедура Клиент1()
          Сервер1(2);
      КонецПроцедуры
      
      &НаСервере
      Процедура Сервер1(КэшированныеЗначения) // ошибка - нет переменной &НаКлиенте
          Метод(КэшированныеЗначения);
      КонецПроцедуры
      
      &НаСервере
      Процедура Метод(Парам)
      КонецПроцедуры
      """;
    
    var documentContext = TestUtils.getDocumentContext(code);
    
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("cachedValueNames", "КэшированныеЗначения");
    diagnosticInstance.configure(configuration);
    
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .hasMessageOnRange(getMessage("КэшированныеЗначения", "Сервер1"), 6, 18, 38)
      .hasSize(1);
  }

  @Test
  void testWithCachedValuesButNotClientVariable() {
    var code = """
      &НаСервере
      Перем КэшированныеЗначения;
      
      &НаКлиенте
      Процедура Клиент1()
          Сервер1(2);
      КонецПроцедуры
      
      &НаСервере
      Процедура Сервер1(КэшированныеЗначения) // ошибка - переменная не &НаКлиенте
          Метод(КэшированныеЗначения);
      КонецПроцедуры
      
      &НаСервере
      Процедура Метод(Парам)
      КонецПроцедуры
      """;
    
    var documentContext = TestUtils.getDocumentContext(code);
    
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("cachedValueNames", "КэшированныеЗначения");
    diagnosticInstance.configure(configuration);
    
    var diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .hasMessageOnRange(getMessage("КэшированныеЗначения", "Сервер1"), 9, 18, 38)
      .hasSize(1);
  }

  private String getMessage(String paramName, String methodName) {
    return String.format("Установите модификатор \"Знач\" для параметра %s метода %s",
      paramName, methodName);
  }


}
