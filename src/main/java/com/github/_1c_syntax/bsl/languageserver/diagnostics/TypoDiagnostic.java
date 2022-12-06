/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.diagnostics.typo.JLanguageToolPool;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.Token;
import org.apache.commons.lang3.StringUtils;
import org.languagetool.JLanguageTool;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.language.Russian;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
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

  @Getter(lazy = true, value = AccessLevel.PRIVATE)
  private static final Map<String, JLanguageToolPool> languageToolPoolMap = Map.of(
    "en", new JLanguageToolPool(new AmericanEnglish()),
    "ru", new JLanguageToolPool(new Russian())
  );

  /**
   * Карта, хранящая результат проверки слова (ошибка/нет ошибки) в разрезе языков.
   */
  private static final Map<String, Map<String, Boolean>> checkedWords = Map.of(
    "en", new ConcurrentHashMap<>(),
    "ru", new ConcurrentHashMap<>()
  );

  private static final Pattern SPACES_PATTERN = Pattern.compile("\\s+");
  private static final Pattern QUOTE_PATTERN = Pattern.compile("\"");
  private static final String FORMAT_STRING_RU = "Л=|ЧЦ=|ЧДЦ=|ЧС=|ЧРД=|ЧРГ=|ЧН=|ЧВН=|ЧГ=|ЧО=|ДФ=|ДЛФ=|ДП=|БЛ=|БИ=";
  private static final String FORMAT_STRING_EN = "|L=|ND=|NFD=|NS=|NDS=|NGS=|NZ=|NLZ=|NG=|NN=|NF=|DF=|DLF=|DE=|BF=|BT=";
  private static final Pattern FORMAT_STRING_PATTERN =
    CaseInsensitivePattern.compile(FORMAT_STRING_RU + FORMAT_STRING_EN);

  private static final Integer[] rulesToFind = new Integer[]{
    BSLParser.RULE_string,
    BSLParser.RULE_lValue,
    BSLParser.RULE_var_name,
    BSLParser.RULE_subName
  };
  private static final Set<Integer> tokenTypes = Set.of(
    BSLParser.STRING,
    BSLParser.IDENTIFIER
  );

  private static final int DEFAULT_MIN_WORD_LENGTH = 3;
  private static final String DEFAULT_USER_WORDS_TO_IGNORE = "";

  @DiagnosticParameter(
    type = Integer.class,
    defaultValue = "" + DEFAULT_MIN_WORD_LENGTH
  )
  private int minWordLength = DEFAULT_MIN_WORD_LENGTH;

  @DiagnosticParameter(
    type = String.class
  )
  private String userWordsToIgnore = DEFAULT_USER_WORDS_TO_IGNORE;
  @DiagnosticParameter(
    type = Boolean.class
  )
  private Boolean caseInsensitive = false;

  @Override
  public void configure(Map<String, Object> configuration) {
    super.configure(configuration);
    minWordLength = Math.max(minWordLength, DEFAULT_MIN_WORD_LENGTH);
  }

  private List<String> getWordsToIgnore() {
    var delimiter = ",";
    String exceptions = SPACES_PATTERN.matcher(info.getResourceString("diagnosticExceptions")).replaceAll("");
    if (!userWordsToIgnore.isEmpty()) {
      exceptions = exceptions + delimiter + SPACES_PATTERN.matcher(userWordsToIgnore).replaceAll("");
    }

    if (caseInsensitive) {
      exceptions = exceptions.toLowerCase();
    }

    return Arrays.stream(exceptions.split(delimiter))
      .collect(Collectors.toList());
  }

  private static JLanguageTool acquireLanguageTool(String lang) {
    return getLanguageToolPoolMap().get(lang).checkOut();
  }

  private static void releaseLanguageTool(String lang, JLanguageTool languageTool) {
    getLanguageToolPoolMap().get(lang).checkIn(languageTool);
  }

  private Map<String, List<Token>> getTokensMap(
    DocumentContext documentContext
  ) {
    List<String> wordsToIgnore = getWordsToIgnore();
    Map<String, List<Token>> tokensMap = new HashMap<>();

    Trees.findAllRuleNodes(documentContext.getAst(), rulesToFind).stream()
      .map(BSLParserRuleContext.class::cast)
      .flatMap(ruleContext -> ruleContext.getTokens().stream())
      .filter(token -> tokenTypes.contains(token.getType()))
      .filter(token -> !FORMAT_STRING_PATTERN.matcher(token.getText()).find())
      .forEach((Token token) -> {
          String curText = QUOTE_PATTERN.matcher(token.getText()).replaceAll("").trim();
          String[] camelCaseSplitWords = StringUtils.splitByCharacterTypeCamelCase(curText);

          Arrays.stream(camelCaseSplitWords)
            .filter(Predicate.not(String::isBlank))
            .filter(element -> element.length() >= minWordLength)
            .filter(element -> wordsToIgnore.stream().noneMatch(word
                -> (caseInsensitive && word.equalsIgnoreCase(element))
                || (!caseInsensitive && word.equals(element))))
            .forEach(element -> tokensMap.computeIfAbsent(element, newElement -> new ArrayList<>()).add(token));
        }
      );

    return tokensMap;
  }

  @Override
  protected void check() {

    String lang = info.getResourceString("diagnosticLanguage");
    Map<String, Boolean> checkedWordsForLang = checkedWords.get(lang);
    Map<String, List<Token>> tokensMap = getTokensMap(documentContext);

    // build string of unchecked words
    Set<String> uncheckedWords = tokensMap.keySet().stream()
      .filter(word -> !checkedWordsForLang.containsKey(word))
      .collect(Collectors.toSet());

    if (uncheckedWords.isEmpty()) {
      fireDiagnosticOnCheckedWordsWithErrors(tokensMap);
      return;
    }

    // Join with double \n to force LT make paragraph after each word.
    // Otherwise results may be flaky cause of sort order of words in file.
    String uncheckedWordsString = String.join("\n\n", uncheckedWords);

    JLanguageTool languageTool = acquireLanguageTool(lang);

    List<RuleMatch> matches = Collections.emptyList();
    try {
      matches = languageTool.check(
        uncheckedWordsString,
        true,
        JLanguageTool.ParagraphHandling.ONLYNONPARA
      );
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      releaseLanguageTool(lang, languageTool);
    }

    // check words and mark matched as checked
    matches.stream()
      .map(ruleMatch -> ruleMatch.getSentence().getTokens()[1].getToken())
      .forEach(word -> checkedWordsForLang.put(word, true));

    // mark unmatched words without errors as checked
    uncheckedWords.forEach(word -> checkedWordsForLang.putIfAbsent(word, false));

    fireDiagnosticOnCheckedWordsWithErrors(tokensMap);
  }

  private void fireDiagnosticOnCheckedWordsWithErrors(
    Map<String, List<Token>> tokensMap
  ) {
    String lang = info.getResourceString("diagnosticLanguage");
    Map<String, Boolean> checkedWordsForLang = checkedWords.get(lang);

    tokensMap.entrySet().stream()
      .filter(entry -> checkedWordsForLang.getOrDefault(entry.getKey(), false))
      .forEach((Map.Entry<String, List<Token>> entry) -> {
        String word = entry.getKey();
        List<Token> tokens = entry.getValue();

        tokens.forEach(token -> diagnosticStorage.addDiagnostic(token, info.getMessage(word)));
      });
  }

}
