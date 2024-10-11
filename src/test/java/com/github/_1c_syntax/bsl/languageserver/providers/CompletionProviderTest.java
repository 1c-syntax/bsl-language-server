package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import jakarta.annotation.PostConstruct;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class CompletionProviderTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Autowired
  private ServerContext serverContext;

  private static final String PATH_TO_FILE = "./src/test/resources/providers/completion.os";

  @PostConstruct
  void prepareServerContext() {
    serverContext.setConfigurationRoot(Paths.get("src/test/resources/metadata/oslib"));
    serverContext.populateContext();
  }

  @Test
  void completionAfterDotOnOSClass() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new CompletionParams();
    params.setPosition(new Position(3, 13));

    // when
    var completions = completionProvider.getCompletions(documentContext, params);

    // then
    assertTrue(completions.isRight());

    var completionList = completions.getRight();
    assertThat(completionList.getItems()).hasSize(1);
    assertThat(completionList.getItems().get(0).getLabel()).isEqualTo("NewObject");
    assertThat(completionList.getItems().get(0).getKind()).isEqualTo(CompletionItemKind.Method);
  }

}