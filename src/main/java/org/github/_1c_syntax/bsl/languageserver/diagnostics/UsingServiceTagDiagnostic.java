package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLLexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO
)
public class UsingServiceTagDiagnostic extends AbstractVisitorDiagnostic{

  private static final String SERVICE_TAGS_DEFAULT = "todo|fixme";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + SERVICE_TAGS_DEFAULT,
    description = "Служебные теги"
  )
  private String serviceTags = SERVICE_TAGS_DEFAULT;

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }
    serviceTags = (String) configuration.get("maxLineLength");
  }

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {

    final Pattern pattern = Pattern.compile(
      "\\s+(" + serviceTags + ")",
      Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    List<Diagnostic> diagnostics = new ArrayList<>();
    for (Token token : documentContext.getTokens())
    {
      if (token.getType() == BSLLexer.LINE_COMMENT
        && pattern.matcher(token.getText().toLowerCase(Locale.getDefault())).find())
      {
        diagnostics.add(BSLDiagnostic.createDiagnostic(
          this,
          RangeHelper.newRange(token),
          getDiagnosticMessage()));
      }
    }
    return diagnostics;
  }
}
