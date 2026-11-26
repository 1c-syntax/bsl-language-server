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
package com.github._1c_syntax.bsl.languageserver.diagnostics.typo;

import com.github._1c_syntax.bsl.languageserver.utils.AbstractObjectPool;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.rules.Rule;

import java.util.function.Predicate;

/**
 * Пул экземпляров {@link JLanguageTool} для одного языка.
 * <p>
 * Пул ограничен: каждый экземпляр при создании загружает собственную копию
 * полного набора правил языка (десятки мегабайт на инстанс, отключение правил
 * память не освобождает), поэтому рост пула до числа потоков анализа
 * недопустим по памяти. При исчерпании лимита {@link #checkOut()} ждёт
 * возврата экземпляра.
 */
public class JLanguageToolPool extends AbstractObjectPool<JLanguageTool> {

  private static final int MAX_POOL_SIZE = 4;

  private final Language language;

  public JLanguageToolPool(Language language) {
    super(Math.min(MAX_POOL_SIZE, Runtime.getRuntime().availableProcessors()));
    this.language = language;
  }

  @Override
  protected JLanguageTool create() {
    JLanguageTool languageTool = new JLanguageTool(language);

    languageTool.getAllRules().stream()
      .filter(Predicate.not(Rule::isDictionaryBasedSpellingRule))
      .map(Rule::getId)
      .forEach(languageTool::disableRule);

    return languageTool;
  }
}
