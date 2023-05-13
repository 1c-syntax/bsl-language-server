package com.github._1c_syntax.bsl.languageserver.commands;

import com.github._1c_syntax.bsl.languageserver.commands.complexity.ToggleComplexityInlayHintsCommandArguments;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.inlayhints.CyclomaticComplexityInlayHintSupplier;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ToggleCyclomaticComplexityInlayHintsCommandSupplierTest {
  private final static String FILE_PATH = "./src/test/resources/commands/ToggleCyclomaticComplexityInlayHintsCommandSupplier.bsl";

  @MockBean
  private CyclomaticComplexityInlayHintSupplier complexityInlayHintSupplier;

  @Autowired
  private ToggleCyclomaticComplexityInlayHintsCommandSupplier supplier;

  @Test
  void testExecute() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    MethodSymbol firstMethod = documentContext.getSymbolTree().getMethods().get(0);

    var methodName = firstMethod.getName();

    var arguments = new ToggleComplexityInlayHintsCommandArguments(
      documentContext.getUri(),
      supplier.getId(),
      methodName
    );

    // when
    Optional<Object> result = supplier.execute(arguments);

    // then
    assertThat(result).isEmpty();
    verify(complexityInlayHintSupplier, times(1)).toggleHints(documentContext.getUri(), methodName);

  }

}
