/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.diagnostics.typo;

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.rules.Rule;
import org.languagetool.rules.spelling.SpellingCheckRule;

import java.util.Arrays;
import java.util.List;

public class JLanguageToolPoolEntry {

  private final JLanguageTool languageTool;
  private String wordsToIgnore;

  public JLanguageToolPoolEntry(Language language) {
    languageTool = new JLanguageTool(language);
  }

  public JLanguageTool getLanguageTool(String wordsToIgnore) {

    if (!wordsToIgnore.equals(this.wordsToIgnore)) {
      this.wordsToIgnore = wordsToIgnore;
      List<String> ignoredTokens = Arrays.asList(this.wordsToIgnore.split(","));

      languageTool.getAllRules().stream()
        .filter(rule -> !rule.isDictionaryBasedSpellingRule())
        .map(Rule::getId)
        .forEach(languageTool::disableRule);

      languageTool.getAllActiveRules()
        .forEach(rule -> ((SpellingCheckRule) rule).addIgnoreTokens(ignoredTokens));
    }

    return languageTool;
  }
}
