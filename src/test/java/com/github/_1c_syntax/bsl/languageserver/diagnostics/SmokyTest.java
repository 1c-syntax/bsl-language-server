package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus;
import com.github._1c_syntax.bsl.languageserver.BSLLSPLauncher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SmokyTest {

  @Test
  @ExpectSystemExitWithStatus(0)
  void test() {

    String[] args = new String[]{"--analyze", "--srcDir", "./src/test/resources/diagnostics"};

    BSLLSPLauncher.main(args);

    assertThat(true).isTrue(); // TODO что проверять?

  }
}
