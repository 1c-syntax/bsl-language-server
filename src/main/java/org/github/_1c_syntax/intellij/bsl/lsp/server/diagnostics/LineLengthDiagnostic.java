package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.github._1c_syntax.intellij.bsl.lsp.server.FileInfo;
import org.github._1c_syntax.intellij.bsl.lsp.server.utils.RangeHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LineLengthDiagnostic implements BSLDiagnostic {

  private FileInfo fileInfo;

  public LineLengthDiagnostic() {
  }

  public Collection<Diagnostic> getDiagnostics() {
    List<Token> tokens = fileInfo.getTokens();
    List<Diagnostic> diagnostics = new ArrayList<>();

    Map<Integer, List<Integer>> tokensInOneLine = new HashMap<>();
    tokens.forEach((Token token) -> {
        List<Integer> tokenList = tokensInOneLine.getOrDefault(token.getLine(), new ArrayList<>());
        tokenList.add(token.getCharPositionInLine());
        tokensInOneLine.put(token.getLine() - 1, tokenList);
      });

    tokensInOneLine.forEach((key, value) -> {
      Optional<Integer> max = value.stream().max(Integer::compareTo);
      Integer maxCharPosition = max.orElse(0);
      if (maxCharPosition > 120) {
        Diagnostic diagnostic = new Diagnostic(
          RangeHelper.newRange(key, 0, key, maxCharPosition),
          "Превышена длина строки",
          DiagnosticSeverity.Error,
          DiagnosticProvider.SOURCE
        );
        diagnostics.add(diagnostic);
      }
    });

    return diagnostics;
  }

  @Override
  public void setFileInfo(FileInfo fileInfo) {
    this.fileInfo = fileInfo;
  }

}
