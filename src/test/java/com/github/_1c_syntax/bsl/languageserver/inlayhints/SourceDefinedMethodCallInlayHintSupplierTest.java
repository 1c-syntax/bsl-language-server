package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
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
class SourceDefinedMethodCallInlayHintSupplierTest {

  private final static String FILE_PATH = "./src/test/resources/inlayhints/SourceDefinedMethodCallInlayHintSupplier.bsl";

  @Autowired
  private SourceDefinedMethodCallInlayHintSupplier supplier;

  @Test
  void testDefaultInlayHints() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    MethodSymbol firstMethod = documentContext.getSymbolTree().getMethods().get(0);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var range = firstMethod.getRange();
    var params = new InlayHintParams(textDocumentIdentifier, range);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .hasSize(2)
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("PlayersHealth:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 23));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **PlayersHealth**: ");
      })
      .anySatisfy(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft("Amount:"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Parameter);
        assertThat(inlayHint.getPosition()).isEqualTo(new Position(3, 32));
        assertThat(inlayHint.getPaddingRight()).isTrue();
        assertThat(inlayHint.getPaddingLeft()).isNull();
        assertThat(inlayHint.getTooltip().getRight().getValue()).isEqualTo("* **Amount**: ");
      })
    ;
  }

}