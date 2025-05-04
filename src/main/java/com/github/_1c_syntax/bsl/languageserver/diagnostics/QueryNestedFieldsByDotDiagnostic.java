package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashSet;
import java.util.Set;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.SQL,
    DiagnosticTag.PERFORMANCE,
    DiagnosticTag.DESIGN
  }

)
public class QueryNestedFieldsByDotDiagnostic extends AbstractSDBLListenerDiagnostic {

  public boolean isVirtualTable = false;
  private final Set<ParseTree> columns = new HashSet<>();

  @Override
  public void enterQuery(SDBLParser.QueryContext ctx) {
    isVirtualTable = false;
    super.enterQuery(ctx);
  }

  @Override
  public void exitVirtualTableParameter(SDBLParser.VirtualTableParameterContext ctx) {
    isVirtualTable = true;
    super.exitVirtualTableParameter(ctx);
  }

  @Override
  public void enterCastFunction(SDBLParser.CastFunctionContext ctx) {
    super.enterCastFunction(ctx);
  }

  @Override
  public void enterFunctionCall(SDBLParser.FunctionCallContext ctx) {
    if(ctx.identifier != null && ctx.columnNames.size() > 1){
      diagnosticStorage.addDiagnostic(ctx);
    }
    super.enterFunctionCall(ctx);
  }

  @Override
  public void enterColumn(SDBLParser.ColumnContext ctx) {

    if((isVirtualTable && ctx.columnNames.size() == 1 && ctx.mdoName != null) || ctx.columnNames.size() > 1){
      diagnosticStorage.addDiagnostic(ctx);
    }
    super.enterColumn(ctx);
  }
}
