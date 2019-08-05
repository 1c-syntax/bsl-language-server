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
  import org.github._1c_syntax.bsl.parser.BSLParser;

  import java.util.ArrayList;
  import java.util.HashMap;
  import java.util.regex.Pattern;
  import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 15
)
public class PairingBrokenTranDiagnostic extends AbstractVisitorDiagnostic {

  private Pattern beginTransaction = Pattern.compile("НачатьТранзакцию|BeginTransaction",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private Pattern allTransaction = Pattern.compile("ЗафиксироватьТранзакцию|CommitTransaction|НачатьТранзакцию|BeginTransaction",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private HashMap<String, String> pairMethods = new HashMap<>();

  public PairingBrokenTranDiagnostic() {
    pairMethods.put("НАЧАТЬТРАНЗАКЦИЮ", "ЗафиксироватьТранзакцию");
    pairMethods.put("ЗАФИКСИРОВАТЬТРАНЗАКЦИЮ", "НачатьТранзакцию");
    pairMethods.put("BEGINTRANSACTION", "CommitTransaction");
    pairMethods.put("COMMITTRANSACTION", "BeginTransaction");
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    return visitFuncProc(ctx);
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {
    return visitFuncProc(ctx);
  }

  private ParseTree visitFuncProc(ParseTree ctx) {

    ArrayList<ParseTree> allTranCalls = new ArrayList<>();

    Trees.findAllRuleNodes(ctx, BSLParser.RULE_globalMethodCall)
      .stream()
      .filter(node -> allTransaction.matcher((
        (BSLParser.GlobalMethodCallContext) node).methodName().getText()).matches())
      .collect(Collectors.toCollection(() -> allTranCalls));

    if(!allTranCalls.isEmpty()) {
      ArrayList<ParseTree> beginCalls = new ArrayList<>();
      for (ParseTree tranCall : allTranCalls) {
        if(beginTransaction.matcher(((BSLParser.GlobalMethodCallContext) tranCall)
            .methodName().getText()).matches()) {
          beginCalls.add(tranCall);
        } else if(!beginCalls.isEmpty()) {
          beginCalls.remove(beginCalls.size() - 1);
        } else {
          addDiagnosticWithMessage(tranCall);
        }
      }

      if(!beginCalls.isEmpty()) {
        beginCalls.forEach(tranCall -> addDiagnosticWithMessage(tranCall));
      }
    }

    return ctx;
  }

  private void addDiagnosticWithMessage(ParseTree tranCall) {
    String methodName = ((BSLParser.GlobalMethodCallContext) tranCall).methodName().getText();
    diagnosticStorage.addDiagnostic((BSLParser.GlobalMethodCallContext) tranCall,
      getDiagnosticMessage(pairMethods.get(methodName.toUpperCase()), methodName));
  }

}