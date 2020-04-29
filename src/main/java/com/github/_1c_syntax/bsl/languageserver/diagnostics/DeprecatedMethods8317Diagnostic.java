package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_17,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.DEPRECATED
  }

)
public class DeprecatedMethods8317Diagnostic extends AbstractFindMethodDiagnostic {

  private static final Pattern DEPRECATED_METHODS_NAMES = Pattern.compile(
    "КраткоеПредставлениеОшибки|BriefErrorDescription" +
      "ПодробноеПредставлениеОшибки|DetailErrorDescription" +
      "ПоказатьИнформациюОбОшибке|ShowErrorInfo",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public DeprecatedMethods8317Diagnostic(DiagnosticInfo info) {
    super(info, DEPRECATED_METHODS_NAMES);
  }

}
