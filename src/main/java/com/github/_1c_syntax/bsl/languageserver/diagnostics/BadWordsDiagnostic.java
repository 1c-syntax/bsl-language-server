package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.*;
import com.github._1c_syntax.utils.*;

import java.util.regex.*;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.DESIGN
  }
)
public class BadWordsDiagnostic extends AbstractDiagnostic {

  private static final String BAD_WORDS_DEFAULT = "";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = BAD_WORDS_DEFAULT
  )
  private String badWords = BAD_WORDS_DEFAULT;

  @Override
  protected void check() {
    
    Pattern pattern = CaseInsensitivePattern.compile(badWords);
    String[] moduleLines = documentContext.getContent().split("\n");

    for (int i=0; i<moduleLines.length; i++ ) {
      Matcher matcher = pattern.matcher(moduleLines[i]);
      while (matcher.find()) {
        diagnosticStorage.addDiagnostic(i, matcher.start(), i, matcher.end());
      }
    }
  }
}
