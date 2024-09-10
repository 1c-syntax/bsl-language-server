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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdclasses.Configuration;
import com.github._1c_syntax.bsl.mdo.support.UseMode;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@DirtiesContext
class UsingSynchronousCallsDiagnosticTest extends AbstractDiagnosticTest<UsingSynchronousCallsDiagnostic> {
  UsingSynchronousCallsDiagnosticTest() {
    super(UsingSynchronousCallsDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = "src/test/resources/metadata/designer/CommonModules/КлиентскийОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_OBJECT_MODULE_FILE = "src/test/resources/metadata/designer/Catalogs/СправочникСМенеджером/Ext/ObjectModule.bsl";
  private static final String PATH_TO_MANAGER_MODULE_FILE = "src/test/resources/metadata/designer/Catalogs/СправочникСМенеджером/Ext/ManagerModule.bsl";

  @Test
  void testDontUse() {

    var documentContext = getDocumentContextWithUseFlag(UseMode.DONT_USE);
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics)
      .hasSize(28)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(2, 12, 3, 57))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*Вопрос.*ПоказатьВопрос.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(21, 4, 21, 84))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*Предупреждение.*ПоказатьПредупреждение.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(29, 4, 29, 26))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ОткрытьЗначение.*ПоказатьЗначение.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(43, 9, 43, 58))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ВвестиДату.*ПоказатьВводДаты.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(72, 9, 72, 67))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ВвестиЗначение.*ПоказатьВводЗначения.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(103, 9, 103, 50))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ВвестиСтроку.*ПоказатьВводСтроки.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(122, 9, 122, 61))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ВвестиЧисло.*ПоказатьВводЧисла.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(138, 4, 138, 50))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*УстановитьВнешнююКомпоненту.*НачатьУстановкуВнешнейКомпоненты.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(148, 4, 148, 33))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ОткрытьФормуМодально.*ОткрытьФорму.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(159, 20, 159, 56))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*УстановитьРасширениеРаботыСФайлами.*НачатьУстановкуРасширенияРаботыСФайлами.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(172, 20, 172, 62))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*УстановитьРасширениеРаботыСКриптографией.*НачатьУстановкуРасширенияРаботыСКриптографией.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(184, 12, 184, 54))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ПодключитьРасширениеРаботыСКриптографией.*НачатьПодключениеРасширенияРаботыСКриптографией.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(185, 8, 185, 129))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*Предупреждение.*ПоказатьПредупреждение.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(198, 12, 198, 48))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ПодключитьРасширениеРаботыСФайлами.*НачатьПодключениеРасширенияРаботыСФайлами.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(199, 8, 199, 109))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*Предупреждение.*ПоказатьПредупреждение.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(214, 4, 214, 88))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ПоместитьФайл.*НачатьПомещениеФайла.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(225, 4, 225, 68))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*КопироватьФайл.*НачатьКопированиеФайла.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(236, 4, 236, 69))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ПереместитьФайл.*НачатьПеремещениеФайла.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(247, 21, 247, 51))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*НайтиФайлы.*НачатьПоискФайлов.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(260, 8, 260, 37))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*УдалитьФайлы.*НачатьУдалениеФайлов.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(274, 4, 274, 29))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*СоздатьКаталог.*НачатьСозданиеКаталога.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(285, 16, 285, 40))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*КаталогВременныхФайлов.*НачатьПолучениеКаталогаВременныхФайлов.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(296, 16, 296, 35))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*КаталогДокументов.*НачатьПолучениеКаталогаДокументов.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(307, 16, 307, 50))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*РабочийКаталогДанныхПользователя.*НачатьПолучениеРабочегоКаталогаДанныхПользователя.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(318, 16, 318, 89))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ПолучитьФайлы.*НачатьПолучениеФайлов.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(344, 16, 344, 64))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ПоместитьФайлы.*НачатьПомещениеФайлов.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(368, 12, 368, 59))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ЗапроситьРазрешениеПользователя.*НачатьЗапросРазрешенияПользователя.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(391, 4, 391, 38))
        && diagnostic.getMessage().matches(".*(синхронного|synchronous).*ЗапуститьПриложение.*НачатьЗапускПриложения.*"));
  }

  @Test
  void testUse() {
    var documentContext = getDocumentContextWithUseFlag(UseMode.USE);
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);
    assertThat(diagnostics).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {PATH_TO_OBJECT_MODULE_FILE, PATH_TO_MANAGER_MODULE_FILE})
  void testServerModules(String file) {
    var context = getDocumentContextWithUseFlag(UseMode.DONT_USE, file);

    List<Diagnostic> diagnostics = getDiagnostics(context);
    assertThat(diagnostics).isEmpty();
  }

  private DocumentContext getDocumentContextWithUseFlag(UseMode useMode) {
    return getDocumentContextWithUseFlag(useMode, PATH_TO_MODULE_FILE);
  }

  private DocumentContext getDocumentContextWithUseFlag(UseMode useMode, String moduleFile) {
    var path = Absolute.path(PATH_TO_METADATA);
    var testFile = Paths.get(moduleFile).toAbsolutePath();

    initServerContext(path);
    var serverContext = spy(context);
    var configuration = spy(serverContext.getConfiguration());
    when(((Configuration) configuration).getSynchronousExtensionAndAddInCallUseMode()).thenReturn(useMode);
    when(serverContext.getConfiguration()).thenReturn(configuration);

    var documentContext = spy(TestUtils.getDocumentContext(testFile.toUri(), getText(), serverContext));
    when(documentContext.getServerContext()).thenReturn(serverContext);

    return documentContext;
  }
}
