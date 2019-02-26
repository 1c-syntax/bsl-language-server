/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.diagnostics.reporter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlCData;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import lombok.Data;
import lombok.Getter;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.FileInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@JacksonXmlRootElement(localName = "testsuites")
class JUnitTestSuites {

  @Getter
  @JsonProperty("package")
  @JacksonXmlProperty(isAttribute = true)
  private final String packageName;

  @Getter
  @JacksonXmlElementWrapper(useWrapping = false)
  private final List<JUnitTestSuite> testsuite;

  public JUnitTestSuites(AnalysisInfo analysisInfo) {
    packageName = "bsl-language-server";

    testsuite = analysisInfo.getFileinfos().stream()
      .filter(fileInfo -> !fileInfo.getDiagnostics().isEmpty())
      .map(JUnitTestSuite::new)
      .collect(Collectors.toList());
  }

  public JUnitTestSuites(
    @JsonProperty("package") String packageName,
    @JsonProperty("testsuite") List<JUnitTestSuite> testsuite
  ) {
    this.packageName = packageName;
    this.testsuite = new ArrayList<>(testsuite);
  }

  @Data
  static class JUnitTestSuite {

    @JacksonXmlProperty(isAttribute = true)
    private final String name;

    @JacksonXmlElementWrapper(useWrapping = false)
    private final List<JUnitTestCase> testcase;

    public JUnitTestSuite(FileInfo fileInfo) {
      this.name = fileInfo.getPath().toString();
      this.testcase = new ArrayList<>();

      List<Diagnostic> diagnostics = fileInfo.getDiagnostics();
      Map<String, List<Diagnostic>> groupedDiagnostics = diagnostics.stream()
        .collect(Collectors.groupingBy(Diagnostic::getCode, Collectors.toList()));

      groupedDiagnostics.forEach((code, diagnosticsList) ->
        testcase.add(new JUnitTestCase(diagnosticsList, code, name))
      );
    }

    public JUnitTestSuite(
      @JsonProperty("name") String  name,
      @JsonProperty("testcase") List<JUnitTestCase> testcase
    ) {
      this.name = name;
      this.testcase = new ArrayList<>(testcase);
    }
  }

  @Data
  static class JUnitTestCase {

    @JacksonXmlProperty(isAttribute = true)
    private final String name;

    @JacksonXmlProperty(isAttribute = true)
    private final String classname;

    @JacksonXmlElementWrapper(useWrapping = false)
    private final List<JUnitFailure> failure;

    public JUnitTestCase(List<Diagnostic> diagnostics, String name, String classname) {
      this.name = name;
      this.classname = classname;
      this.failure = new ArrayList<>();

      diagnostics.forEach((Diagnostic diagnostic) -> {
        String type = diagnostic.getSeverity().toString().toLowerCase(Locale.ENGLISH);
        String message = diagnostic.getMessage();
        String value = String.format(
          "line: %d, column: %d, text: %s",
          diagnostic.getRange().getStart().getLine() + 1,
          diagnostic.getRange().getStart().getCharacter(),
          diagnostic.getMessage()
        );

        this.failure.add(new JUnitFailure(type, message, value));
      });
    }

    public JUnitTestCase(
      @JsonProperty("name") String  name,
      @JsonProperty("classname") String classname,
      @JsonProperty("failure") List<JUnitFailure> failure
    ) {
      this.name = name;
      this.classname = classname;
      this.failure = new ArrayList<>(failure);
    }
  }

  @Data
  @JsonDeserialize(using = JUnitFailureDeserializer.class)
  static class JUnitFailure {

    @JacksonXmlProperty(isAttribute = true)
    private final String type;

    @JacksonXmlProperty(isAttribute = true)
    private final String message;

    @JacksonXmlText
    @JacksonXmlCData
    private final String value;
  }

  static class JUnitFailureDeserializer extends JsonDeserializer<JUnitFailure> {

    @Override
    public JUnitFailure deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      JsonNode node = jp.getCodec().readTree(jp);

      String type = node.get("type").asText("");
      String message = node.get("message").asText("");
      String value = node.get("").asText("");

      return new JUnitFailure(type, message, value);
    }
  }

}
