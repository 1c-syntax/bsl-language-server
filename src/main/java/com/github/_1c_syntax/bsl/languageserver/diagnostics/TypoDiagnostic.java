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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.Token;
import org.apache.commons.lang3.StringUtils;
import org.languagetool.JLanguageTool;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.language.Russian;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.spelling.SpellingCheckRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

  private static final Russian ruLang = new Russian();
  private static final AmericanEnglish enLang = new AmericanEnglish();
  private static final JLanguageTool ruLangTool = new JLanguageTool(ruLang);
  private static final JLanguageTool enLangTool = new JLanguageTool(enLang);
  private static Map<String, JLanguageTool> languageToolMap = new HashMap<>();

  private static final int DEFAULT_MIN_WORD_LENGTH = 2;

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + DEFAULT_MIN_WORD_LENGTH
  )
  private int minWordLength = DEFAULT_MIN_WORD_LENGTH;

  public TypoDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }
    minWordLength = (int) configuration.getOrDefault("diagnosticMinWordLength", minWordLength);
  }

  private ArrayList<String> getWordsToIgnore() {
    String exceptions = info.getResourceString("diagnosticExceptions").replaceAll("\n", "");
    return new ArrayList<>(Arrays.asList(exceptions.split(",")));
  }

  @Override
  protected void check(DocumentContext documentContext) {

    String lang = info.getResourceString("diagnosticLanguage");
    ArrayList<String> wordsToIgnore = getWordsToIgnore();

    languageToolMap.put("ru", ruLangTool);
    languageToolMap.put("en", enLangTool);

    languageToolMap.get(lang).getAllRules().stream().filter(rule -> !rule.isDictionaryBasedSpellingRule()).map(Rule::getId).forEach(languageToolMap.get(lang)::disableRule);
    languageToolMap.get(lang).getAllActiveRules().forEach(rule -> ((SpellingCheckRule) rule).addIgnoreTokens(wordsToIgnore));

    StringBuilder text = new StringBuilder();
    Map<String, List<Token>> tokensMap = new HashMap<>();

    documentContext.getTokens().stream()
      .filter(token -> token.getType() == BSLParser.STRING
        || token.getType() == BSLParser.IDENTIFIER)
      .forEach(token -> {
        String curText = token.getText().replaceAll("\"", "");
        var splitList = Arrays.asList(StringUtils.splitByCharacterTypeCamelCase(curText));
        splitList.stream()
          .filter(element -> element.length() >= minWordLength)
          .forEach(element -> {

          tokensMap.computeIfPresent(element, (key, value) -> {
            value.add(token);
            return value;
          });

          tokensMap.computeIfAbsent(element, key -> {
            List<Token> value = new ArrayList<>();
            value.add(token);
            return value;

          });
        });

        text.append(" ");
        text.append(String.join(" ", splitList));

      });

    String result = Arrays.stream(text.toString().trim().split("\\s+")).distinct().collect(Collectors.joining(" "));

    try {
      List<RuleMatch> matches;

      synchronized (languageToolMap.get(lang)) {
        matches = languageToolMap.get(lang).check(result, true, JLanguageTool.ParagraphHandling.ONLYNONPARA);
      }

      if (!matches.isEmpty()) {
        var usedNodes = new HashSet<Token>();

        matches.stream()
          .filter(ruleMatch -> !ruleMatch.getSuggestedReplacements().isEmpty())
          .map(ruleMatch -> result.substring(ruleMatch.getFromPos(), ruleMatch.getToPos()))
          .map(tokensMap::get).filter(Objects::nonNull)
          .forEach(nodeList -> nodeList.stream()
            .filter(parseTree -> !usedNodes.contains(parseTree))
            .forEach(token -> {
              diagnosticStorage.addDiagnostic(token, info.getMessage(token.getText()));
              usedNodes.add(token);
        }));
      }
    } catch(IOException e){
      LOGGER.error(e.getMessage(), e);
    }
  }
}