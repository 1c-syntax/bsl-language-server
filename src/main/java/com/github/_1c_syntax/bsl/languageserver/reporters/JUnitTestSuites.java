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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import lombok.Getter;
import lombok.Value;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.dataformat.xml.annotation.JacksonXmlCData;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@JsonRootName("testsuites")
class JUnitTestSuites {

  @Getter
  @JsonProperty("package")
  @JacksonXmlProperty(isAttribute = true)
  private final String name;

  @Getter
  @JacksonXmlElementWrapper(useWrapping = false)
  private final List<JUnitTestSuite> testsuite;

  public JUnitTestSuites(AnalysisInfo analysisInfo) {
    name = "bsl-language-server";

    testsuite = analysisInfo.fileinfos().stream()
      .filter(fileInfo -> !fileInfo.getDiagnostics().isEmpty())
      .map(JUnitTestSuite::new)
      .toList();
  }

  public JUnitTestSuites(
    @JsonProperty("package") String name,
    @JsonProperty("testsuite") List<JUnitTestSuite> testsuite
  ) {
    this.name = name;
    this.testsuite = new ArrayList<>(testsuite);
  }

  @Value
  static class JUnitTestSuite {

    @JacksonXmlProperty(isAttribute = true)
    String name;

    @JacksonXmlElementWrapper(useWrapping = false)
    List<JUnitTestCase> testcase;

    public JUnitTestSuite(FileInfo fileInfo) {
      this.name = fileInfo.getPath().toString();
      this.testcase = new ArrayList<>();

      List<Diagnostic> diagnostics = fileInfo.getDiagnostics();
      var groupedDiagnostics = diagnostics.stream()
        .collect(Collectors.groupingBy(
          Diagnostic::getCode,
          Collectors.toList())
        );

      groupedDiagnostics.forEach((code, diagnosticsList) ->
        testcase.add(new JUnitTestCase(diagnosticsList, DiagnosticCode.getStringValue(code), name))
      );
    }

    public JUnitTestSuite(
      @JsonProperty("name") String name,
      @JsonProperty("testcase") List<JUnitTestCase> testcase
    ) {
      this.name = name;
      this.testcase = new ArrayList<>(testcase);
    }
  }

  @Value
  static class JUnitTestCase {

    @JacksonXmlProperty(isAttribute = true)
    String name;

    @JacksonXmlProperty(isAttribute = true)
    String classname;

    @JacksonXmlElementWrapper(useWrapping = false)
    JUnitFailure failure;

    public JUnitTestCase(List<Diagnostic> diagnostics, String name, String classname) {
      this.name = name;
      this.classname = classname;
      List<String> value = new ArrayList<>();

      var type = "";
      var message = "";

      for (Diagnostic diagnostic : diagnostics) {
        type = diagnostic.getSeverity().toString().toLowerCase(Locale.ENGLISH);
        Position startRange = diagnostic.getRange().getStart();
        message = diagnostic.getMessage();
        value.add("line: %d, column: %d, text: %s".formatted(
          startRange.getLine() + 1,
          startRange.getCharacter(),
          diagnostic.getMessage()
        ));
      }

      this.failure = new JUnitFailure(type, message, String.join(System.lineSeparator(), value));
    }

    public JUnitTestCase(
      @JsonProperty("name") String name,
      @JsonProperty("classname") String classname,
      @JsonProperty("failure") JUnitFailure failure
    ) {
      this.name = name;
      this.classname = classname;
      this.failure = failure;
    }
  }

  @Value
  @JsonDeserialize(using = JUnitFailureDeserializer.class)
  static class JUnitFailure {

    @JacksonXmlProperty(isAttribute = true)
    String type;

    @JacksonXmlProperty(isAttribute = true)
    String message;

    @JacksonXmlText
    @JacksonXmlCData
    String value;
  }

  static class JUnitFailureDeserializer extends ValueDeserializer<JUnitFailure> {

    @Override
    public JUnitFailure deserialize(JsonParser jp, DeserializationContext ctxt) {
      JsonNode node = jp.objectReadContext().readTree(jp);

      var type = node.get("type").asString("");
      var message = node.get("message").asString("");
      var value = node.get("").asString("");

      return new JUnitFailure(type, message, value);
    }
  }
}
