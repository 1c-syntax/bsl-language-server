package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.mdclasses.mdo.MDObjectBase;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }

)
public class ObjectNameLengthDiagnostic extends AbstractDiagnostic {

  private static final int MAX_OBJECT_NAME_LENGTH = 80;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_OBJECT_NAME_LENGTH
  )
  private int maxObjectNameLength = MAX_OBJECT_NAME_LENGTH;

  public ObjectNameLengthDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check() {
    documentContext
      .getMdObject()
      .map(MDObjectBase::getName)
      .filter(this::checkName)
      .ifPresent(objectName -> diagnosticStorage.addDiagnostic(
        documentContext.getTokensFromDefaultChannel().get(0),
        info.getMessage(maxObjectNameLength)
      ));
  }

  private boolean checkName(String name) {
    return name.length() > maxObjectNameLength;
  }
}
