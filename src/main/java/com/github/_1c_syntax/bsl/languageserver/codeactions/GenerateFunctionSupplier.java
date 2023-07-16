package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GenerateFunctionSupplier implements CodeActionSupplier {

  final private ReferenceResolver referenceResolver;

  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {

    var start = params.getRange().getStart();
    if (start == null) {
      return Collections.emptyList();
    }

    var parseTree = documentContext.getAst();
    var node = Trees.findTerminalNodeContainsPosition(parseTree, start);

    if(nodeIsMethod(node) && (referenceResolver.findReference(documentContext.getUri(), start).isEmpty())){
      return codeActions(documentContext, parseTree, node);
    }

    return Collections.emptyList();
  }

  private List<CodeAction> codeActions(DocumentContext documentContext, BSLParser.FileContext parseTree, Optional<TerminalNode> node){

    var position = getNewMethodPosition(parseTree);
    var methodName = node.get().getText();

    var funcCodeAction = codeAction(documentContext, position, "Generate function", getMethodContent(methodName, true));
    var procCodeAction = codeAction(documentContext, position, "Generate procedure", getMethodContent(methodName, false));

    var codeActions = List.of(funcCodeAction);
   // codeActions.add(procCodeAction);

    return codeActions;
  }

  private CodeAction codeAction(DocumentContext documentContext, Range position, String title ,String methodContent){

    TextEdit textEdit = new TextEdit();

    textEdit.setRange(position);
    textEdit.setNewText(methodContent);

    WorkspaceEdit edit = new WorkspaceEdit();

    Map<String, List<TextEdit>> changes = Map.of(documentContext.getUri().toString(), Collections.singletonList(textEdit));
    edit.setChanges(changes);

    var codeAction = new CodeAction(title);
    codeAction.setKind(CodeActionKind.Refactor);
    codeAction.setEdit(edit);

    return codeAction;
  }

  private String getMethodContent(String methodName, boolean isFunction){

    var methodText = String.format(isFunction ? functionTemplate() : procedureTemplate(),methodName);
    return methodText;

  }

  private String functionTemplate(){
    return "%n%nФункция %s()%n%n    //TODO: содержание метода%n%n    Возврат Неопределено;%n%nКонецФункции";
  }

  private String procedureTemplate(){
    return "%n%nПроцедура %s()%n%n    //TODO: содержание метода%n%nКонецПроцедуры";
  }
  private Range getNewMethodPosition(BSLParser.FileContext parseTree){
    return Ranges.create(parseTree.getStop());
  }

  private boolean nodeIsMethod(Optional<TerminalNode> node){
    return node.map(TerminalNode::getParent)
                .filter(BSLParser.MethodNameContext.class::isInstance)
                .isPresent();
  }
}
