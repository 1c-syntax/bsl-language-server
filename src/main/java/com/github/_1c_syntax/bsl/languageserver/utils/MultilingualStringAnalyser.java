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
package com.github._1c_syntax.bsl.languageserver.utils;

import org.antlr.v4.runtime.ParserRuleContext;
import com.github._1c_syntax.bsl.parser.BSLParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MultilingualStringAnalyser {

  private final static Byte VALID_LANG_PARTS = 2;
  private final static String NSTR_METHOD_NAME = "НСтр|NStr";
  private final static String TEMPLATE_METHOD_NAME = "СтрШаблон|StrTemplate";
  private final static Pattern nStrMethodName = Pattern.compile(
    NSTR_METHOD_NAME,
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );
  private final static Pattern templateMethodName = Pattern.compile(
    TEMPLATE_METHOD_NAME,
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private BSLParser.GlobalMethodCallContext globalMethodCallContext;
  private boolean isParentTemplate;
  private String variableName;
  private ArrayList<String> expectedLanguages;
  private Map<String, String> expandedMultilingualString = new HashMap<>();
  private ArrayList<String> missingLanguages = new ArrayList<>();

  public MultilingualStringAnalyser(String declaredLanguages) {

    this.expectedLanguages = new ArrayList<>(Arrays.asList(declaredLanguages.replaceAll("\\s", "").split(",")));

  }

  public void parse(BSLParser.GlobalMethodCallContext ctx) throws IllegalArgumentException {
    expandedMultilingualString.clear();
    missingLanguages.clear();
    isParentTemplate = false;

    if (isNotMultilingualString(ctx)) {
      throw new IllegalArgumentException("Method not multilingual string");
    }

    globalMethodCallContext = ctx;
    isParentTemplate = hasTemplateInParents(ctx);
    variableName = getVariableName(ctx);
    expandMultilingualString();
    checkDeclaredLanguages();
  }

  private static boolean isNotMultilingualString(BSLParser.GlobalMethodCallContext globalMethodCallContext) {
    return !nStrMethodName.matcher(globalMethodCallContext.methodName().getText()).find();
  }

  private static boolean hasTemplateInParents(ParserRuleContext globalMethodCallContext) {
    ParserRuleContext parent = globalMethodCallContext.getParent();

    if (parent instanceof BSLParser.FileContext || parent instanceof BSLParser.StatementContext) {
      return false;
    }

    if (parent instanceof BSLParser.GlobalMethodCallContext && isTemplate((BSLParser.GlobalMethodCallContext) parent)) {
      return true;
    }

    return hasTemplateInParents(parent);
  }

  private static boolean isTemplate(BSLParser.GlobalMethodCallContext parent) {
    return templateMethodName.matcher(parent.methodName().getText()).find();
  }

  private String getVariableName(BSLParser.GlobalMethodCallContext ctx) {
    BSLParser.AssignmentContext assignment = (BSLParser.AssignmentContext)
      Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_assignment);

    if (assignment == null) {
      return null;
    }

    BSLParser.LValueContext lValue = assignment.lValue();
    if (lValue == null) {
      return null;
    }

    return lValue.getText();
  }

  private void expandMultilingualString() {
    String[] languagesStrings = getMultilingualString().split("';");
    for (String s : languagesStrings) {
      String[] parts = s.split("='");
      if (parts.length == VALID_LANG_PARTS) {
        expandedMultilingualString.put(parts[0].replaceAll("\\W+", ""), parts[1]);
      }
    }
  }

  private String getMultilingualString() {
    return globalMethodCallContext.doCall().callParamList().callParam(0).getText();
  }

  private void checkDeclaredLanguages() {
    if (expandedMultilingualString.isEmpty()) {
      missingLanguages = new ArrayList<>(expectedLanguages);
      return;
    }

    for(String lang : expectedLanguages) {
      if (!expandedMultilingualString.containsKey(lang)) {
        missingLanguages.add(lang);
      }
    }
  }

  public boolean hasNotAllDeclaredLanguages() {
    return !missingLanguages.isEmpty();
  }

  public String getMissingLanguages() {
    return missingLanguages.toString();
  }

  public boolean isParentTemplate() {
    return isParentTemplate || istVariableUsingInTemplate();
  }

  private boolean istVariableUsingInTemplate() {
    if (variableName == null) {
      return false;
    }

    BSLParser.CodeBlockContext codeBlock = getCodeBlock();

    if (codeBlock == null) {
      return false;
    }

    return Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_globalMethodCall)
      .stream()
      .filter(n -> ((BSLParser.GlobalMethodCallContext) n).getStart().getLine() > globalMethodCallContext.getStart().getLine())
      .filter(n -> isTemplate((BSLParser.GlobalMethodCallContext) n))
      .map(n -> ((BSLParser.GlobalMethodCallContext) n).doCall().callParamList())
      .filter(Objects::nonNull)
      .map(BSLParser.CallParamListContext::callParam)
      .filter(cp -> !cp.isEmpty())
      .anyMatch(cp -> cp.stream().anyMatch(p -> p.getText().equalsIgnoreCase(variableName)));
  }

  private BSLParser.CodeBlockContext getCodeBlock() {
    return (BSLParser.CodeBlockContext) Trees.getAncestorByRuleIndex(
      globalMethodCallContext,
      BSLParser.RULE_codeBlock
    );
  }

}
