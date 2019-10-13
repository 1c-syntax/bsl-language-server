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
import java.util.regex.Pattern;

public final class MultilingualStringParser {

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
  private ArrayList<String> expectedLanguages;
  private Map<String, String> expandedMultilingualString = new HashMap<>();
  private ArrayList<String> missingLanguages = new ArrayList<>();

  public MultilingualStringParser(BSLParser.GlobalMethodCallContext globalMethodCallContext, String declaredLanguages)
    throws IllegalArgumentException {

    if(isNotMultilingualString(globalMethodCallContext)) {
      throw new IllegalArgumentException("Method not multilingual string");
    }

    this.globalMethodCallContext = globalMethodCallContext;
    this.isParentTemplate = hasTemplateInParents(globalMethodCallContext);
    this.expectedLanguages = new ArrayList<>(Arrays.asList(declaredLanguages.replaceAll("\\s", "").split(",")));
    expandMultilingualString();
    checkDeclaredLanguages();

  }

  private static boolean isNotMultilingualString(BSLParser.GlobalMethodCallContext globalMethodCallContext) {
    return !nStrMethodName.matcher(globalMethodCallContext.methodName().getText()).find();
  }

  private static boolean hasTemplateInParents(ParserRuleContext globalMethodCallContext) {
    ParserRuleContext parent = globalMethodCallContext.getParent();

    if(parent instanceof BSLParser.FileContext || parent instanceof BSLParser.StatementContext) {
      return false;
    }

    if(
      parent instanceof BSLParser.GlobalMethodCallContext
        && templateMethodName.matcher(((BSLParser.GlobalMethodCallContext) parent).methodName().getText()).find()
    ) {
      return true;
    }

    return hasTemplateInParents(parent);
  }

  private void expandMultilingualString() {
    String[] languagesStrings = getMultilingualString().split("';");
    for (String s : languagesStrings) {
      String[] parts = s.split("='");
      if(parts.length == VALID_LANG_PARTS) {
        expandedMultilingualString.put(parts[0].replaceAll("\\W+", ""), parts[1]);
      }
    }
  }

  private String getMultilingualString() {
    return globalMethodCallContext.doCall().callParamList().callParam(0).getText();
  }

  private void checkDeclaredLanguages() {
    if(expandedMultilingualString.isEmpty()) {
      missingLanguages = expectedLanguages;
      return;
    }

    for(String lang : expectedLanguages) {
      if(!expandedMultilingualString.containsKey(lang)) {
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
    return this.isParentTemplate;
  }

}
