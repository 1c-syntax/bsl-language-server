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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import mockit.Mock;
import mockit.MockUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class BSLLSPLauncherTest {

  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;

  @BeforeEach
  void setUpStreams() {
    new MockUp<System>() {
      @Mock
      public void exit(int value) {
        throw new RuntimeException(String.valueOf(value));
      }
    };

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
  void testParseError() {
    // given
    String[] args = new String[]{"--error"};

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("2");

    // then
    assertThat(errContent.toString()).containsIgnoringCase("Unknown option: '--error'");
  }

  @Test
  void testAnalyze() {
    // given
    String[] args = "--analyze --srcDir ./src/test/resources/cli".split(" ");

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("0");

    // then
    // main-method should run without exceptions
    assertThat(outContent.toString()).isEmpty();
    // assertThat(errContent.toString()).contains("100%");
    assertThat(errContent.toString()).doesNotContain("ERROR");
  }

  @Test
  void testAnalyzeSilent() {
    // given
    String[] args = "--analyze --srcDir ./src/test/resources/cli --silent".split(" ");

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("0");

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  void testAnalyzeError() {
    // given
    String[] args = "--analyze --srcDir fake-dir".split(" ");

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("1");

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).contains("is not exists");
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  void testFormat() {
    // given
    String[] args = "--format --src ./src/test/resources/cli".split(" ");

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("0");

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    // assertThat(errContent.toString()).contains("100%");
    assertThat(errContent.toString()).doesNotContain("ERROR");
  }

  @Test
  void testFormatOneFile() {
    // given
    String[] args = "--format --src ./src/test/resources/cli/test.bsl.txt".split(" ");

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("0");

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    // assertThat(errContent.toString()).contains("100%");
    assertThat(errContent.toString()).doesNotContain("ERROR");
  }

  @Test
  void testFormatTwoFiles() {
    // given
    String[] args = "--format --src ./src/test/resources/cli/test.bsl.txt,./src/test/resources/cli/test.bsl".split(" ");

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("0");

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    // assertThat(errContent.toString()).contains("100%");
    assertThat(errContent.toString()).doesNotContain("ERROR");
  }

  @Test
  void testFormatSilent() {
    // given
    String[] args = "--format --src ./src/test/resources/cli --silent".split(" ");

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("0");

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).isEmpty();
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  void testFormatError() {
    // given
    String[] args = "--format --src fake-dir".split(" ");

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("1");

    // then
    // main-method should runs without exceptions
    assertThat(outContent.toString()).contains("is not exists");
    assertThat(errContent.toString()).isEmpty();
  }

  @Test
  void testVersion() {
    // given
    String[] args = {"-v"};

    // when-then
    assertThatThrownBy(() -> BSLLSPLauncher.main(args))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("0");

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
    BSLLSPLauncher.main(args);

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