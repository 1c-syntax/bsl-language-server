package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  scope = DiagnosticScope.ALL,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.DESIGN,
    DiagnosticTag.UNPREDICTABLE
  }
)
public class ExportVariablesDiagnostic extends AbstractSymbolTreeDiagnostic {
  public ExportVariablesDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public void visitVariable(VariableSymbol variable) {
    if (variable.isExport()) {
      diagnosticStorage.addDiagnostic(variable.getRange());
    }
  }

  @Override
  public void visitMethod(MethodSymbol method) {
    // skip content of methods
  }
}
