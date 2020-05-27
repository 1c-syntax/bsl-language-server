package com.github._1c_syntax.bsl.languageserver.diagnostics;

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
public class exportVariablesDiagnostic extends AbstractVisitorDiagnostic {
  public exportVariablesDiagnostic(DiagnosticInfo info) {
    super(info);
  }

}
