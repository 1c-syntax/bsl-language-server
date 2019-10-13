package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.utils.MultilingualStringParser;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;

public abstract class AbstractMultilingualStringDiagnostic extends AbstractVisitorDiagnostic {

  private static final String DECLARED_LANGUAGES_DEFAULT = "ru";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DECLARED_LANGUAGES_DEFAULT,
    description = "Заявленные языки"
  )
  private String declaredLanguages = DECLARED_LANGUAGES_DEFAULT;
  protected MultilingualStringParser parser;

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }

    declaredLanguages = (String) configuration.get("declaredLanguages");
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    try {
      parser = new MultilingualStringParser(ctx, declaredLanguages);
    } catch (IllegalArgumentException e) {
      return super.visitGlobalMethodCall(ctx);
    }
    if(check()) {
      diagnosticStorage.addDiagnostic(ctx, getDiagnosticMessage(parser.getMissingLanguages()));
    }
    return super.visitGlobalMethodCall(ctx);
  }

  protected boolean check() {
    return false;
  }

}
