package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
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
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

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

    var methodContext = ((BSLParser.GlobalMethodCallContext) node.get().getParent().getParent());
    var methodName = methodContext.methodName().getText();
    var methodParams = getMethodParams(methodContext);

    // TODO: Корректное позиционирование - после текущего метода, после переменных, после инклудов, в начале.
    var position = getNewMethodPosition(documentContext, methodContext);

    // TODO: Двуязычность.
    var funcCodeAction = codeAction(documentContext, position, "Generate function", getMethodContent(methodName, methodParams, true));
    var procCodeAction = codeAction(documentContext, position, "Generate procedure", getMethodContent(methodName, methodParams, false));

    return List.of(funcCodeAction, procCodeAction);
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

  private String getMethodParams(BSLParser.GlobalMethodCallContext methodContext){

    var callParams = methodContext.doCall().callParamList().callParam();
    var joiner = new StringJoiner(", ");

    for (BSLParser.CallParamContext callParam: callParams) {
      joiner.add(callParam.getText());
    }

    return joiner.toString();
  }

  private String getMethodContent(String methodName, String methodParams, boolean isFunction){

    return String.format(isFunction ? functionTemplate() : procedureTemplate(), methodName, methodParams);

  }

  private String functionTemplate(){
    return "%nФункция %s(%s)%n%n    //TODO: содержание метода%n%n    Возврат Неопределено;%n%nКонецФункции%n";
  }

  private String procedureTemplate(){
    return "%nПроцедура %s(%s)%n%n    //TODO: содержание метода%n%nКонецПроцедуры%n";
  }
  private Range getNewMethodPosition(DocumentContext documentContext, BSLParser.GlobalMethodCallContext methodContext){

    var ancestorRuleSub = Trees.getAncestorByRuleIndex(methodContext, BSLParser.RULE_sub);

    if (ancestorRuleSub != null){
      var line = ancestorRuleSub.getStop().getLine();
      return Ranges.create(line, 1, line, 1);
    }

    var methods = documentContext.getSymbolTree().getMethods();

    if (!methods.isEmpty()){
      var lastMethod = methods.get(methods.size() - 1);
      var line = lastMethod.getRange().getEnd().getLine() + 1;
      return Ranges.create(line, 0, line, 0);
    }

    var variables = documentContext.getSymbolTree().getVariables()
      .stream().filter(e -> e.getKind() == VariableKind.MODULE).toList();

    if (!variables.isEmpty()){
      var lastVariable = variables.get(variables.size() - 1);
      var line = lastVariable.getRange().getEnd().getLine() + 1;
      return Ranges.create(line, 0, line, 0);
    }

    var annotations = documentContext.getAst().moduleAnnotations();
    if (annotations != null) {
      var uses = annotations.use();

      if (!uses.isEmpty()) {
        var lastUse = uses.get(uses.size() - 1);
        var line = lastUse.stop.getLine();
        return Ranges.create(line, 0, line, 0);
      }
    }

    return Ranges.create(0, 0, 0, 0);

  }

  private boolean nodeIsMethod(Optional<TerminalNode> node){
    return node.map(TerminalNode::getParent)
                .filter(BSLParser.MethodNameContext.class::isInstance)
                .isPresent();
  }
}
