package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;

//@SpringBootTest
//@CleanupContextBeforeClassAndAfterEachTestMethod
class MissingMethodOfCommonModuleDiagnosticTest extends AbstractDiagnosticTest<MissingMethodOfCommonModuleDiagnostic> {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

//  @Autowired
//  private ServerContext serverContext;

  MissingMethodOfCommonModuleDiagnosticTest() {
    super(MissingMethodOfCommonModuleDiagnostic.class);
  }

//  @BeforeEach
//  void setUp() {
//    initServerContext(Absolute.path(PATH_TO_METADATA));
//  }

  @Test
  void test() {
    initServerContext(Absolute.path(PATH_TO_METADATA));

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics);
    assertThat(diagnostics, true)
      .hasRange(1, 4, 41)
      .hasRange(2, 8, 51)
      .hasRange(3, 4, 44)
      .hasRange(4, 4, 48)
      .hasRange(5, 8, 54)

      .hasRange(11, 4, 56)
      .hasRange(12, 8, 30)
      .hasRange(13, 4, 26)
      .hasRange(14, 4, 26)
      .hasRange(15, 8, 30)
      .hasSize(10);
  }

  @Test
  void testWithoutMetadata() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(0);
  }
}
