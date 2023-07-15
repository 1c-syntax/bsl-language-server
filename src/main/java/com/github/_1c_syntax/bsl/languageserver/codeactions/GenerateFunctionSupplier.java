package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GenerateFunctionSupplier implements CodeActionSupplier {

  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {

    var start = params.getRange().getStart();
    if (start == null) {
      return Collections.emptyList();
    }

    var parseTree = documentContext.getAst();

    var node = Trees.findTerminalNodeContainsPosition(parseTree, start);

    if(nodeIsMethod(node) && (nodeIsImplemented(node, parseTree) == false)){

      var symbol = node.get().getSymbol();

      TextEdit textEdit = new TextEdit();

      textEdit.setRange(new Range());
      textEdit.setNewText("generated function");

      WorkspaceEdit edit = new WorkspaceEdit();

      Map<String, List<TextEdit>> changes = Map.of(documentContext.getUri().toString(), Collections.singletonList(textEdit));
      edit.setChanges(changes);

      String title = "Generate function";
      var codeAction = new CodeAction(title);
      codeAction.setKind(CodeActionKind.Refactor);
      codeAction.setEdit(edit);
      return List.of(codeAction);
    }

    return Collections.emptyList();
  }

  private boolean nodeIsImplemented(Optional<TerminalNode> node, BSLParser.FileContext parseTree) {
    return false;
  }

  private boolean nodeIsMethod(Optional<TerminalNode> node){

    return node
            .map(TerminalNode::getParent)
            .filter(BSLParser.MethodNameContext.class::isInstance)
            .isPresent();
  }
}
