/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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

import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildOptions;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 2,
  tags = {DiagnosticTag.SUSPICIOUS, DiagnosticTag.BADPRACTICE}
)
@RequiredArgsConstructor
public class LostVariableDiagnostic extends AbstractVisitorDiagnostic {

  private final ControlFlowGraphIndex graphs;
  private final TypeService types;

  @DiagnosticParameter(type = Boolean.class, defaultValue = "false")
  private boolean checkFormShadowing;

  @Override
  public void configure(Map<String, Object> configuration) {
    checkFormShadowing = (boolean) configuration.getOrDefault("checkFormShadowing", false);
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    if (checkFormShadowing && documentContext.getModuleType() == ModuleType.FormModule
      && documentContext.getMdObject().isPresent()) {
      for (var variable : documentContext.getSymbolTree().getVariables()) {
        if ((variable.getKind() == VariableKind.LOCAL || variable.getKind() == VariableKind.PARAMETER)
          && types.findSelfMember(documentContext, variable.getName(), MemberKind.PROPERTY).isPresent()) {
          diagnosticStorage.addDiagnostic(variable.getVariableNameRange(),
            info.getResourceString("formShadow", variable.getName()));
        }
      }
    }
    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {
    // Only the outer body is visited. Its graph already contains all nested code blocks.
    if (ctx.getChildCount() > 0 && !Trees.nodeContainsErrors(ctx)) {
      var graph = graphs.graphOf(documentContext, ctx, CfgBuildOptions.defaults());
      new LostVariableFlow(documentContext, ctx, graph).check((name, message) ->
        diagnosticStorage.addDiagnostic(name, info.getResourceString(message, name.getText())));
    }
    return ctx;
  }
}
