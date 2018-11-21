package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.intellij.bsl.lsp.server.FileInfo;

import java.util.Collection;

public interface BSLDiagnostic {
  Collection<Diagnostic> getDiagnostics();
  void setFileInfo(FileInfo fileInfo);
}
