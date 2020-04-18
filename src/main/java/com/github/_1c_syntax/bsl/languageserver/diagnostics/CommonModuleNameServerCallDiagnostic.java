package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.mdclasses.mdo.CommonModule;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }

)
public class CommonModuleNameServerCallDiagnostic extends AbstractDiagnostic {
  public CommonModuleNameServerCallDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  private static final Pattern pattern = Pattern.compile(
    "^.*вызовсервера|^.*servercall",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  @Override
  protected void check(DocumentContext documentContext) {

    if (documentContext.getTokens().isEmpty()) {
      return;
    }

    documentContext.getMdObject()
      .map(CommonModule.class::cast)
      .filter(CommonModule::isServer)
      .filter(CommonModule::isServerCall)
      .filter(commonModule -> !pattern.matcher(commonModule.getName()).matches())
      .ifPresent(commonModule -> diagnosticStorage.addDiagnostic(documentContext.getTokens().get(0)));

  }

}
