package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR
  }

)
public class MetadataBordersDiagnostic extends AbstractVisitorDiagnostic {

}
