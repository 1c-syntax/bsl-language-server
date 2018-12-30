package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.reporter;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import java.util.HashMap;
import java.util.Map;

class TSLintReportEntry {
  private Position endPosition;
  private String failure;
  private String name;
  private String ruleName;
  private String ruleSeverity;
  private Position startPosition;

  private static Map<DiagnosticSeverity, String> severityMap = new HashMap<>();

  static {
    severityMap.put(DiagnosticSeverity.Error, "error");
    severityMap.put(DiagnosticSeverity.Hint, "warn");
    severityMap.put(DiagnosticSeverity.Information, "warn");
    severityMap.put(DiagnosticSeverity.Warning, "warn");
  }

  TSLintReportEntry(String fileName, Diagnostic diagnostic) {
    endPosition = new Position(diagnostic.getRange().getStart());
    failure = diagnostic.getMessage();
    name = fileName;
    ruleName = diagnostic.getCode();
    ruleSeverity = severityMap.get(diagnostic.getSeverity());
    startPosition = new Position(diagnostic.getRange().getEnd());
  }

  public Position getEndPosition() {
    return endPosition;
  }

  public String getFailure() {
    return failure;
  }

  public String getName() {
    return name;
  }

  public String getRuleName() {
    return ruleName;
  }

  public String getRuleSeverity() {
    return ruleSeverity;
  }

  public Position getStartPosition() {
    return startPosition;
  }

  static class Position {
    private int character;
    private int line;
    private int position;

    Position(org.eclipse.lsp4j.Position position) {
      line = position.getLine();
      character = position.getCharacter();
      this.position = position.getCharacter();
    }

    public int getCharacter() {
      return character;
    }

    public int getLine() {
      return line;
    }

    public int getPosition() {
      return position;
    }

  }

}
