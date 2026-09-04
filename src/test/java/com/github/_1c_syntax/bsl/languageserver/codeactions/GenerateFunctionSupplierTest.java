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
import org.eclipse.lsp4j.TextEdit;
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
    params.setRange(Ranges.create(14 , 5, 24));
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(2)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Generate function"))
      .anyMatch(codeAction -> codeAction.getTitle().equals("Generate procedure"));
  }

  @Test
  void testCodeActionPositionFromBodyEmpty() {
    // given
    configuration.setLanguage(Language.EN);

    String filePath = "./src/test/resources/suppliers/GenerateFunctionSupplier_Empty.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    CodeActionParams params = new CodeActionParams();
    params.setRange(Ranges.create(6 , 1, 19));
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat((((List<?>) (codeActions.get(0).getEdit().getChanges().values()).toArray()[0])))
      .allMatch(textedit -> ((TextEdit) textedit).getRange().getStart().getLine() == 0)
    ;

  }

  @Test
  void testCodeActionPositionFromBodyUse() {
    // given
    configuration.setLanguage(Language.EN);

    String filePath = "./src/test/resources/suppliers/GenerateFunctionSupplier_Use.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    CodeActionParams params = new CodeActionParams();
    params.setRange(Ranges.create(9 , 1, 19));
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat((((List<?>) (codeActions.get(0).getEdit().getChanges().values()).toArray()[0])))
      .allMatch(textedit -> ((TextEdit) textedit).getRange().getStart().getLine() == 2)
    ;

  }

  @Test
  void testCodeActionPositionFromBodyVars() {
    // given
    configuration.setLanguage(Language.EN);

    String filePath = "./src/test/resources/suppliers/GenerateFunctionSupplier_UseAndVars.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    CodeActionParams params = new CodeActionParams();
    params.setRange(Ranges.create(12 , 1, 19));
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat((((List<?>) (codeActions.get(0).getEdit().getChanges().values()).toArray()[0])))
      .allMatch(textedit -> ((TextEdit) textedit).getRange().getStart().getLine() == 5)
    ;

  }

  @Test
  void testCodeActionPositionFromBodyMethod() {
    // given
    configuration.setLanguage(Language.EN);

    String filePath = "./src/test/resources/suppliers/GenerateFunctionSupplier.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    CodeActionParams params = new CodeActionParams();
    params.setRange(Ranges.create(24 , 1, 20));
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat((((List<?>) (codeActions.get(0).getEdit().getChanges().values()).toArray()[0])))
      .allMatch(textedit -> ((TextEdit) textedit).getRange().getStart().getLine() == 21)
    ;

  }

  @Test
  void testCodeActionPositionFromMethod() {
    // given
    configuration.setLanguage(Language.EN);

    String filePath = "./src/test/resources/suppliers/GenerateFunctionSupplier.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    CodeActionParams params = new CodeActionParams();
    params.setRange(Ranges.create(16 , 5, 24));
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat((((List<?>) (codeActions.get(0).getEdit().getChanges().values()).toArray()[0])))
      .allMatch(textedit -> ((TextEdit) textedit).getRange().getStart().getLine() == 21)
    ;

  }

  @Test
  void testCodeActionHasParams() {
    // given
    configuration.setLanguage(Language.EN);

    String filePath = "./src/test/resources/suppliers/GenerateFunctionSupplier.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    CodeActionParams params = new CodeActionParams();
    params.setRange(Ranges.create(16 , 5, 24));
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat((((List<?>) (codeActions.get(0).getEdit().getChanges().values()).toArray()[0])))
      .allMatch(textedit -> ((TextEdit) textedit).getNewText().indexOf("Параметр, ВторойПараметр") > 0)
    ;
    assertThat((((List<?>) (codeActions.get(1).getEdit().getChanges().values()).toArray()[0])))
      .allMatch(textedit -> ((TextEdit) textedit).getNewText().indexOf("Параметр, ВторойПараметр") > 0)
    ;
  }

  @Test
  void testGetNoCodeActionOnExistsMethod() {
    // given
    configuration.setLanguage(Language.EN);

    String filePath = "./src/test/resources/suppliers/GenerateFunctionSupplier.bsl";
    var documentContext = TestUtils.getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = new ArrayList<>();

    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);

    CodeActionParams params = new CodeActionParams();
    params.setRange(Ranges.create(12 , 5, 22));
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<CodeAction> codeActions = codeActionSupplier.getCodeActions(params, documentContext);

    assertThat(codeActions)
      .hasSize(0);
  }

}
