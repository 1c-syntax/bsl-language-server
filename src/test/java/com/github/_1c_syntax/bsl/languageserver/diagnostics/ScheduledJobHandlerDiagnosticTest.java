/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.Module;
import com.github._1c_syntax.bsl.mdo.ModuleOwner;
import com.github._1c_syntax.bsl.mdo.ScheduledJob;
import com.github._1c_syntax.bsl.mdo.support.Handler;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        diagnostic -> diagnostic.getRange().equals(getRange()))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Укажите существующий обработчик вместо несуществующего \"ПервыйОбщийМодуль.НесуществующийМетод\" у регламентного задания \"РегламентноеЗаданиеНесуществующийМетод\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Добавьте \"Экспорт\" методу \"ПервыйОбщийМодуль.Тест\" или исправьте некорректный обработчик регламентного задания \"РегламентноеЗаданиеПриватныйМетод\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Добавьте код в тело обработчика \"ПервыйОбщийМодуль.НеУстаревшаяПроцедура\" регламентного задания \"РегламентноеЗадание1\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Добавьте код в тело обработчика \"ПервыйОбщийМодуль.НеУстаревшаяПроцедура\" регламентного задания \"РегламентноеЗадание2\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Исправьте дубли использования одного обработчика \"ПервыйОбщийМодуль.НеУстаревшаяПроцедура\" в разных регламентных заданиях. Задания: \"РегламентноеЗадание1, РегламентноеЗадание2\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Исправьте некорректный обработчик \"ПервыйОбщийМодуль.ВерсионированиеПриЗаписи\" предопределенного регламентного задания \"РегламентноеЗаданиеПредопределенноеНесколькоПараметров\" - у метода не должно быть параметров"))
      .hasSize(6)
    ;

  }

  @Test
  void testEmptyHandler() {
    final var methodPath = "";

    List<Diagnostic> diagnostics = checkMockHandler(methodPath);

    assertThat(diagnostics, true)
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Укажите существующий обработчик вместо несуществующего \"\" у регламентного задания \"РегламентноеЗадание1\""))
      .hasSize(1)
    ;
  }

  @Test
  void testMissingModule() {
    final var methodPath = "ОбщийМодуль.НесуществующийМодуль.КакойТоМетод";

    List<Diagnostic> diagnostics = checkMockHandler(methodPath);

    assertThat(diagnostics, true)
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Создайте общий модуль \"НесуществующийМодуль\" или исправьте некорректный обработчик регламентного задания \"РегламентноеЗадание1\""))
      .hasSize(1)
    ;
  }

  @Test
  void testNonServerModule() {
    final var methodPath = "ОбщийМодуль.КлиентскийОбщийМодуль.ОбработчикОписаниеОповещения";

    List<Diagnostic> diagnostics = checkMockHandler(methodPath);

    assertThat(diagnostics, true)
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Установите флаг \"Сервер\" общему модулю \"КлиентскийОбщийМодуль\" или исправьте некорректный обработчик регламентного задания \"РегламентноеЗадание1\""))
      .hasSize(1)
    ;
  }

  @Test
  void testMethodWithParams() {
    final var methodPath = "ОбщийМодуль.ПервыйОбщийМодуль.ОбработчикСПараметрами";

    List<Diagnostic> diagnostics = checkMockHandler(methodPath);

    assertThat(diagnostics, true).isEmpty();
  }

  @Test
  void testEmptyMethodContent() {

    List<Diagnostic> diagnostics = checkMockHandler("ОбщийМодуль.ПервыйОбщийМодуль.ОбработчикБезТела");

    assertThat(diagnostics, true)
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Добавьте код в тело обработчика \"ПервыйОбщийМодуль.ОбработчикБезТела\" регламентного задания \"РегламентноеЗадание1\""))
      .hasSize(1)
    ;
  }

  @Test
  void testNonEmptyMethodContent_Variable() {

    List<Diagnostic> diagnostics = checkMockHandler("ОбщийМодуль.ПервыйОбщийМодуль.ОбработчикСТелом1");

    assertThat(diagnostics, true)
      .hasSize(0)
    ;
  }

  @Test
  void testNonEmptyMethodContent_HasCall() {

    List<Diagnostic> diagnostics = checkMockHandler("ОбщийМодуль.ПервыйОбщийМодуль.ОбработчикСТелом2");

    assertThat(diagnostics, true)
      .hasSize(0)
    ;
  }

  private List<Diagnostic> checkMockHandler(String methodPath) {
    initServerContext(Absolute.path(PATH_TO_METADATA));
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    List<MD> children = new ArrayList<>();

    context.getConfiguration().getChildren().forEach(mdo -> {
      var spyMDO = spy(mdo);
      if (mdo instanceof ScheduledJob) {
        when(((ScheduledJob) spyMDO).getMethodName()).thenReturn(new Handler(methodPath));

        if (mdo.getName().equalsIgnoreCase("РегламентноеЗадание1")) {
          children.add(spyMDO);
        }
      } else if (mdo instanceof ModuleOwner moduleOwner) {
        List<Module> modules = moduleOwner.getModules().stream()
          .filter(mdoModule -> mdoModule.getModuleType() == ModuleType.ManagerModule)
          .collect(Collectors.toList());
        when(((ModuleOwner) spyMDO).getModules()).thenReturn(modules);
      }
    });

    var configuration = spy(context.getConfiguration());
    when(configuration.getChildren()).thenReturn(children);
    var serverContext = spy(documentContext.getServerContext());
    when(serverContext.getConfiguration()).thenReturn(configuration);
    final var varServerModuleMdoRef = MdoReference.create(MDOType.COMMON_MODULE, "ПервыйОбщийМодуль").getMdoRef();
    when(serverContext.getDocument(varServerModuleMdoRef, ModuleType.CommonModule)).thenReturn(Optional.of(documentContext));
    when(documentContext.getServerContext()).thenReturn(serverContext);

    final var diagnostics = getDiagnostics(documentContext);
    assertThat(diagnostics, true)
      .allMatch(
        diagnostic -> diagnostic.getRange().equals(getRange()));

    return diagnostics;
  }

  private static Range getRange() {
    return Ranges.create(0, 0, 9);
  }
}
