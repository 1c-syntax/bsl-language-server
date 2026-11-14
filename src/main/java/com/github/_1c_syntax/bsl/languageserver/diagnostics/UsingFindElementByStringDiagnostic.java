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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.PERFORMANCE
  }
)

public class UsingFindElementByStringDiagnostic extends AbstractVisitorDiagnostic {

  private final TypeService typeService;

  public UsingFindElementByStringDiagnostic(TypeService typeService) {
    this.typeService = typeService;
  }

  private final Pattern pattern = CaseInsensitivePattern.compile(
    "(НайтиПоНаименованию|FindByDescription|НайтиПоКоду|FindByCode|НайтиПоНомеру|FindByNumber)"
  );

  @Override
  public @Nullable ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    var matcher = pattern.matcher(ctx.methodName().getText());
    if (matcher.matches()) {
      var members = typeService.accessTargetsAt(documentContext, ctx.methodName().IDENTIFIER(), null);
      if (members.stream().anyMatch(member -> !member.descriptor().standardLibrary())) {
        return super.visitMethodCall(ctx);
      }
      var missing = missingSearchOption(ctx);
      if (missing != null) {
        diagnosticStorage.addDiagnostic(ctx, info.getResourceString(missing, ctx.methodName().getText()));
        return super.visitMethodCall(ctx);
      }
      BSLParser.CallParamContext param = ctx.doCall().callParamList().callParam().getFirst();
      if (param.getChildren().isEmpty() ||
        param.getStart().getType() == BSLParser.STRING ||
        param.getStart().getType() == BSLParser.DECIMAL) {
        diagnosticStorage.addDiagnostic(ctx, info.getMessage(matcher.group(0)));
      }
    }
    return super.visitMethodCall(ctx);
  }

  private @Nullable String missingSearchOption(BSLParser.MethodCallContext ctx) {
    var name = ctx.methodName().getText();
    String option;
    String ownerRu;
    String ownerEn;
    String messageKey;
    if ("НайтиПоНаименованию".equalsIgnoreCase(name) || "FindByDescription".equalsIgnoreCase(name)) {
      option = "ТочноеСоответствие";
      ownerRu = "СправочникМенеджер.";
      ownerEn = "CatalogManager.";
      messageKey = "missingExactMatch";
    } else if ("НайтиПоНомеру".equalsIgnoreCase(name) || "FindByNumber".equalsIgnoreCase(name)) {
      option = "ДатаИнтервала";
      ownerRu = "ДокументМенеджер.";
      ownerEn = "DocumentManager.";
      messageKey = "missingPeriod";
    } else {
      return null;
    }
    var members = typeService.accessTargetsAt(documentContext, ctx.methodName().IDENTIFIER(), null);
    if (members.isEmpty()) {
      return null;
    }
    var arguments = ctx.doCall().callParamList().callParam();
    for (var member : members) {
      if (member.owner() == null || !member.descriptor().standardLibrary()) {
        return null;
      }
      var owner = member.owner().qualifiedName();
      if (!(owner.regionMatches(true, 0, ownerRu, 0, ownerRu.length())
        || owner.regionMatches(true, 0, ownerEn, 0, ownerEn.length()))
        || member.descriptor().signatures().isEmpty()) {
        return null;
      }
      for (var signature : member.descriptor().signatures()) {
        int index = -1;
        for (int i = 0; i < signature.parameters().size(); i++) {
          if (signature.parameters().get(i).matches(option)) {
            index = i;
            break;
          }
        }
        if (index < 0 || (arguments.size() > index && arguments.get(index).expression() != null)) {
          return null;
        }
      }
    }
    return messageKey;
  }

}
