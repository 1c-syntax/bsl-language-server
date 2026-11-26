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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.ser.ToXmlGenerator;

import javax.xml.namespace.QName;
import java.nio.file.Path;

@Slf4j
@Component
public class JUnitReporter implements DiagnosticReporter {

  private static final String SUITE_ELEMENT = "testsuite";

  private ReportFile reportFile;
  private ToXmlGenerator generator;

  @Override
  public String key() {
    return "junit";
  }

  @Override
  public void beginReport(ReportContext context, Path outputDir) {
    var mapper = XmlMapper.builder()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .build();

    reportFile = ReportFile.create(outputDir, "bsl-junit.xml");
    // XmlMapper.writeValue сам раскрывает @JsonRootName; при потоковой записи корневой элемент
    // и его атрибут задаются вручную
    generator = (ToXmlGenerator) mapper.createGenerator(reportFile.stream());
    generator.initGenerator();
    generator.setNextName(new QName("testsuites"));
    generator.writeStartObject();
    generator.setNextIsAttribute(true);
    generator.writeName("package");
    generator.writeString("bsl-language-server");
    generator.setNextIsAttribute(false);
  }

  @Override
  public void accept(FileInfo fileInfo) {
    if (fileInfo.getDiagnostics().isEmpty()) {
      return;
    }

    var testSuite = new JUnitTestSuites.JUnitTestSuite(fileInfo);
    generator.writeName(SUITE_ELEMENT);
    generator.writePOJO(testSuite);
  }

  @Override
  public void endReport() {
    generator.writeEndObject();
    generator.close();
    reportFile.commit();
    LOGGER.info("JUnit report saved to {}", reportFile.path().toAbsolutePath());
  }

  @Override
  public void abortReport() {
    if (reportFile != null) {
      reportFile.close();
    }
  }
}
