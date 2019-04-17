/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package org.github._1c_syntax.bsl.languageserver.diagnostics;


import org.antlr.v4.runtime.tree.ParseTree;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.Map;


/**
 * @author Leon Chagelishvili <lChagelishvily@gmail.com>
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  scope = DiagnosticScope.ALL,
  minutesToFix = 10
)

public class NumberOfValuesInStructureConstructorDiagnostic extends AbstractVisitorDiagnostic{

  private static final int MAX_VALUES_COUNT = 3;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + MAX_VALUES_COUNT,
    description = "Допустимое количество значений свойств, передаваемых в конструктор структуры"
  )

  private int maxValuesCount = MAX_VALUES_COUNT;

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }
    maxValuesCount = (Integer) configuration.get("maxStructureConstructorValuesCount");
  }

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {

    if(!(DiagnosticHelper.isStructureType(ctx.typeName()) || DiagnosticHelper.isFixedStructureType(ctx.typeName()))){
      return super.visitNewExpression(ctx);
    }

    if(ctx.doCall().callParamList().callParam().size() > maxValuesCount + 1)
      addDiagnostic(ctx);

    return super.visitNewExpression(ctx);
  }

}
