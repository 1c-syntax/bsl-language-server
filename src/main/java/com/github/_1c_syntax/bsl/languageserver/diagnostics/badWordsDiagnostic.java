package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.*;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tag = {
    DiagnosticTag.DESIGN
  }
)
public class badWordsDiagnostic extends AbstractVisitorDiagnostic {
}
