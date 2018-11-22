package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.github._1c_syntax.intellij.bsl.lsp.server.FileInfo;
import org.github._1c_syntax.intellij.bsl.lsp.server.utils.RangeHelper;
import org.github._1c_syntax.parser.BSLLexer;
import org.github._1c_syntax.parser.BSLParser;
import org.github._1c_syntax.parser.BSLParserBaseVisitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FunctionShouldHaveReturnDiagnostic extends BSLParserBaseVisitor<ParseTree> implements BSLDiagnostic {
  private FileInfo fileInfo;
  private List<Diagnostic> diagnostics = new ArrayList<>();

  @Override
  public List<Diagnostic> getDiagnostics() {
    this.visitFile(fileInfo.getTree());
    return diagnostics;
  }

  @Override
  public void setFileInfo(FileInfo fileInfo) {
    this.fileInfo = fileInfo;
    diagnostics.clear();
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {

    Collection<ParseTree> tokens = Trees.findAllTokenNodes(ctx, BSLLexer.RETURN_KEYWORD);
    if (tokens.isEmpty()) {
      BSLParser.SubNameContext subName = ctx.funcDeclaration().subName();
      Diagnostic diagnostic = new Diagnostic(
        RangeHelper.newRange(subName.getStart(), subName.getStop()),
        "Фукция не содержит Возврат",
        DiagnosticSeverity.Error,
        DiagnosticProvider.SOURCE
      );
      diagnostics.add(diagnostic);
    }
    return ctx;
  }
}
