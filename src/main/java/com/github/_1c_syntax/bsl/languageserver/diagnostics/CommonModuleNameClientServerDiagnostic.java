package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.mdclasses.mdo.CommonModule;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.CommonModule
  },
  minutesToFix = 2,
  tags = {
    DiagnosticTag.STANDARD
  }

)
public class CommonModuleNameClientServerDiagnostic extends AbstractDiagnostic {

  private static final Pattern pattern = Pattern.compile(
    "(КлиентСервер$|ClientServer$)",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public CommonModuleNameClientServerDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check(DocumentContext documentContext) {

    if (documentContext.getTokens().isEmpty()) {
      return;
    }

    documentContext.getMdObject()
      .map(CommonModule.class::cast)
      .filter(CommonModule::isServer)
      .filter(CommonModule::isClientManagedApplication)
      .filter(commonModule -> !pattern.matcher(commonModule.getName()).matches())
      .ifPresent(commonModule -> diagnosticStorage.addDiagnostic(documentContext.getTokens().get(0)));

  }

}
