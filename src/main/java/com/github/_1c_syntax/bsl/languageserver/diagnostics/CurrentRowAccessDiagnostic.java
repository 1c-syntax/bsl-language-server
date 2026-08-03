package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.*;
import com.github._1c_syntax.bsl.parser.BSLParser.AccessPropertyContext;
import org.antlr.v4.runtime.tree.ParseTree;

@DiagnosticMetadata(type=DiagnosticType.CODE_SMELL, severity=DiagnosticSeverity.MAJOR,
  scope=DiagnosticScope.BSL, minutesToFix=2,
  tags={DiagnosticTag.PERFORMANCE, DiagnosticTag.BADPRACTICE})
public class CurrentRowAccessDiagnostic extends AbstractVisitorDiagnostic {

  @Override public ParseTree visitAccessProperty(AccessPropertyContext ctx) {
    var id = ctx.IDENTIFIER();
    if (id == null) return ctx;
    if (!"ТекущаяСтрока".equalsIgnoreCase(id.getText())
      && !"CurrentRow".equalsIgnoreCase(id.getText())) return ctx;

    var ci = ctx.getParent().getParent();
    if (ci != null && ci.getChildCount() >= 3) {
      diagnosticStorage.addDiagnostic(id, info.getMessage("ТекущиеДанные", "CurrentData"));
    }
    return ctx;
  }
}
