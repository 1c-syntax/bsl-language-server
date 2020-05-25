package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }

)
public class ObjectNameLengthDiagnostic extends AbstractVisitorDiagnostic {

  private static final int MAX_OBJECT_NAME_LENGTH = 7;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_OBJECT_NAME_LENGTH
  )
  private int maxParamsCount = MAX_OBJECT_NAME_LENGTH;

  public ObjectNameLengthDiagnostic(DiagnosticInfo info) {
    super(info);
  }

}
