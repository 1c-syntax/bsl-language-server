/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver;

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class BSLLSPLauncherTest {

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;

  @BeforeEach
  void setUpStreams() {
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  @ExpectSystemExitWithStatus(0)
  void testHelp() {
    // given
    String[] args = new String[]{"--help"};


    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    assertThat(outContent.toString()).containsIgnoringCase("usage: ");
  }

  @Test
  @ExpectSystemExitWithStatus(1)
  void testParseError() {
    // given
    String[] args = new String[]{"--error"};

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // FIXME How test logger?
    // assertThat(outContent.toString()).containsIgnoringCase("Unrecognized option: --error");
  }

  @Test
  @ExpectSystemExitWithStatus(0)
  void testAnalyze() {
    // given
    String[] args = new String[]{"--analyze"};

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    assertThat(errContent.toString()).contains("100%");
  }

  @Test
  void testWithoutParameters() {
    // given
    String[] args = new String[]{};

    // when
    BSLLSPLauncher.main(args);

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    assertThat(errContent.toString()).isEmpty();
  }
}