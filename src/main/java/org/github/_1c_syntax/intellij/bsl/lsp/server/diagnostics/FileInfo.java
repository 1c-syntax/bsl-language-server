package org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics;

import org.eclipse.lsp4j.Diagnostic;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class FileInfo {
  private Path path;
  private Diagnostic[] diagnostics;

  public FileInfo(Path path, List<Diagnostic> diagnostics) {
    this.path = path;
    this.diagnostics = diagnostics.toArray(new Diagnostic[]{});
  }

  public Path getPath() {
    return path;
  }

  public Diagnostic[] getDiagnostics() {
    return diagnostics;
  }

  @Override
  public String toString() {
    return "FileInfo{" +
      "path=" + path +
      ", diagnostics=" + Arrays.asList(diagnostics) +
      '}';
  }

}
