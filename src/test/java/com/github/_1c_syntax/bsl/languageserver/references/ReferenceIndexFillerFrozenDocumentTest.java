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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Индекс ссылок должен обновляться при перестроении документа с новым содержимым
 * независимо от признака заморозки вычисленных данных.
 * <p>
 * Сценарий: документ проиндексирован, заморожен и очищен (как в {@code populateContext}),
 * затем его содержимое изменилось «на диске» и документ перечитан
 * ({@code workspace/didChangeWatchedFiles} после git checkout, либо
 * {@code McpDocumentReader.analyze} после правки файла MCP-клиентом). Устаревший
 * индекс приводил к ложным срабатываниям UnusedLocalVariable/UnusedLocalMethod.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ReferenceIndexFillerFrozenDocumentTest {

  @Autowired
  private ReferenceIndex referenceIndex;

  @Test
  void rebuildOfFrozenDocumentRefreshesIndex() {
    // given: документ проиндексирован через событие rebuild
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          А = 1;
          Б = А;
      КонецПроцедуры
      """);

    var varA = documentContext.getSymbolTree().getVariables().stream()
      .filter(v -> v.getName().equals("А"))
      .findFirst().orElseThrow();

    assertThat(referenceIndex.getReferencesTo(varA))
      .anyMatch(ref -> ref.occurrenceType() == OccurrenceType.REFERENCE);

    // when: документ заморожен и очищен (как после populateContext),
    // затем перечитан с изменённым содержимым
    documentContext.freezeComputedData();
    var serverContext = documentContext.getServerContext();
    serverContext.tryClearDocument(documentContext);
    serverContext.rebuildDocument(documentContext, """
      Процедура Тест()
          В = 1;
          Б = В;
      КонецПроцедуры
      """, 1);

    var varV = documentContext.getSymbolTree().getVariables().stream()
      .filter(v -> v.getName().equals("В"))
      .findFirst().orElseThrow();

    // then: использование новой переменной видно в индексе
    assertThat(referenceIndex.getReferencesTo(varV))
      .anyMatch(ref -> ref.occurrenceType() == OccurrenceType.REFERENCE);
  }
}
