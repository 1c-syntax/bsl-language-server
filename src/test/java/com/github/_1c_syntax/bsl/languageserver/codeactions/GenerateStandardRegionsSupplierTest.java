package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateStandardRegionsSupplierTest {

  @Test
  void testGetCodeActions() {

    // given
    String filePath = "./src/test/resources/suppliers/generateRegion.bsl";
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(filePath);

    final LanguageServerConfiguration configuration = LanguageServerConfiguration.create();
    DiagnosticSupplier diagnosticSupplier = new DiagnosticSupplier(configuration);
    QuickFixSupplier quickFixSupplier = new QuickFixSupplier(diagnosticSupplier);
    DiagnosticProvider diagnosticProvider = new DiagnosticProvider(diagnosticSupplier);
    List<Diagnostic> diagnostics = new ArrayList<>();

    CodeActionProvider codeActionProvider = new CodeActionProvider(diagnosticProvider, quickFixSupplier);

    CodeActionParams params = new CodeActionParams();
    TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();

    codeActionContext.setDiagnostics(diagnostics);

    params.setRange(new Range());
    params.setTextDocument(textDocumentIdentifier);
    params.setContext(codeActionContext);

    // when
    List<Either<Command, CodeAction>> codeActions = codeActionProvider.getCodeActions(params, documentContext);
    String regionName = "#Область СлужебныеПроцедурыИФункции\\r\\n#КонецОбласти\\r\\n";

    System.out.println(codeActions.get(0).getRight().getEdit().getChanges());

    assertThat(codeActions)
      .hasSize(1)
      .extracting(Either::getRight)
      .anyMatch(codeAction -> codeAction.getTitle().equals("Generate missing regions"));
  }
}