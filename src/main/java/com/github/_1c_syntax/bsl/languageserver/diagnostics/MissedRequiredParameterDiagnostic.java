package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR
  }

)
public class MissedRequiredParameterDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    String methodName = ctx.methodName().IDENTIFIER().getText();
    var method = documentContext.getSymbolTree().getMethodSymbol(methodName);
    if (method.isEmpty()) {
      return ctx;
    }

    var callParameters = ctx.doCall().callParamList().callParam();
    var methodParameters = method.get().getParameters();
    int callParametersCount = callParameters.size();
    int methodParametersCount = methodParameters.size();

    for (int i = 0; i < methodParametersCount; i++) {
      var methodParameter = methodParameters.get(i);
      if (methodParameter.isOptional()) {
        continue;
      }

      if (callParametersCount <= i || callParameters.get(i).expression() == null) {
        diagnosticStorage.addDiagnostic(ctx, info.getMessage(methodParameter.getName()));
      }
    }

    return ctx;
  }
}
