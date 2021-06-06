package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import lombok.val;

import java.util.Set;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.VULNERABILITY,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.DESIGN
  },
  modules = {
    ModuleType.ManagedApplicationModule
  }
)
public class SetPermissionsForNewObjectsDiagnostic extends AbstractDiagnostic {

  @Override
  public void check() {

    val configuration = documentContext.getServerContext().getConfiguration();

    final Set<MDObjectBase> rolesWithSetForNewObjects = configuration.getRoles();

    for (role : rolesWithSetForNewObjects)
    {
      diagnosticStorage.addDiagnostic();
    }

  }

}
