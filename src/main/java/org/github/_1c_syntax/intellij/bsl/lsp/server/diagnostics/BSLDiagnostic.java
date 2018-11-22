package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.intellij.bsl.lsp.server.FileInfo;

import java.util.List;

public interface BSLDiagnostic {
  List<Diagnostic> getDiagnostics();
  void setFileInfo(FileInfo fileInfo);
}
