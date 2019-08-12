package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;

import java.util.List;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 5
)
public class CommentedCodeDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {

    return documentContext
      .getComments()
      .parallelStream()
      .filter(CommentedCodeDiagnostic::isCommentedCode)
      .map(this::TokenToDiagnostic)
      .collect((Collectors.toList()));
  }

  private static Boolean isCommentedCode(Token token) {
    String textWithoutSlashes = token.getText().replaceFirst("//", "");
    DocumentContext tokenContext = new DocumentContext("http://fake.uri.bsl", textWithoutSlashes);
    return true;
  }

  private Diagnostic TokenToDiagnostic(Token token) {
    return BSLDiagnostic.createDiagnostic(
      this,
      RangeHelper.newRange(token),
      getDiagnosticMessage()
    );
  }
}
