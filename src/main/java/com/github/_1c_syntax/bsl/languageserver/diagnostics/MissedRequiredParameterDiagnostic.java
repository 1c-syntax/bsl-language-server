package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.HashMap;
import java.util.Map;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR
  }

)
@RequiredArgsConstructor
public class MissedRequiredParameterDiagnostic extends AbstractVisitorDiagnostic {

  private final ReferenceIndex referenceIndex;

  Map<Range, MethodCall> calls = new HashMap<>();

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    calls.clear();
    super.visitFile(ctx);

    for (var reference : referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method)) {
      if (calls.containsKey(reference.getSelectionRange())) {
        checkMethod((MethodSymbol) reference.getSymbol(), calls.get(reference.getSelectionRange()));
      }
    }

    calls.clear();
    return ctx;
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    String methodName = ctx.methodName().IDENTIFIER().getText();
    if (documentContext.getSymbolTree().getMethodSymbol(methodName).isPresent()) {
      appendMethodCall(ctx.methodName().getStart(), ctx.doCall(), ctx);
    }

    return super.visitGlobalMethodCall(ctx);
  }

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    appendMethodCall(ctx.methodName().getStart(), ctx.doCall(), ctx);
    return super.visitMethodCall(ctx);
  }

  void appendMethodCall(Token methodName, BSLParser.DoCallContext doCallContext, BSLParserRuleContext node) {
    var parameters = doCallContext.callParamList().callParam();
    MethodCall methodCall = new MethodCall();
    methodCall.parameters = new Boolean[parameters.size()];

    for (int i = 0; i < methodCall.parameters.length; i++) {
      methodCall.parameters[i] = parameters.get(i).expression() != null;
    }

    methodCall.range = Ranges.create(node);
    calls.put(Ranges.create(methodName), methodCall);
  }

  private void checkMethod(MethodSymbol methodDefinition, MethodCall callInfo) {
    int callParametersCount = callInfo.parameters.length;

    ParameterDefinition methodParameter;
    for (int i = 0; i < methodDefinition.getParameters().size(); i++) {

      methodParameter = methodDefinition.getParameters().get(i);
      if (methodParameter.isOptional()) {
        continue;
      }

      if (callParametersCount <= i || !callInfo.parameters[i]) {
        diagnosticStorage.addDiagnostic(callInfo.range, info.getMessage(methodParameter.getName()));
      }
    }
  }

  private static class MethodCall {
    Boolean[] parameters;
    Range range;
  }
}