package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.github._1c_syntax.intellij.bsl.lsp.server.FileInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DiagnosticProvider {

  public static final String SOURCE = "bsl-language-server";

  private static List<BSLDiagnostic> diagnosticClasses = Arrays.asList(
    new FunctionShouldHaveReturnDiagnostic(),
    new LineLengthDiagnostic()
  );

  public static void computeAndPublishDiagnostics(LanguageClient client, String uri, FileInfo fileInfo) {

    List<Diagnostic> diagnostics = new ArrayList<>();
    diagnosticClasses.parallelStream()
      .flatMap(diagnostic -> {
        diagnostic.setFileInfo(fileInfo);
        return diagnostic.getDiagnostics().stream();
      }).forEach(
      diagnostics::add
    );

    client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
  }
}
