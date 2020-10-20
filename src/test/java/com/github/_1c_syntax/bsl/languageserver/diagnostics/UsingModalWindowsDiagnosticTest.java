/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.metadata.additional.UseMode;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class UsingModalWindowsDiagnosticTest extends AbstractDiagnosticTest<UsingModalWindowsDiagnostic> {
  @SpyBean
  private ServerContext context;

  UsingModalWindowsDiagnosticTest() {
    super(UsingModalWindowsDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_MODULE_FILE = "src/test/resources/metadata/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";

  @Test
  void testDontUse() {

    var documentContext = getDocumentContextWithUseFlag(UseMode.DONT_USE);
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);
    assertDiagnosticList(diagnostics);

  }

  @Test
  void testUse() {

    DocumentContext documentContext = getDocumentContextWithUseFlag(UseMode.USE);
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testUseWithForce() {

    DocumentContext documentContext = getDocumentContextWithUseFlag(UseMode.USE);

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("forceModalityMode", true);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);
    assertDiagnosticList(diagnostics);

  }

  private void assertDiagnosticList(List<Diagnostic> diagnostics) {

    assertThat(diagnostics).hasSize(12)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(2, 12, 3, 57))
        && diagnostic.getMessage().matches(".*(модального|modal).*Вопрос.*ПоказатьВопрос.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(21, 4, 21, 84))
        && diagnostic.getMessage().matches(".*(модального|modal).*Предупреждение.*ПоказатьПредупреждение.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(29, 4, 29, 26))
        && diagnostic.getMessage().matches(".*(модального|modal).*ОткрытьЗначение.*ПоказатьЗначение.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(43, 9, 43, 58))
        && diagnostic.getMessage().matches(".*(модального|modal).*ВвестиДату.*ПоказатьВводДаты.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(72, 9, 72, 67))
        && diagnostic.getMessage().matches(".*(модального|modal).*ВвестиЗначение.*ПоказатьВводЗначения.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(103, 9, 103, 50))
        && diagnostic.getMessage().matches(".*(модального|modal).*ВвестиСтроку.*ПоказатьВводСтроки.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(122, 9, 122, 61))
        && diagnostic.getMessage().matches(".*(модального|modal).*ВвестиЧисло.*ПоказатьВводЧисла.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(138, 4, 138, 50))
        && diagnostic.getMessage().matches(".*(модального|modal).*УстановитьВнешнююКомпоненту.*НачатьУстановкуВнешнейКомпоненты.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(148, 4, 148, 33))
        && diagnostic.getMessage().matches(".*(модального|modal).*ОткрытьФормуМодально.*ОткрытьФорму.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(159, 20, 159, 56))
        && diagnostic.getMessage().matches(".*(модального|modal).*УстановитьРасширениеРаботыСФайлами.*НачатьУстановкуРасширенияРаботыСФайлами.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(172, 20, 172, 62))
        && diagnostic.getMessage().matches(".*(модального|modal).*УстановитьРасширениеРаботыСКриптографией.*НачатьУстановкуРасширенияРаботыСКриптографией.*"))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(186, 4, 186, 88))
        && diagnostic.getMessage().matches(".*(модального|modal).*ПоместитьФайл.*НачатьПомещениеФайла.*"));

  }

  private DocumentContext getDocumentContextWithUseFlag(UseMode useMode) {
    var path = Absolute.path(PATH_TO_METADATA);
    var testFile = Paths.get(PATH_TO_MODULE_FILE).toAbsolutePath();

    initServerContext(path);
    var configuration = spy(context.getConfiguration());
    when(configuration.getModalityUseMode()).thenReturn(useMode);
    when(context.getConfiguration()).thenReturn(configuration);

    return context.addDocument(testFile.toUri(), getText());
  }

}

