package org.github._1c_syntax.bsl.languageserver.configuration.diagnostics;

import lombok.Value;

@Value
public class LineLengthDiagnosticConfiguration implements DiagnosticConfiguration {
  private final int maxLineLength;
}
