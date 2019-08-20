package org.github._1c_syntax.bsl.languageserver.codeactions;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractQuickFixSupplier implements CodeActionSupplier {

  protected DiagnosticProvider diagnosticProvider;

  public AbstractQuickFixSupplier(DiagnosticProvider diagnosticProvider) {
    this.diagnosticProvider = diagnosticProvider;
  }

  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {
    List<String> only = params.getContext().getOnly();
    if (only != null && !only.isEmpty() && !only.contains(CodeActionKind.QuickFix)) {
      return Collections.emptyList();
    }

    List<Diagnostic> incomingDiagnostics = params.getContext().getDiagnostics();
    if (incomingDiagnostics.isEmpty()) {
      return Collections.emptyList();
    }

    Set<Diagnostic> computedDiagnostics = diagnosticProvider.getComputedDiagnostics(documentContext);

    Stream<Diagnostic> diagnosticStream = incomingDiagnostics.stream()
      .filter(computedDiagnostics::contains);

    return processDiagnosticStream(diagnosticStream, params, documentContext)
      .collect(Collectors.toList());

  }

  protected abstract Stream<CodeAction> processDiagnosticStream(
    Stream<Diagnostic> diagnosticStream,
    CodeActionParams params,
    DocumentContext documentContext
  );
}
