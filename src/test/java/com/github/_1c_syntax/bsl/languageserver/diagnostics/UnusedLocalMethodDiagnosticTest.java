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

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.registry.BslContextHolder;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class UnusedLocalMethodDiagnosticTest extends AbstractDiagnosticTest<UnusedLocalMethodDiagnostic> {
  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = PATH_TO_METADATA + "/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_MODULE_CONTENT = "src/test/resources/diagnostics/UnusedLocalMethodDiagnostic.bsl";
  private static final String MANAGED_FORM_MODULE =
    "Documents/Документ1/Forms/ФормаДокумента/Ext/Form/Module.bsl";

  @Autowired
  private ReferenceIndex referenceIndex;

  @Autowired
  private EventContractsIndex eventContractsIndex;

  private CommonModule module;
  private DocumentContext documentContext;

  UnusedLocalMethodDiagnosticTest() {
    super(UnusedLocalMethodDiagnostic.class);
  }

  @Test
  void test() {
    var dc = spy(TestUtils.getDocumentContext(readFixture()));
    when(dc.getModuleType()).thenReturn(ModuleType.CommonModule);
    List<Diagnostic> diagnostics = getDiagnostics(dc);
    checkByDefault(diagnostics);
  }

  @SneakyThrows
  private static String readFixture() {
    return FileUtils.readFileToString(
      Path.of(PATH_TO_MODULE_CONTENT).toFile(), StandardCharsets.UTF_8);
  }

  private static void checkByDefault(List<Diagnostic> diagnostics) {
    // ПриСозданииОбъекта (66, 10, 28) — здесь не событие: это имя события OScript-класса,
    // а не CommonModule/ObjectModule, поэтому метод действительно неиспользуемый.
    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(1, 10, 24)
      .hasRange(66, 10, 28)
      .hasRange(70, 10, 41)
    ;
  }

  @Test
  void testObjectModuleByDefault() {
    getObjectModuleDocumentContext();

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testConfigure() {
    // given
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("attachableMethodPrefixes", "ПодключаемаяМоя_");
    diagnosticInstance.configure(configuration);

    var dc = spy(TestUtils.getDocumentContext(readFixture()));
    when(dc.getModuleType()).thenReturn(ModuleType.CommonModule);

    // when
    List<Diagnostic> diagnostics = getDiagnostics(dc);

    // then
    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(1, 10, 24)
      .hasRange(60, 10, 40)
      .hasRange(63, 10, 39)
      .hasRange(66, 10, 28)
    ;
  }

  @Test
  void testObjectModuleWithEnabledConfiguration() {
    // given
    getObjectModuleDocumentContext();

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("checkObjectModule", true);
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    // then
    checkByDefault(diagnostics);
  }

  @Test
  void testOScriptClassConstructorEventNotFlaggedByRuName() {
    var content = """
      Процедура ПриСозданииОбъекта(Знач Параметр)
      КонецПроцедуры
      """;
    var dc = spy(TestUtils.getDocumentContext(content));
    when(dc.getModuleType()).thenReturn(ModuleType.OScriptClass);

    List<Diagnostic> diagnostics = getDiagnostics(dc);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testOScriptClassConstructorEventNotFlaggedByEnName() {
    var content = """
      Процедура OnObjectCreate(Знач Параметр)
      КонецПроцедуры
      """;
    var dc = spy(TestUtils.getDocumentContext(content));
    when(dc.getModuleType()).thenReturn(ModuleType.OScriptClass);

    List<Diagnostic> diagnostics = getDiagnostics(dc);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testManagedFormFlagsOnlyMethodsThatAreNotHandlers() {
    // Обработчики управляемой формы объявлены в Form.xml и висят EVENT-членами на её
    // типе: и событие самой формы, и событие элемента, и действие команды.
    var formModule = formModuleWith("""
      &НаСервере
      Процедура ПриЗаписиНаСервере(Отказ, ТекущийОбъект, ПараметрыЗаписи)
      КонецПроцедуры

      &НаКлиенте
      Процедура Реквизит1ПриИзменении(Элемент)
      КонецПроцедуры

      &НаКлиенте
      Процедура ЗаполнитьПоОснованиюКоманда(Команда)
      КонецПроцедуры

      &НаКлиенте
      Процедура ЗабытыйМетод()
      КонецПроцедуры
      """);

    List<Diagnostic> diagnostics = getDiagnostics(formModule);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true).hasRange(13, 10, 22);
  }

  @Test
  void testHandlerAttachedByNameIsNotFlagged() {
    // Обработчик ожидания подключается именем-строкой, и это единственное обращение к
    // нему в модуле. Такой вызов индексируется как ссылка (см. AttachedHandlers) —
    // иначе на управляемых формах реальной конфигурации набегают сотни ложных.
    var formModule = formModuleWith("""
      &НаКлиенте
      Процедура ПодключитьПроверку()
        ПодключитьОбработчикОжидания("ПроверитьФоновоеЗадание", 1, Истина);
        Элементы.ТабличнаяЧасть1.УстановитьДействие("ПриАктивизацииСтроки", "СтрокаАктивизирована");
      КонецПроцедуры

      &НаКлиенте
      Процедура ПроверитьФоновоеЗадание()
      КонецПроцедуры

      &НаКлиенте
      Процедура СтрокаАктивизирована(Элемент)
      КонецПроцедуры
      """);

    List<Diagnostic> diagnostics = getDiagnostics(formModule);

    assertThat(diagnostics)
      .as("сама ПодключитьПроверку никем не вызвана, а подключённые ею — вызваны")
      .hasSize(1);
    assertThat(diagnostics, true).hasRange(1, 10, 28);
  }

  @Test
  void testOrdinaryFormIsSkipped() {
    // У обычной формы своя иерархия элементов со своими событиями, в системе типов
    // не смоделированная, — там забытым выглядел бы любой обработчик.
    var dc = spy(TestUtils.getDocumentContext(readFixture()));
    var form = mock(Form.class);
    when(form.getFormType()).thenReturn(FormType.ORDINARY);
    when(dc.getModuleType()).thenReturn(ModuleType.FormModule);
    when(dc.getMdObject()).thenReturn(Optional.of(form));

    assertThat(getDiagnostics(dc)).isEmpty();
  }

  @Test
  void testUnknownModuleTypeIsSkipped() {
    // #4326: одиночный .bsl, открытый вне проекта. Владельца модуля, а значит и его
    // событий, не видно — каждый обработчик выглядит методом, которого никто не зовёт.
    // Проверять надо на экземпляре, который считает синтакс-помощник загруженным:
    // без HBK этот тип модуля и так не диагностируется, и тест бы ничего не доказывал.
    var dc = spy(TestUtils.getDocumentContext(readFixture()));
    when(dc.getModuleType()).thenReturn(ModuleType.UNKNOWN);

    assertThat(diagnosticAsIfHbkLoaded().getDiagnostics(dc)).isEmpty();
  }

  /**
   * Экземпляр диагностики, считающий синтакс-помощник загруженным. Ветку «HBK есть»
   * иначе не проверить: на CI платформы нет, а от неё зависит, какие типы модулей
   * вообще попадают в диагностирование.
   */
  private UnusedLocalMethodDiagnostic diagnosticAsIfHbkLoaded() {
    var holder = new BslContextHolder(null) {
      @Override
      public Optional<ContextProvider> get() {
        return Optional.of(mock(ContextProvider.class));
      }
    };
    var diagnostic = new UnusedLocalMethodDiagnostic(referenceIndex, eventContractsIndex, holder);
    diagnostic.setInfo(diagnosticInstance.getInfo());
    return diagnostic;
  }

  /** Модуль управляемой формы документа с подменённым содержимым. */
  private DocumentContext formModuleWith(String content) {
    initServerContext(Absolute.path(PATH_TO_METADATA));
    var uri = Absolute.uri(Path.of(PATH_TO_METADATA, MANAGED_FORM_MODULE).toFile());
    var formModule = context.addDocument(uri);
    context.rebuildDocument(formModule, content, 1);
    return formModule;
  }

  private void getObjectModuleDocumentContext() {
    Path testFile = Path.of(PATH_TO_MODULE_CONTENT).toAbsolutePath();
    getDocumentContextFromFile(testFile);
    when(documentContext.getModuleType()).thenReturn(ModuleType.ObjectModule);
    when(documentContext.getMdObject()).thenReturn(Optional.of(module));
  }

  @SneakyThrows
  void getDocumentContextFromFile(Path testFile) {

    Path path = Absolute.path(PATH_TO_METADATA);
    Path moduleFile = Path.of(PATH_TO_MODULE_FILE).toAbsolutePath();

    initServerContext(path);
    var configuration = context.getConfiguration();
    documentContext = spy(TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    ));

    module = spy((CommonModule) configuration.findChild(moduleFile.toUri()).get());
  }
}
