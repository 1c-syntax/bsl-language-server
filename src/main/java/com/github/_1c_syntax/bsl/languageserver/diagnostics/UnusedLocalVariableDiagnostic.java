package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
		DiagnosticTag.BRAINOVERLOAD,
		DiagnosticTag.BADPRACTICE
	},
  modules = {
    ModuleType.CommandModule,
    ModuleType.CommonModule,
    ModuleType.ManagerModule,
    ModuleType.ValueManagerModule,
    ModuleType.SessionModule,
  }
)
public class UnusedLocalVariableDiagnostic extends AbstractVisitorDiagnostic {

  private List<String> moduleVariables = new ArrayList<>();
  private List<String> subParam = new ArrayList<>();
  private List<Token> currentSubTokens;

  public UnusedLocalVariableDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitModuleVarDeclaration(BSLParser.ModuleVarDeclarationContext ctx) {

    List<Token> moduleVarTokens = ctx.getTokens();

    if (moduleVarTokens.size() == 0) {
      return ctx;
    }

    moduleVariables.add(moduleVarTokens.get(0).getText());
    return ctx;

  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {

    updateCurrentSubTokens(ctx);
    updateSubParam(ctx);
    return super.visitSub(ctx);

  }

  private void updateSubParam(BSLParser.SubContext ctx) {

    subParam.clear();
    BSLParser.FunctionContext func = ctx.function();

    if (func != null) {
      BSLParser.ParamListContext paramList = func.funcDeclaration().paramList();

      if (paramList != null) {
        for (BSLParser.ParamContext param : paramList.param()) {
          subParam.add(param.getText());
        }
      }
    }

    BSLParser.ProcedureContext proc = ctx.procedure();

    if (proc != null) {
      BSLParser.ParamListContext paramList = proc.procDeclaration().paramList();

      if (paramList != null) {
        for (BSLParser.ParamContext param : paramList.param()) {
          subParam.add(param.getText());
        }
      }
    }

  }

  private void updateCurrentSubTokens(BSLParser.SubContext ctx) {

    currentSubTokens = documentContext
      .getTokens()
      .subList(ctx.start.getTokenIndex(), ctx.stop.getTokenIndex());

  }

  @Override
  public ParseTree visitSubVarDeclaration(BSLParser.SubVarDeclarationContext ctx) {

    if (!usingBeforeInSub(ctx.getText(), ctx.stop.getTokenIndex())) {
      diagnosticStorage.addDiagnostic(ctx);
    }

    return ctx;

  }

  @Override
  public ParseTree visitLValue(BSLParser.LValueContext ctx) {

    if (ctx.getChildCount() != 1) {
      return ctx;
    }

    String variableName = ctx.getText();

    if (moduleVariables.contains(variableName) || subParam.contains(variableName)) {
      return ctx;
    }

    if (usingBeforeInSub(variableName, ctx.getParent().stop.getTokenIndex())) {
      return ctx;
    }

    diagnosticStorage.addDiagnostic(ctx);
    return ctx;

  }

  private boolean usingBeforeInSub(String variableName, int startOfSearch) {

    return currentSubTokens
      .stream()
      .filter(t -> t.getTokenIndex() > startOfSearch)
      .map(Token::getText)
      .anyMatch(n -> n.equals(variableName));

  }

}
