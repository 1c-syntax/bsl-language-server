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

  //Флаг обработки параметров виртуальной таблицы
  public boolean isVirtualTable = false;

  @Override
  public void enterQuery(SDBLParser.QueryContext ctx) {
    isVirtualTable = false; //Сбрасываем флаг при начале обработки запроса
    super.enterQuery(ctx);
  }

  @Override
  public void exitVirtualTableParameter(SDBLParser.VirtualTableParameterContext ctx) {
    isVirtualTable = true; //Взводим флаг при начале обработки параметров виртуальной таблицы
    super.exitVirtualTableParameter(ctx);
  }

  @Override
  public void enterFunctionCall(SDBLParser.FunctionCallContext ctx) {
    //Контролируем разыменование в функциях (ВЫРАЗИТЬ, ЕСТЬNULL и т.д.)
    if(ctx.identifier != null && ctx.columnNames.size() > 1){
      diagnosticStorage.addDiagnostic(ctx);
    }
    super.enterFunctionCall(ctx);
  }

  @Override
  public void enterColumn(SDBLParser.ColumnContext ctx) {
    /*Если взведен флаг обработки виртуальной таблицы
    и определен контекст метаданных, то проверяем заполненность контекста имен колонок.
    В противном случае считаем что работаем со стандартным полем выборки или соединения
    и выводим ошибку когда список имен колонки содержит более одного идентификатора
    */
    if((isVirtualTable && ctx.mdoName != null && !ctx.columnNames.isEmpty()) || ctx.columnNames.size() > 1){

      diagnosticStorage.addDiagnostic(ctx);
    }
    super.enterColumn(ctx);
  }
}
