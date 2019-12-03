package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import org.eclipse.lsp4j.Diagnostic;

import java.util.List;

public abstract class AbstractDiagnostic implements BSLDiagnostic {

  protected final DiagnosticInfo info;
  protected DiagnosticStorage diagnosticStorage = new DiagnosticStorage(this);

  public AbstractDiagnostic(DiagnosticInfo info) {
    this.info = info;
  }

  @Override
  public DiagnosticInfo getInfo() {
    return info;
  }

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    diagnosticStorage.clearDiagnostics();
    check(documentContext);
    return diagnosticStorage.getDiagnostics();
  }

  protected abstract void check(DocumentContext documentContext);
}
