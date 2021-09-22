package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.*;
import com.github._1c_syntax.bsl.parser.*;
import com.github._1c_syntax.utils.*;
import org.antlr.v4.runtime.tree.*;

import java.util.*;
import java.util.regex.*;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR
  }

)
public class MetadataBordersDiagnostic extends AbstractVisitorDiagnostic {

  private static final String METADATA_BORDERS_DEFAULT = "";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = ""
  )
  private Map<String, String> metadataBordersParameters = MapFromJSON(METADATA_BORDERS_DEFAULT);

  private static HashMap<String, String> MapFromJSON(String userSettings) {
    try {
      return new ObjectMapper().readValue(userSettings, HashMap.class);
    } catch (JsonProcessingException e) {
      return new HashMap<>();
    }
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    this.metadataBordersParameters = MapFromJSON( (String) configuration.getOrDefault("metadataBordersParameters", METADATA_BORDERS_DEFAULT));
  }

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx){

    for (Map.Entry<String, String> entry: metadataBordersParameters.entrySet()) {

      if (entry.getKey().trim().length() == 0) {
        continue;
      }

      Pattern pattern = CaseInsensitivePattern.compile(entry.getKey());
      Matcher matcher = pattern.matcher(ctx.getText());
      while (matcher.find()) {
        diagnosticStorage.addDiagnostic(ctx);
      }
    }
    return super.visitStatement(ctx);
  }
}
