package com.github._1c_syntax.bsl.languageserver.utils;

import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

public class RelatedInformation {

  private RelatedInformation() {
    // Utility class
  }

  public static DiagnosticRelatedInformation create(String uri, Range range, String message) {
    Location location = new Location(uri, range);
    return new DiagnosticRelatedInformation(location, message);
  }
}