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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
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

  private static final Pattern SEARCH_METHOD = CaseInsensitivePattern.compile(
    "(?<byDescription>НайтиПоНаименованию|FindByDescription)|НайтиПоКоду|FindByCode|"
      + "(?<byNumber>НайтиПоНомеру|FindByNumber)"
  );

  private final TypeService typeService;

  public UsingFindElementByStringDiagnostic(TypeService typeService) {
    this.typeService = typeService;
  }

  @Override
  public @Nullable ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    var matcher = SEARCH_METHOD.matcher(ctx.methodName().getText());
    if (matcher.matches()) {
      checkSearchCall(ctx, matcher);
    }
    return super.visitMethodCall(ctx);
  }

  private void checkSearchCall(BSLParser.MethodCallContext ctx, Matcher matcher) {
    var arguments = ctx.doCall().callParamList().callParam();
    var firstArgument = arguments.getFirst();
    var literalSearch = firstArgument.getChildren().isEmpty()
      || firstArgument.getStart().getType() == BSLParser.STRING
      || firstArgument.getStart().getType() == BSLParser.DECIMAL;
    var missingSecondArgument = arguments.size() < 2 || arguments.get(1).expression() == null;
    var checkExactMatch = missingSecondArgument && matcher.group("byDescription") != null;
    var checkIntervalDate = missingSecondArgument && matcher.group("byNumber") != null;
    if (!literalSearch && !checkExactMatch && !checkIntervalDate) {
      return;
    }

    var members = typeService.accessTargetsAt(documentContext, ctx.methodName().IDENTIFIER(), null);
    if (members.stream().anyMatch(member -> !member.descriptor().standardLibrary())) {
      return;
    }

    var methodName = matcher.group();
    if (checkExactMatch && allFromManager(members, "СправочникМенеджер.", "CatalogManager.")) {
      diagnosticStorage.addDiagnostic(ctx, info.getResourceString("missingExactMatch", methodName));
    } else if (checkIntervalDate && allFromManager(members, "ДокументМенеджер.", "DocumentManager.")) {
      diagnosticStorage.addDiagnostic(ctx, info.getResourceString("missingPeriod", methodName));
    } else if (literalSearch) {
      diagnosticStorage.addDiagnostic(ctx, info.getMessage(methodName));
    }
  }

  private static boolean allFromManager(List<TypedMember> members, String ownerPrefixRu, String ownerPrefixEn) {
    return !members.isEmpty() && members.stream().allMatch(member -> {
      var owner = member.owner();
      if (owner == null || member.descriptor().signatures().isEmpty()) {
        return false;
      }
      var name = owner.qualifiedName();
      return name.regionMatches(true, 0, ownerPrefixRu, 0, ownerPrefixRu.length())
        || name.regionMatches(true, 0, ownerPrefixEn, 0, ownerPrefixEn.length());
    });
  }

}
