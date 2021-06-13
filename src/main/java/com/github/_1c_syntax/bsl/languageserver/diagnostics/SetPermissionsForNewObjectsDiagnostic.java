package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import lombok.val;

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

  private static final String NAME_FULL_ACCESS_ROLE_RU = "ПолныеПрава";
  private static final String NAME_FULL_ACCESS_ROLE_EN = "FullAccess";

  @Override
  public void check() {

    val configuration = documentContext.getServerContext().getConfiguration();

    val roles = configuration.getRoles();

    for (var role : roles)
    {
      var nameRole = role.getName();

      if (!nameRole.equals(NAME_FULL_ACCESS_ROLE_RU)
        && !nameRole.equals(NAME_FULL_ACCESS_ROLE_EN)
        && role.getRoleData().isSetForNewObjects())
      {

        var range = Ranges.getFirstSignificantTokenRange(documentContext.getTokens());
        if (range.isEmpty()) {
          return;
        }
        diagnosticStorage.addDiagnostic(range.get());
      }
    }

  }

}
