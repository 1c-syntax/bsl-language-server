package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.mdclasses.mdo.CommonModule;

import java.util.regex.Pattern;

abstract class AbstractCommonModuleNameDiagnostic extends AbstractDiagnostic {

  Pattern pattern;

  public AbstractCommonModuleNameDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check(DocumentContext documentContext) {
    if (documentContext.getTokens().isEmpty()) {
      return;
    }

    documentContext.getMdObject()
      .map(CommonModule.class::cast)
      .filter(this::flagsCheck)
      .filter(commonModule -> !pattern.matcher(commonModule.getName()).matches())
      .ifPresent(commonModule -> diagnosticStorage.addDiagnostic(documentContext.getTokens().get(0)));
  }

  protected abstract boolean flagsCheck(CommonModule commonModule);

}
