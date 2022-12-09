/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver;

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class BSLLSPLauncherTest {

  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;

  @BeforeEach
  void setUpStreams() {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
    System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err)));
  }

  @Test
  @ExpectSystemExitWithStatus(2)
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
    assertThat(errContent.toString()).containsIgnoringCase("Unknown option: '--error'");
  }

  @Test
  @ExpectSystemExitWithStatus(0)
  void testAnalyze() {
    // given
    String[] args = "--analyze --srcDir ./src/test/resources/cli".split(" ");

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // main-method should run without exceptions
    assertThat(outContent.toString()).isEmpty();
    // assertThat(errContent.toString()).contains("100%");
    assertThat(errContent.toString()).doesNotContain("ERROR");
  }

  @Test
  @ExpectSystemExitWithStatus(0)
  void testAnalyzeSilent() {
    // given
    String[] args = "--analyze --srcDir ./src/test/resources/cli --silent".split(" ");

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  @ExpectSystemExitWithStatus(1)
  void testAnalyzeError() {
    // given
    String[] args = "--analyze --srcDir fake-dir".split(" ");

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).contains("is not exists");
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  @ExpectSystemExitWithStatus(0)
  void testFormat() {
    // given
    String[] args = "--format --src ./src/test/resources/cli".split(" ");

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    // assertThat(errContent.toString()).contains("100%");
    assertThat(errContent.toString()).doesNotContain("ERROR");
  }

  @Test
  @ExpectSystemExitWithStatus(0)
  void testFormatOneFile() {
    // given
    String[] args = "--format --src ./src/test/resources/cli/test.bsl.txt".split(" ");

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    // assertThat(errContent.toString()).contains("100%");
    assertThat(errContent.toString()).doesNotContain("ERROR");
  }

  @Test
  @ExpectSystemExitWithStatus(0)
  void testFormatTwoFiles() {
    // given
    String[] args = "--format --src ./src/test/resources/cli/test.bsl.txt,./src/test/resources/cli/test.bsl".split(" ");

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    // assertThat(errContent.toString()).contains("100%");
    assertThat(errContent.toString()).doesNotContain("ERROR");
  }

  @Test
  @ExpectSystemExitWithStatus(0)
  void testFormatSilent() {
    // given
    String[] args = "--format --src ./src/test/resources/cli --silent".split(" ");

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  @ExpectSystemExitWithStatus(1)
  void testFormatError() {
    // given
    String[] args = "--format --src fake-dir".split(" ");

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).contains("is not exists");
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  @ExpectSystemExitWithStatus(0)
  void testVersion() {
    // given
    String[] args = {"-v"};

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).startsWith("version:");
    assertThat(errContent.toString()).isEmpty();
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

  @Test
  void testWithoutCommandWithConfig() {
    // проверим, что перешли в команду lsp

    // given
    String[] args = "-c .".split(" ");

    // when
    try {
      BSLLSPLauncher.main(args);
    } catch (RuntimeException ignored) {
      // catch prevented system.exit call
    }

    // then
    assertThat(outContent.toString()).contains("LanguageServerStartCommand");
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  void testWithoutParametersErrorCfg() {
    // given
    String[] args = new String[]{"-c", "src/test/resources/cli/error-trace.json"};

    // when
    BSLLSPLauncher.main(args);

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).contains("Trace log setting must lead to file, not directory");
    assertThat(errContent.toString()).isEmpty();
  }
}