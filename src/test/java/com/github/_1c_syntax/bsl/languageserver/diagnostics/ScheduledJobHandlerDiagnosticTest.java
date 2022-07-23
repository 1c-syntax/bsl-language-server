/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.support.Handler;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import com.github._1c_syntax.mdclasses.mdo.MDScheduledJob;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@CleanupContextBeforeClassAndAfterEachTestMethod
class ScheduledJobHandlerDiagnosticTest extends AbstractDiagnosticTest<ScheduledJobHandlerDiagnostic> {
  ScheduledJobHandlerDiagnosticTest() {
    super(ScheduledJobHandlerDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  @Test
  void test() {

    initServerContext(Absolute.path(PATH_TO_METADATA));
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .allMatch(
        diagnostic -> diagnostic.getRange().equals(Ranges.create(0, 0, 8)))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Укажите существующий обработчик вместо несуществующего \"ПервыйОбщийМодуль.НесуществующийМетод\" у регламентного задания \"РегламентноеЗаданиеНесуществующийМетод\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Добавьте \"Экспорт\" методу \"ПервыйОбщийМодуль.Тест\" или исправьте некорректный обработчик регламентного задания \"РегламентноеЗаданиеПриватныйМетод\""))
      .hasSize(2)
    ;

  }

  @Test
  void testEmptyHandler() {

    initServerContext(Absolute.path(PATH_TO_METADATA));
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    Set<AbstractMDObjectBase> children = new HashSet<>();

    context.getConfiguration().getChildren().forEach(mdo -> {
      if (mdo instanceof MDScheduledJob) {
        var spyMDO = spy(mdo);
        when(((MDScheduledJob) spyMDO).getHandler()).thenReturn(new Handler(""));

        if (mdo.getName().equalsIgnoreCase("РегламентноеЗадание1")) {
          children.add(spyMDO);
        }
      }
    });

    var configuration = spy(context.getConfiguration());
    when(configuration.getChildren()).thenReturn(children);
    var context = spy(documentContext.getServerContext());
    when(context.getConfiguration()).thenReturn(configuration);
    when(documentContext.getServerContext()).thenReturn(context);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .allMatch(
        diagnostic -> diagnostic.getRange().equals(Ranges.create(0, 0, 8)))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Укажите существующий обработчик вместо несуществующего \"\" у регламентного задания \"РегламентноеЗадание1\""))
      .hasSize(1)
    ;
  }

  @Test
  void testMissingModule() {

    initServerContext(Absolute.path(PATH_TO_METADATA));
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    Set<AbstractMDObjectBase> children = new HashSet<>();

    context.getConfiguration().getChildren().forEach(mdo -> {
      if (mdo instanceof MDScheduledJob) {
        var spyMDO = spy(mdo);
        when(((MDScheduledJob) spyMDO).getHandler()).thenReturn(new Handler("ОбщийМодуль.НесуществующийМодуль.КакойТоМетод"));

        if (mdo.getName().equalsIgnoreCase("РегламентноеЗадание1")) {
          children.add(spyMDO);
        }
      }
    });

    var configuration = spy(context.getConfiguration());
    when(configuration.getChildren()).thenReturn(children);
    var context = spy(documentContext.getServerContext());
    when(context.getConfiguration()).thenReturn(configuration);
    when(documentContext.getServerContext()).thenReturn(context);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .allMatch(
        diagnostic -> diagnostic.getRange().equals(Ranges.create(0, 0, 8)))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Создайте общий модуль \"НесуществующийМодуль\" или исправьте некорректный обработчик регламентного задания \"РегламентноеЗадание1\""))
      .hasSize(1)
    ;
  }

  @Test
  void testNonServerModule() {

    initServerContext(Absolute.path(PATH_TO_METADATA));
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    Set<AbstractMDObjectBase> children = new HashSet<>();

    context.getConfiguration().getChildren().forEach(mdo -> {
      if (mdo instanceof MDScheduledJob) {
        var spyMDO = spy(mdo);
        when(((MDScheduledJob) spyMDO).getHandler()).thenReturn(new Handler("ОбщийМодуль.КлиентскийОбщийМодуль.ОбработчикОписаниеОповещения"));

        if (mdo.getName().equalsIgnoreCase("РегламентноеЗадание1")) {
          children.add(spyMDO);
        }
      }
    });

    var configuration = spy(context.getConfiguration());
    when(configuration.getChildren()).thenReturn(children);
    var context = spy(documentContext.getServerContext());
    when(context.getConfiguration()).thenReturn(configuration);
    when(documentContext.getServerContext()).thenReturn(context);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .allMatch(
        diagnostic -> diagnostic.getRange().equals(Ranges.create(0, 0, 8)))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Установите флаг \"Сервер\" общему модулю \"КлиентскийОбщийМодуль\" или исправьте некорректный обработчик регламентного задания \"РегламентноеЗадание1\""))
      .hasSize(1)
    ;
  }
}
