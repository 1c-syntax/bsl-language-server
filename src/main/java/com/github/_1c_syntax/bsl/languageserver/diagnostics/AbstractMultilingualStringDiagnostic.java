/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.utils.MultilingualStringAnalyser;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;

/**
 * Базовый класс для диагностик, анализирующих многоязычные строки НСтр.
 */
public abstract class AbstractMultilingualStringDiagnostic extends AbstractVisitorDiagnostic {

  private static final String DECLARED_LANGUAGES_DEFAULT = "ru";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = DECLARED_LANGUAGES_DEFAULT
  )
  private String declaredLanguages = DECLARED_LANGUAGES_DEFAULT;

  /**
   * Парсер для анализа многоязычных строк.
   */
  protected MultilingualStringAnalyser parser = new MultilingualStringAnalyser(DECLARED_LANGUAGES_DEFAULT);

  @Override
  public void configure(Map<String, Object> configuration) {
    declaredLanguages = (String) configuration.get("declaredLanguages");
    parser = new MultilingualStringAnalyser(declaredLanguages);
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {

    if (parser.parse(ctx) && check()) {
      diagnosticStorage.addDiagnostic(ctx, info.getMessage(parser.getMissingLanguages()));
    }

    return super.visitGlobalMethodCall(ctx);

  }

  /**
   * Дополнительная проверка после парсинга строки.
   *
   * @return {@code true} если проверка не прошла и нужно регистрировать замечание
   */
  protected boolean check() {
    return false;
  }

}
