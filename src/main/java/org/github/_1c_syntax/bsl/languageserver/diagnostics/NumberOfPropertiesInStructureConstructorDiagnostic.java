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
import org.antlr.v4.runtime.tree.Trees;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;
import java.util.Collection;


/**
 * @author Leon Chagelishvili <lChagelishvily@gmail.com>
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 10
)

public class NumberOfPropertiesInStructureConstructorDiagnostic extends AbstractVisitorDiagnostic{

  private static final int MAX_PROPERTIES_COUNT = 3;

  @Override
  public ParseTree visitNewExpression(BSLParser.NewExpressionContext ctx) {

    if(!(DiagnosticHelper.isStructureType(ctx.typeName()) || DiagnosticHelper.isFixedStructureType(ctx.typeName()))){
      return super.visitNewExpression(ctx);
    }

    Collection<ParseTree> paramList = Trees.findAllRuleNodes(ctx, BSLParser.RULE_callParamList);

    if(paramList.stream()
      .limit(1)
      .anyMatch(ParseTree -> ((BSLParser.CallParamListContext) ParseTree).callParam().size() > MAX_PROPERTIES_COUNT + 1))
      addDiagnostic(ctx);

    return super.visitNewExpression(ctx);
  }

}
