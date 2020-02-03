/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.StringUtils;
import org.languagetool.JLanguageTool;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.language.BritishEnglish;
import org.languagetool.language.Russian;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  }

)
@Slf4j
public class TypoDiagnostic extends AbstractDiagnostic {
  public TypoDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check(DocumentContext documentContext) {
    List<RuleMatch> matches;

    String lang = info.getResourceString("diagnosticLanguage");
    JLanguageTool langTool = null;
    if (lang.equals("en")) {
      langTool = new JLanguageTool(new AmericanEnglish());
    } else {
      langTool = new JLanguageTool(new BritishEnglish());
    }

    langTool.getAllRules().stream().filter(rule -> !rule.isDictionaryBasedSpellingRule()).map(Rule::getId).forEach(langTool::disableRule);

    BSLParser.FileContext tree = documentContext.getAst();
    List<ParseTree> list = new ArrayList<>();

    List<Integer> tokens = new ArrayList<>();
    tokens.add(BSLParser.STRING);
    tokens.add(BSLParser.IDENTIFIER);

    tokens.stream().map(token -> Trees.findAllTokenNodes(tree, token)).forEach(list::addAll);

    for (ParseTree element : list) {

      try {
        String[] arr = StringUtils.splitByCharacterTypeCamelCase(element.getText());
        boolean isTypo = false;

        for (String s : arr) {
          if (!isTypo) {
            matches = langTool.check(s);
            if (!matches.isEmpty()) {
              diagnosticStorage.addDiagnostic((BSLParserRuleContext) element.getParent(), info.getMessage(element.getText().replaceAll("\"", "")));
              isTypo = true;
            }
          }
        }
      } catch(IOException e){
        LOGGER.error(e.getMessage(), e);
      }
    }
  }
}