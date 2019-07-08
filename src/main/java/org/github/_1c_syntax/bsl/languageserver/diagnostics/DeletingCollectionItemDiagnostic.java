package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLParser;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5
)
public class DeletingCollectionItemDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {
    diagnosticStorage.addDiagnostic(ctx);
    return super.visitForEachStatement(ctx);
  }


}
