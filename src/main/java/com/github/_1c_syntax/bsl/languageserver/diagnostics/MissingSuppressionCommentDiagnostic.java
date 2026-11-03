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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLLexer;

import java.util.regex.Pattern;

/**
 * Диагностика-матрёшка: проверяет, что у комментариев подавления диагностик
 * {@code // BSLLS:...-off} есть пояснение причины.
 * <p>
 * Голое подавление без объяснения — плохая практика: следующие разработчики
 * не понимают, почему диагностика отключена и можно ли её вернуть.
 * <p>
 * Сама диагностика не подавляема.
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.SUSPICIOUS
  }
)
public class MissingSuppressionCommentDiagnostic extends AbstractDiagnostic {

  /** Строка начинается с {@code // BSLLS:...-off} или {@code // BSLLS-off}. */
  private static final Pattern SUPPRESSION_PATTERN = Pattern.compile(
    "^\\s*//\\s*BSLLS(?::\\w+)?\\s*-\\s*(?:off|выкл)",
    Pattern.CASE_INSENSITIVE
  );

  /** Голое подавление: после {@code -off} нет поясняющего текста. */
  private static final Pattern BARE_SUPPRESSION_PATTERN = Pattern.compile(
    "^\\s*//\\s*BSLLS(?::\\w+)?\\s*-\\s*(?:off|выкл)\\s*$",
    Pattern.CASE_INSENSITIVE
  );

  @Override
  public void check() {
    var content = documentContext.getContentList();

    for (int i = 0; i < content.length; i++) {
      var line = content[i].stripTrailing();
      if (!line.stripLeading().startsWith("//")) {
        continue;
      }

      if (SUPPRESSION_PATTERN.matcher(line).find()
        && BARE_SUPPRESSION_PATTERN.matcher(line).matches()) {
        diagnosticStorage.addDiagnostic(
          i, 0, i, line.length());
      }
    }
  }

}
