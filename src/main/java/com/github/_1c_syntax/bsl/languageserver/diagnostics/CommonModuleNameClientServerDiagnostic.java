package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.mdo.CommonModule;
import com.github._1c_syntax.mdclasses.mdo.MDObjectBase;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Optional;

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
public class CommonModuleNameClientServerDiagnostic extends AbstractVisitorDiagnostic {
  public CommonModuleNameClientServerDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {

    Optional<MDObjectBase> commonModuleOptional = documentContext.getMdObject();

    if (commonModuleOptional.isEmpty()
      || !(commonModuleOptional.get() instanceof CommonModule)) {
      return super.visitFile(ctx);
    }

    CommonModule commonModule = (CommonModule) commonModuleOptional.get();

    if (commonModule.isServer()
      && commonModule.isClientManagedApplication()
      && !commonModule.getName().endsWith("КлиентСервер")) {
      diagnosticStorage.addDiagnostic(ctx.getStart());
    }

    return super.visitFile(ctx);
  }

}
