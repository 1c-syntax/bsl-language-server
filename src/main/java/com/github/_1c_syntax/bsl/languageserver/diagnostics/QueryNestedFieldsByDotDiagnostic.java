package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.SDBLParser;

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
  public void enterFunctionCall(SDBLParser.FunctionCallContext ctx) {
    if(ctx.identifier != null && ctx.columnNames.size() > 1){
      diagnosticStorage.addDiagnostic(ctx,
        info.getMessage(ctx.getText()));
    }
    super.enterFunctionCall(ctx);
  }

  @Override
  public void enterColumn(SDBLParser.ColumnContext ctx) {

    if((isVirtualTable && ctx.columnNames.size() == 1 && ctx.mdoName != null) || ctx.columnNames.size() > 1){

      diagnosticStorage.addDiagnostic(ctx,
        info.getMessage(ctx.getText()));
    }
    super.enterColumn(ctx);
  }
}
