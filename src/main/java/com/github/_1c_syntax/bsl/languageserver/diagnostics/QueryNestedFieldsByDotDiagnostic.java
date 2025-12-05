/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.SDBLParser;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.SQL,
    DiagnosticTag.PERFORMANCE
    DiagnosticTag.DESIGN
  }

)
public class QueryNestedFieldsByDotDiagnostic extends AbstractSDBLListenerDiagnostic {

  //Флаг обработки параметров виртуальной таблицы
  private boolean isVirtualTable = false;

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
