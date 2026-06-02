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

import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Лёгкий нагрузочный прогон по типизации всех переменных модуля.
 * Ничего не утверждает кроме того, что прогон завершился; использовать
 * для ручной фиксации до/после изменений в инферансере или кэшах.
 */
@SpringBootTest
@Slf4j
class TypeServiceBenchmarkTest {

  private static final String PATH_TO_FILE = "./src/test/resources/types/TypeResolver.os";
  private static final int ITERATIONS = 200;

  @Autowired
  private TypeService typeService;

  @Test
  void benchmarkInferenceHotPath() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var module = documentContext.getSymbolTree().getModule();
    var variables = documentContext.getSymbolTree().getVariables();

    // прогрев — JIT + populate caches
    for (var v : variables) {
      var ref = Reference.of(module, v,
        new Location(documentContext.getUri().toString(), v.getSelectionRange()),
        OccurrenceType.DEFINITION);
      typeService.typesAt(ref);
    }

    var start = System.nanoTime();
    long callCount = 0;
    for (int i = 0; i < ITERATIONS; i++) {
      for (var v : variables) {
        var ref = Reference.of(module, v,
          new Location(documentContext.getUri().toString(), v.getSelectionRange()),
          OccurrenceType.DEFINITION);
        typeService.typesAt(ref);
        callCount++;
      }
    }
    var elapsedNs = System.nanoTime() - start;
    double perCallUs = (elapsedNs / 1000.0) / Math.max(1, callCount);

    LOGGER.info("TypeService bench: {} calls / {} variables / {} iter -> {} us/call",
      callCount, variables.size(), ITERATIONS, String.format("%.2f", perCallUs));

    // sanity check: ничего не сломалось, выполнено ожидаемое число вызовов
    assertThat(callCount).isEqualTo((long) ITERATIONS * variables.size());
  }
}
