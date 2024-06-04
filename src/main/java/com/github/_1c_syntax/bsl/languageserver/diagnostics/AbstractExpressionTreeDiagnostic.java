package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeVisitor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.Getter;
import lombok.Setter;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Diagnostic;

import java.util.List;

/**
 * Диагностика, анализирующая выражения BSL и предоставляющая для этого Expression Tree
 */
public abstract class AbstractExpressionTreeDiagnostic extends ExpressionTreeVisitor implements BSLDiagnostic {
  @Getter
  @Setter
  protected DiagnosticInfo info;
  protected final DiagnosticStorage diagnosticStorage = new DiagnosticStorage(this);
  protected DocumentContext documentContext;

  @Override
  public final List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    this.documentContext = documentContext;
    diagnosticStorage.clearDiagnostics();

    var expressionTreeBuilder = new ExpressionTreeBuilder();
    expressionTreeBuilder.visitFile(documentContext.getAst());

    return diagnosticStorage.getDiagnostics();
  }

  /**
   * При входе в выражение вызывается данный метод.
   * Переопределяя его можно оценить - имеет ли смысл строить дерево выражения, или данное выражение не подходит.
   * Позволяет сократить время на построение дерева, если это не требуется для данного AST.
   * @param ctx - выражение, которое в данный момент посещается.
   * @return
   *   - если надо прекратить обход в глубину и построить Expression Tree на данном выражении - надо вернуть ACCEPT
   *   - если надо пройти дальше и посетить дочерние выражения, не затрагивая данное - надо вернуть VISIT_CHILDREN
   *   - если надо пропустить выражение, не ходить глубже и не строить Expression Tree - надо вернуть SKIP
   */
  protected ExpressionVisitorDecision onExpressionEnter(BSLParser.ExpressionContext ctx) {
    return ExpressionVisitorDecision.ACCEPT;
  }

  /**
   * Стратегия по построению дерева выражения на основе выражения AST
   */
  protected enum ExpressionVisitorDecision {

    /**
     * Не обрабатывать выражение
     */
    SKIP,

    /**
     * Обработать данное выражение (построить для него ExpressionTree)
     */
    ACCEPT,

    /**
     * Пропустить данное выражение и обойти вложенные в него выражения
     */
    VISIT_CHILDREN;
  }

  private class ExpressionTreeBuilder extends ExpressionTreeBuildingVisitor {
    @Override
    public ParseTree visitExpression(BSLParser.ExpressionContext ctx) {

      var result = onExpressionEnter(ctx);
      return switch (result) {
        case SKIP -> ctx;
        case ACCEPT -> {
          super.visitExpression(ctx);
          var expressionTree = getExpressionTree();
          if (expressionTree != null) // нашлись выражения в предложенном файле
            visitTopLevelExpression(expressionTree);

          yield ctx;
        }
        case VISIT_CHILDREN -> super.visitChildren(ctx);
      };
    }
  }

}
