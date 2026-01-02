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
package com.github._1c_syntax.bsl.languageserver.aop.measures;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("measures")
class MeasuresSubsystemTest {

  @Autowired
  private ServerContext serverContext;

  @Autowired
  private ConfigurableApplicationContext applicationContext;

  @Autowired
  private MeasureCollector measureCollector;

  @AfterEach
  void afterAll() {
    applicationContext.getBeanFactory().destroySingletons();
  }

  @Test
  void testMeasuresAreCollected() {
    // given
    var configurationRoot = Absolute.path(TestUtils.PATH_TO_METADATA);
    serverContext.setConfigurationRoot(configurationRoot);

    // when
    serverContext.populateContext();
    var documentContext = TestUtils.getDocumentContext("Запрос = Новый Запрос(\"ВЫБРАТЬ 1 как а\");");
    documentContext.getDiagnostics();

    // then
    Map<String, List<Long>> measures = measureCollector.getMeasures();

    assertThat(measures)
      .containsKey("context: ast")
      .containsKey("context: queries")
      .containsKey("context: queryAst")
      .containsKey("context: symbolTree")
      .containsKey("context: diagnosticIgnorance")
      .containsKey("context: cognitiveComplexity")
      .containsKey("context: cyclomaticComplexity")
      .containsKey("context: metrics")
      .containsKey("context: configuration")
    ;

    assertThat(measures.keySet())
      .anyMatch(s -> s.startsWith("diagnostic: "))
      .anyMatch(s -> s.startsWith("computer: "))
    ;

    assertThat(measures.values())
      .allSatisfy(longs -> assertThat(longs).isNotEmpty())
    ;
  }

}