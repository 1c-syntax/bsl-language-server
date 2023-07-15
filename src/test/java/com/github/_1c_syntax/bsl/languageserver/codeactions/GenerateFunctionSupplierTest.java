package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class GenerateFunctionSupplierTest {

  @Autowired
  private LanguageServerConfiguration configuration;

  @Autowired
  private GenerateFunctionSupplier codeActionSupplier;

  @Test
  void testGetCodeAction() {
    // given
    configuration.setLanguage(Language.EN);

    String filePath = "./src/test/resources/suppliers/GenerateFunctionSupplier.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    CodeActionParams params = new CodeActionParams();
    //params.setRange(Ranges.create(15 , 5, 34));
    params.setRange(Ranges.create(12 , 5, 27));
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    var cahnges = codeActions.get(0).getEdit().getChanges().values().toArray();

    assertThat(codeActions)
      .hasSize(1)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Generate function"));
  }

}
