package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

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

    var maybeDoCall = Trees.findTerminalNodeContainsPosition(parseTree, start)
      .map(TerminalNode::getParent)
      .filter(BSLParser.TypeNameContext.class::isInstance)
      .map(BSLParser.TypeNameContext.class::cast)
      .filter(DiagnosticHelper::isStructureType)
      .map(BSLParserRuleContext::getParent)
      .map(BSLParser.NewExpressionContext.class::cast)
      .map(BSLParser.NewExpressionContext::doCall);

    return null;
  }
}
