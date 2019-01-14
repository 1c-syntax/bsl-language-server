package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.FileInfo;
import org.github._1c_syntax.intellij.bsl.lsp.server.utils.RangeHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JsonReporterTest {

  private File file = new File("./bsl-json.json");

  @BeforeEach
  void setUp() {
    FileUtils.deleteQuietly(file);
  }

  @AfterEach
  void tearDown() {
    FileUtils.deleteQuietly(file);
  }

  @Test
  void report() throws IOException {

    // given
    Diagnostic diagnostic = new Diagnostic(
      RangeHelper.newRange(0, 1, 2, 3),
      "message",
      DiagnosticSeverity.Error,
      "test-source",
      "test"
    );

    FileInfo fileInfo = new FileInfo(new File("").toPath(), Collections.singletonList(diagnostic));
    AnalysisInfo analysisInfo = new AnalysisInfo(LocalDateTime.now(), Collections.singletonList(fileInfo));

    JsonReporter reporter = new JsonReporter();

    // when
    reporter.report(analysisInfo);

    // then
    ObjectMapper mapper = new ObjectMapper();
    mapper.findAndRegisterModules();
    AnalysisInfo report = mapper.readValue(file, AnalysisInfo.class);

    assertThat(report.getFileinfos()).hasSize(1);

  }
}