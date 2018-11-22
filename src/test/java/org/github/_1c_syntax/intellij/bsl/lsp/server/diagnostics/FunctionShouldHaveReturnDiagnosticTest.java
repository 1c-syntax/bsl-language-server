package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.intellij.bsl.lsp.server.FileInfo;
import org.github._1c_syntax.intellij.bsl.lsp.server.utils.RangeHelper;
import org.github._1c_syntax.parser.BSLLexer;
import org.github._1c_syntax.parser.BSLParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

class FunctionShouldHaveReturnDiagnosticTest {

  private BSLDiagnostic diagnostic = new FunctionShouldHaveReturnDiagnostic();

  @Test
  void getDiagnostics() throws IOException {

    String textDocumentContent = IOUtils.resourceToString(
      "diagnostics/FunctionShouldHaveReturnDiagnostic.bsl",
      Charset.forName("UTF-8"),
      this.getClass().getClassLoader()
    );
    BSLLexer lexer = new BSLLexer(CharStreams.fromString(textDocumentContent));

    CommonTokenStream tokens = new CommonTokenStream(lexer);

    BSLParser parser = new BSLParser(tokens);

    FileInfo fileInfo = new FileInfo(parser.file(), tokens.getTokens());

    diagnostic.setFileInfo(fileInfo);

    List<Diagnostic> diagnostics = diagnostic.getDiagnostics();

    assertThat(diagnostics, hasSize(1));
    assertThat(diagnostics.get(0).getRange(), equalTo(RangeHelper.newRange(0, 8, 0, 26)));

  }
}