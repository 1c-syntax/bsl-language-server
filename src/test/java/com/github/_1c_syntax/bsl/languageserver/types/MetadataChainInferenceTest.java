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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Цепочка {@code Метаданные.Документы.<MD>.Реквизиты.<имя>}: проверяет наружный
 * эффект {@code MetadataCollectionSpecializer} через {@link TypeService} —
 * именно тот API, который видят consumer'ы (hover/completion/инференс выражения).
 * Регистрация типов покрыта low-level unit-тестами в registry, здесь — резолв
 * выражения целиком.
 */
@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=true")
@EnabledIfEnvironmentVariable(named = "BSL_LANGUAGE_SERVER_RUN_HBK_TESTS",
  matches = "true",
  disabledReason = "Требует HBK 1С (тип Метаданные и per-property element-type приходят из bsl-context)")
class MetadataChainInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void resolvesMetadataChain() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();

    var dc = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/MetadataChain.bsl");

    assertThat(qnamesAtRhs(dc, "КоллекцияДокументов"))
      .containsExactlyInAnyOrder("КоллекцияОбъектовМетаданных.Документы");
    assertThat(qnamesAtRhs(dc, "ДокументМД"))
      .containsExactlyInAnyOrder("ОбъектМетаданных: Документ.Документ1");
    assertThat(qnamesAtRhs(dc, "КоллекцияРеквизитов"))
      .containsExactlyInAnyOrder("КоллекцияОбъектовМетаданных.Реквизиты.Документ1");
    assertThat(qnamesAtRhs(dc, "РеквизитМД"))
      .containsExactlyInAnyOrder("ОбъектМетаданных: Реквизит");
    assertThat(qnamesAtRhs(dc, "ТЧ"))
      .containsExactlyInAnyOrder("ОбъектМетаданных: ТабличнаяЧасть.Документ1.ТабличнаяЧасть1");
  }

  /**
   * Кладёт курсор в правую часть присваивания {@code <var> = …} на последний
   * сегмент цепочки и возвращает qualifiedName'ы охватывающего выражения через
   * {@link TypeService#expressionTypesAt} — это API, на который реархитектура
   * #3993 свела resolve типа выражения для consumer'ов.
   */
  private List<String> qnamesAtRhs(DocumentContext dc, String assignedVar) {
    var content = dc.getContent();
    var marker = assignedVar + " = ";
    var markerIdx = content.indexOf(marker);
    assertThat(markerIdx)
      .as("qnamesAtRhs: marker `%s` not found in fixture", marker)
      .isGreaterThanOrEqualTo(0);
    var rhsStart = markerIdx + marker.length();
    var eolFromRhs = content.indexOf(';', rhsStart);
    assertThat(eolFromRhs)
      .as("qnamesAtRhs: end-of-statement `;` not found after marker `%s`", marker)
      .isGreaterThanOrEqualTo(0);
    var lastDot = content.lastIndexOf('.', eolFromRhs);
    // Ограничиваем точку RHS: если RHS без точек, не цепляем точку из ранее
    // идущего кода (комментарий/литерал) — каретку ставим в начало RHS.
    var lastDotInRhs = lastDot >= rhsStart ? lastDot : -1;
    var caret = lastDotInRhs < 0 ? rhsStart : lastDotInRhs + 1;
    int lineStart = content.lastIndexOf('\n', caret) + 1;
    int line = content.substring(0, caret).split("\n").length - 1;
    int charInLine = caret - lineStart;
    return qnames(typeService.expressionTypesAt(dc, new Position(line, charInLine)));
  }

  private static List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
