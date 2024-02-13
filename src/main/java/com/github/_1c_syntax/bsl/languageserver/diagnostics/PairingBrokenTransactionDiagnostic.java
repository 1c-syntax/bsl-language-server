/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class PairingBrokenTransactionDiagnostic extends AbstractVisitorDiagnostic {

  private final Pattern beginTransaction = CaseInsensitivePattern.compile(
    "НачатьТранзакцию|BeginTransaction"
  );

  private final Pattern beginEndTransaction = CaseInsensitivePattern.compile(
    "ЗафиксироватьТранзакцию|CommitTransaction|НачатьТранзакцию|BeginTransaction"
  );

  private final Pattern beginCancelTransaction = CaseInsensitivePattern.compile(
    "ОтменитьТранзакцию|RollbackTransaction|НачатьТранзакцию|BeginTransaction"
  );

  private final HashMap<String, String> pairMethodsBeginEnd = new HashMap<>();
  private final HashMap<String, String> pairMethodsBeginCancel = new HashMap<>();

  public PairingBrokenTransactionDiagnostic() {
    pairMethodsBeginEnd.put("НАЧАТЬТРАНЗАКЦИЮ", "ЗафиксироватьТранзакцию");
    pairMethodsBeginEnd.put("ЗАФИКСИРОВАТЬТРАНЗАКЦИЮ", "НачатьТранзакцию");
    pairMethodsBeginEnd.put("BEGINTRANSACTION", "CommitTransaction");
    pairMethodsBeginEnd.put("COMMITTRANSACTION", "BeginTransaction");

    pairMethodsBeginCancel.put("НАЧАТЬТРАНЗАКЦИЮ", "ОтменитьТранзакцию");
    pairMethodsBeginCancel.put("ОТМЕНИТЬТРАНЗАКЦИЮ", "НачатьТранзакцию");
    pairMethodsBeginCancel.put("BEGINTRANSACTION", "RollbackTransaction");
    pairMethodsBeginCancel.put("ROLLBACKTRANSACTION", "BeginTransaction");
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    findAndAddDiagnostic(ctx, beginEndTransaction, pairMethodsBeginEnd);
    findAndAddDiagnostic(ctx, beginCancelTransaction, pairMethodsBeginCancel);
    return ctx;
  }

  private void findAndAddDiagnostic(ParseTree ctx, Pattern pattern, HashMap<String, String> pairMethods) {

    ArrayList<ParseTree> allTranCalls = new ArrayList<>();

    Trees.findAllRuleNodes(ctx, BSLParser.RULE_globalMethodCall)
      .stream()
      .filter(node -> pattern.matcher((
        (BSLParser.GlobalMethodCallContext) node).methodName().getText()).matches())
      .collect(Collectors.toCollection(() -> allTranCalls));

    ArrayDeque<ParseTree> beginCalls = new ArrayDeque<>();
    for (ParseTree tranCall : allTranCalls) {
      if (beginTransaction.matcher(((BSLParser.GlobalMethodCallContext) tranCall).methodName().getText()).matches()) {
        beginCalls.add(tranCall);
      } else if (!beginCalls.isEmpty()) {
        beginCalls.pop();
      } else {
        addDiagnosticWithMessage(tranCall, pairMethods);
      }
    }
    if (!beginCalls.isEmpty()) {
      beginCalls.forEach(tranCall -> addDiagnosticWithMessage(tranCall, pairMethods));
    }
  }

  private void addDiagnosticWithMessage(ParseTree tranCall, HashMap<String, String> pairMethods) {
    String methodName = ((BSLParser.GlobalMethodCallContext) tranCall).methodName().getText();
    diagnosticStorage.addDiagnostic((BSLParser.GlobalMethodCallContext) tranCall,
      info.getMessage(pairMethods.get(methodName.toUpperCase(Locale.ENGLISH)), methodName));
  }

}
