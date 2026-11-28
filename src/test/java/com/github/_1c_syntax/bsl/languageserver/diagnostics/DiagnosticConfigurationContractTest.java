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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticObjectProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт настройки диагностик: в {@code configure} приходит карта из файла конфигурации как есть,
 * поэтому в ней может не быть ни одного параметра. Отсутствующий параметр сохраняет значение по
 * умолчанию — настройка не падает, и диагностика после неё работает.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class DiagnosticConfigurationContractTest extends AbstractServerContextAwareTest {

  private static final String CONTENT = """
    Процедура Тест(Параметр) Экспорт
      Сообщить(НСтр("ru = 'Текст'"));
      Если Параметр = 1 Тогда
        ЭтотОбъект.Реквизит = СтрШаблон("%1", Параметр); // TODO
      КонецЕсли;
    КонецПроцедуры
    """;

  @Autowired
  private DiagnosticObjectProvider diagnosticObjectProvider;

  @Test
  void everyDiagnosticAcceptsConfigurationWithoutParameters() throws Exception {
    initServerContext();
    var documentContext = TestUtils.getDocumentContext(CONTENT, context);

    var failures = new TreeMap<String, String>();
    for (var code : DiagnosticDocsLayoutTest.diagnosticCodes()) {
      var diagnosticClass = Class.forName(getClass().getPackageName() + "." + code + "Diagnostic")
        .asSubclass(BSLDiagnostic.class);
      try {
        var diagnostic = diagnosticObjectProvider.get(diagnosticClass);
        diagnostic.configure(Map.of());
        diagnostic.getDiagnostics(documentContext);
      } catch (RuntimeException e) {
        failures.put(code, e.toString());
      }
    }

    assertThat(failures).as("диагностики, падающие на настройке без параметров").isEmpty();
  }
}
