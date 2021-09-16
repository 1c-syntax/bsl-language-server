package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.mdo.MDLanguage;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.SUSPICIOUS
  }

)
public class UselessTernaryOperatorDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider {

  @Override
  public ParseTree visitTernaryOperator(BSLParser.TernaryOperatorContext ctx){

    var emptyToken = 0;
    var exp = ctx.expression();
    var condition = getBooleanToken(exp.get(0));
    var trueBranch = getBooleanToken(exp.get(1));
    var falseBranch = getBooleanToken(exp.get(2));

    if (condition != emptyToken) {
      diagnosticStorage.addDiagnostic(ctx);
    } else if (trueBranch == BSLParser.TRUE && falseBranch == BSLParser.FALSE){
      var dgs = diagnosticStorage.addDiagnostic(ctx);
      dgs.ifPresent(diagnostic -> diagnostic.setData(exp.get(0).getText()));
    } else if (trueBranch == BSLParser.FALSE && falseBranch == BSLParser.TRUE){
      var dgs = diagnosticStorage.addDiagnostic(ctx);
      if (dgs.isPresent()) {
        if(documentContext.getServerContext().getConfiguration().getDefaultLanguage() == MDLanguage.ENGLISH) {
          dgs.get().setData("NOT (" + exp.get(0).getText() + ")");
        } else {
          dgs.get().setData("НЕ (" + exp.get(0).getText() + ")");
        }
      }
    } else if (trueBranch != emptyToken){
      diagnosticStorage.addDiagnostic(ctx);
    } else if (falseBranch != emptyToken){
      diagnosticStorage.addDiagnostic(ctx);
    }

    return super.visitTernaryOperator(ctx);
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    List<TextEdit> textEdits = new ArrayList<>();

    diagnostics.forEach((Diagnostic diagnostic) -> {
      var range = diagnostic.getRange();
      var textEdit = new TextEdit(range,(String) diagnostic.getData());
      textEdits.add(textEdit);
    });

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );

  }

  private int getBooleanToken(BSLParser.ExpressionContext expCtx){

    if (expCtx.children.size() == 1){
      var mmbCtx = (BSLParser.MemberContext) expCtx.getChild(0);
      var parseTree = mmbCtx.getChild(0);
      if (parseTree instanceof BSLParser.ConstValueContext){
        var constValue = (BSLParser.ConstValueContext) parseTree;
        var tokenTrue = constValue.getToken(BSLParser.TRUE, 0);
        if (tokenTrue != null) {
          return BSLParser.TRUE;
        }
        var tokenFalse = constValue.getToken(BSLParser.FALSE, 0);
        if (tokenFalse != null) {
          return BSLParser.FALSE;
        }
      }
    }
    return 0;
  }

}
