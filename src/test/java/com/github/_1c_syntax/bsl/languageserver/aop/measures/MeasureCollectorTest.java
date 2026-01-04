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

import com.github._1c_syntax.bsl.languageserver.utils.ThrowingSupplier;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("measures")
class MeasureCollectorTest {

  @Autowired
  private MeasureCollector measureCollector;

  private ByteArrayOutputStream outContent;

  private final ThrowingSupplier<Void> throwingSupplier = () -> null;

  @BeforeEach
  void setUpStreams() {
    measureCollector.getMeasures().clear();
    outContent = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outContent));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
  }

  @Test
  void testMeasuresCollecting() {
    // when
    measureCollector.measureIt(throwingSupplier, "test");

    // then
    var measures = measureCollector.getMeasures();

    assertThat(measures)
      .containsKey("test")
      .extractingByKey("test")
      .asInstanceOf(InstanceOfAssertFactories.LIST)
      .isNotEmpty();
  }

  @Test
  void testMeasuresPrint() {
    // given
    measureCollector.measureIt(throwingSupplier, "test");

    // when
    measureCollector.printMeasures();

    // then
    assertThat(outContent.toString()).containsPattern("test - \\d+");
  }

}