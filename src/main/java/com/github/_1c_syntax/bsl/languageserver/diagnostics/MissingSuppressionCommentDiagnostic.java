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

import com.github._1c_syntax.bsl.languageserver.context.computer.DiagnosticIgnoranceComputer;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;

/**
 * Диагностика-матрёшка: проверяет, что у комментариев подавления диагностик
 * {@code // BSLLS:...-off} есть пояснение причины.
 * <p>
 * Проверяются как отдельностоящие комментарии, так и комментарии в конце строки с кодом.
 * Голое подавление без объяснения — плохая практика: следующие разработчики
 * не понимают, почему диагностика отключена и можно ли её вернуть.
 * <p>
 * Пояснением считается любой непустой текст после директивы подавления.
 * Сама диагностика не подавляема ({@code suppressible = false}).
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1,
  suppressible = false,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.SUSPICIOUS
  }
)
public class MissingSuppressionCommentDiagnostic extends AbstractDiagnostic {

  @Override
  public void check() {
    documentContext.getDiagnosticIgnorance().getSuppressionComments().stream()
      .filter(DiagnosticIgnoranceComputer.SuppressionComment::disabling)
      .filter(suppression -> !suppression.hasExplanation())
      .forEach(suppression -> diagnosticStorage.addDiagnostic(suppression.range()));
  }

}
