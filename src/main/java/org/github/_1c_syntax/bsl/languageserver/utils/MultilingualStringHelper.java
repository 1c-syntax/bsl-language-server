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
package org.github._1c_syntax.bsl.languageserver.utils;

import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class MultilingualStringHelper {

  public static boolean isMultilingualString(BSLParser.GlobalMethodCallContext globalMethodCallContext) {
    Pattern nStrPattern = Pattern.compile(
      "НСтр|NStr",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    return !nStrPattern.matcher(globalMethodCallContext.methodName().getText()).find();
  }

  public static boolean hasAllDeclaredLanguages(BSLParser.GlobalMethodCallContext globalMethodCallContext, String languages) {
    String text = getMultilingualString(globalMethodCallContext);
    Map<String, String> expandedMultilingualString = expandMultilingualString(text);
    if(expandedMultilingualString.isEmpty()) {
      return true;
    }

    String[] expectedLanguages = languages.replaceAll("\\s", "").split(",");

    for(String lang : expectedLanguages) {
      if(!expandedMultilingualString.containsKey(lang)) {
        return false;
      }
    };

    return true;
  }

  private static String getMultilingualString(BSLParser.GlobalMethodCallContext globalMethodCallContext) {
    return globalMethodCallContext.doCall().callParamList().callParam(0).getText();
  }

  private static Map<String, String> expandMultilingualString(String text) {
    String[] languagesStrings = text.split("';");

    Map<String, String> expandedMultilingualString = new HashMap<>();

    for (String s : languagesStrings) {
      String[] parts = s.split("='");
      if(parts.length == 2) {
        expandedMultilingualString.put(parts[0].replaceAll("\\W+", ""), parts[1]);
      }
    }

    return expandedMultilingualString;
  }

}
