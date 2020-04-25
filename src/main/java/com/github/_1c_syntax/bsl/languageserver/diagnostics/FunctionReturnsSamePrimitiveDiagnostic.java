package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.checkerframework.checker.nullness.Opt;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;

import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  }

)
public class FunctionReturnsSamePrimitiveDiagnostic extends AbstractVisitorDiagnostic {
  public FunctionReturnsSamePrimitiveDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {
    var tree = Trees.findAllRuleNodes(ctx, BSLParser.RULE_returnStatement);
    if (tree.size() > 1){
      var set = tree.stream()
        .map(BSLParser.ReturnStatementContext.class::cast)
        .map(BSLParser.ReturnStatementContext::expression)
        .flatMap(Stream::ofNullable)
        .map(BSLParser.ExpressionContext::getText)
        .collect(Collectors.toSet());
      if (set.size() == 1) {
        var relatedInformation = tree.stream()
          .map(BSLParser.ReturnStatementContext.class::cast)
          .map(statement ->
            RelatedInformation.create(
              documentContext.getUri(),
              Ranges.create(statement.getStart()),
              info.getMessage()))
          .collect(Collectors.toList());
        diagnosticStorage.addDiagnostic(ctx.funcDeclaration(), info.getMessage(), relatedInformation);
      }
    }
    return ctx;
  }
}
