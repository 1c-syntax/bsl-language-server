package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class CognitiveComplexityInlayHintSupplierTest {

  private final static String FILE_PATH = "./src/test/resources/inlayhints/CognitiveComplexityInlayHintSupplier.bsl";

  @Autowired
  private CognitiveComplexityInlayHintSupplier supplier;

  @Test
  void testGetInlayHints() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    MethodSymbol firstMethod = documentContext.getSymbolTree().getMethods().get(0);
    var methodName = firstMethod.getName();

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = Ranges.create();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints).isEmpty();

    // when
    supplier.toggleHints(documentContext.getUri(), methodName);
    inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .hasSize(1)
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("+1"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(1, 4));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
      });
  }

}