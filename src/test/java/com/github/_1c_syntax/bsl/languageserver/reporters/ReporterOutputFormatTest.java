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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.MetricStorage;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticInfos;
import com.github._1c_syntax.bsl.languageserver.diagnostics.info.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Побайтовая фиксация формата файлов отчётов.
 * <p>
 * Эти тесты — страховка на время перевода репортёров на потоковую запись: содержимое файлов
 * меняться не должно. Утверждения не зависят от способа вызова репортёра — вся связь с API
 * сосредоточена в {@link #runReport}, поэтому при смене контракта правится только он.
 * <p>
 * Переводы строк нормализуются: часть форматов использует {@code System.lineSeparator()},
 * а CI гоняет тесты на трёх ОС.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ReporterOutputFormatTest extends AbstractServerContextAwareTest {

  private static final String SOURCE_DIR = "/src";
  private static final LocalDateTime DATE = LocalDateTime.of(2026, 8, 7, 13, 45, 9);
  private static final Path FIRST_PATH = Path.of("src", "Модуль1.bsl");
  private static final Path SECOND_PATH = Path.of("src", "Модуль2.bsl");

  /** Путь как он выглядит внутри строки JSON: на Windows разделитель экранируется. */
  private static final String FIRST_PATH_IN_JSON = FIRST_PATH.toString().replace("\\", "\\\\");

  /** Путь с разделителем, нормализованным в {@code /} — так его пишет code-quality. */
  private static final String FIRST_PATH_SLASHED = FIRST_PATH.toString().replace('\\', '/');

  @TempDir
  Path outputDir;

  @Autowired
  private CodeQualityReporter codeQualityReporter;

  @Autowired
  private GenericIssueReporter genericIssueReporter;

  @Autowired
  private DiagnosticInfos diagnosticInfos;

  @BeforeEach
  void setUp() {
    initServerContext();
  }

  /**
   * Единственное место, знающее, как запускается репортёр. При смене контракта правится только оно.
   */
  private void runReport(DiagnosticReporter reporter) {
    var analysisInfo = analysisInfo();

    reporter.beginReport(new ReportContext(analysisInfo.date(), analysisInfo.sourceDir()), outputDir);
    analysisInfo.fileinfos().forEach(reporter::accept);
    reporter.endReport();
  }

  private String report(DiagnosticReporter reporter, String fileName) throws Exception {
    runReport(reporter);
    return Files.readString(outputDir.resolve(fileName), StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  private static AnalysisInfo analysisInfo() {
    var first = new FileInfo(
      FIRST_PATH,
      "CommonModule.Модуль1",
      List.of(
        new Diagnostic(Ranges.create(0, 1, 2, 3), "сообщение <&>",
          DiagnosticSeverity.Error, "bsl-language-server", "Typo"),
        new Diagnostic(Ranges.create(4, 0, 4, 10), "второе",
          DiagnosticSeverity.Warning, "bsl-language-server", "Typo")
      ),
      new MetricStorage(1, 2, 30, 25, 4, 12, new int[]{1, 2}, new int[]{}, 3, 5)
    );
    // файл без замечаний — краевой случай: junit такие пропускает, остальные не порождают записей
    var second = new FileInfo(SECOND_PATH, "CommonModule.Модуль2", List.of(), new MetricStorage());
    return new AnalysisInfo(DATE, List.of(first, second), SOURCE_DIR);
  }

  private DiagnosticInfo typo() {
    return diagnosticInfos.getByCode().get("Typo");
  }

  @Test
  void json() throws Exception {
    var expected = ("{\"date\":\"2026-08-07 13:45:09\",\"fileinfos\":["
      + "{\"path\":\"" + FIRST_PATH.toUri() + "\",\"mdoRef\":\"CommonModule.Модуль1\",\"diagnostics\":["
      + "{\"code\":\"Typo\",\"codeDescription\":null,\"data\":null,\"message\":\"сообщение <&>\","
      + "\"range\":{\"end\":{\"character\":3,\"line\":2},\"start\":{\"character\":1,\"line\":0}},"
      + "\"relatedInformation\":null,\"severity\":\"Error\",\"source\":\"bsl-language-server\",\"tags\":null},"
      + "{\"code\":\"Typo\",\"codeDescription\":null,\"data\":null,\"message\":\"второе\","
      + "\"range\":{\"end\":{\"character\":10,\"line\":4},\"start\":{\"character\":0,\"line\":4}},"
      + "\"relatedInformation\":null,\"severity\":\"Warning\",\"source\":\"bsl-language-server\",\"tags\":null}],"
      + "\"metrics\":{\"procedures\":1,\"functions\":2,\"lines\":30,\"ncloc\":25,\"comments\":4,"
      + "\"statements\":12,\"nclocData\":[1,2],\"covlocData\":[],\"cognitiveComplexity\":3,"
      + "\"cyclomaticComplexity\":5}},"
      + "{\"path\":\"" + SECOND_PATH.toUri() + "\",\"mdoRef\":\"CommonModule.Модуль2\",\"diagnostics\":[],"
      + "\"metrics\":{\"procedures\":0,\"functions\":0,\"lines\":0,\"ncloc\":0,\"comments\":0,"
      + "\"statements\":0,\"nclocData\":null,\"covlocData\":null,\"cognitiveComplexity\":0,"
      + "\"cyclomaticComplexity\":0}}],\"sourceDir\":\"/src\"}");

    assertThat(report(new JsonReporter(), "bsl-json.json")).isEqualTo(expected);
  }

  @Test
  void tslint() throws Exception {
    var expected = """
      [ {
        "startPosition" : {
          "character" : 1,
          "line" : 0,
          "position" : 1
        },
        "endPosition" : {
          "character" : 3,
          "line" : 2,
          "position" : 3
        },
        "failure" : "сообщение <&>",
        "name" : "%s",
        "ruleName" : "Typo",
        "rileSeverity" : "error"
      }, {
        "startPosition" : {
          "character" : 0,
          "line" : 4,
          "position" : 0
        },
        "endPosition" : {
          "character" : 10,
          "line" : 4,
          "position" : 10
        },
        "failure" : "второе",
        "name" : "%s",
        "ruleName" : "Typo",
        "rileSeverity" : "warn"
      } ]""".formatted(FIRST_PATH_IN_JSON, FIRST_PATH_IN_JSON);

    assertThat(report(new TSLintReporter(), "bsl-tslint.json")).isEqualTo(expected);
  }

  @Test
  void junit() throws Exception {
    // значение failure склеивается через System.lineSeparator(), поэтому подставляем его явно
    var failureValue = String.join(System.lineSeparator(),
      "line: 1, column: 1, text: сообщение <&>",
      "line: 5, column: 0, text: второе").replace("\r\n", "\n");

    var expected = """
      <testsuites package="bsl-language-server">
        <testsuite name="%s">
          <testcase name="Typo" classname="%s">
            <failure type="warning" message="второе"><![CDATA[%s]]></failure>
          </testcase>
        </testsuite>
      </testsuites>
      """.formatted(FIRST_PATH, FIRST_PATH, failureValue);

    assertThat(report(new JUnitReporter(), "bsl-junit.xml")).isEqualTo(expected);
  }

  @Test
  void codeQuality() throws Exception {
    var severity = CodeQualityReportEntry.Severity.valueOf(typo().getSeverity().name()).name()
      .toLowerCase(java.util.Locale.ENGLISH);

    var expected = """
      [ {
        "description" : "сообщение <&>",
        "check_name" : "Typo",
        "fingerprint" : "39f7a76d15b5c321817533942f0db8c0729815d86e2d768805fd651c6f5776be",
        "severity" : "%s",
        "location" : {
          "path" : "%s",
          "lines" : {
            "begin" : 1
          }
        }
      }, {
        "description" : "второе",
        "check_name" : "Typo",
        "fingerprint" : "80e5b890cf81a230e085a3b11ea48b0b6955e74fd7ac61d886dc3fea06adedcc",
        "severity" : "%s",
        "location" : {
          "path" : "%s",
          "lines" : {
            "begin" : 5
          }
        }
      } ]""".formatted(severity, FIRST_PATH_SLASHED, severity, FIRST_PATH_SLASHED);

    assertThat(report(codeQualityReporter, "bsl-code-quality.json")).isEqualTo(expected);
  }

  @Test
  void generic() throws Exception {
    var info = typo();
    var severity = info.getSeverity().name();
    var type = "CODE_SMELL";
    var effort = info.getMinutesToFix();

    var expected = """
      {
        "issues" : [ {
          "engineId" : "bsl-language-server",
          "ruleId" : "Typo",
          "severity" : "%s",
          "type" : "%s",
          "primaryLocation" : {
            "message" : "сообщение <&>",
            "filePath" : "%s",
            "textRange" : {
              "startLine" : 1,
              "endLine" : 3,
              "startColumn" : 1,
              "endColumn" : 3
            }
          },
          "effortMinutes" : %d,
          "secondaryLocations" : [ ]
        }, {
          "engineId" : "bsl-language-server",
          "ruleId" : "Typo",
          "severity" : "%s",
          "type" : "%s",
          "primaryLocation" : {
            "message" : "второе",
            "filePath" : "%s",
            "textRange" : {
              "startLine" : 5,
              "endLine" : 5,
              "startColumn" : 0,
              "endColumn" : 10
            }
          },
          "effortMinutes" : %d,
          "secondaryLocations" : [ ]
        } ]
      }""".formatted(severity, type, FIRST_PATH_IN_JSON, effort, severity, type, FIRST_PATH_IN_JSON, effort);

    assertThat(report(genericIssueReporter, "bsl-generic-json.json")).isEqualTo(expected);
  }
}
