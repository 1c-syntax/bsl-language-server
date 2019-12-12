package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;

import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class EmptyRegionDiagnostic extends AbstractDiagnostic {

  public EmptyRegionDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check(DocumentContext documentContext) {

    documentContext.getMethods();

    List<RegionSymbol> regions = documentContext.getRegionsFlat();
    documentContext.getRegionsFlat()
      .stream()
      .filter(regionSymbol -> regionSymbol.getMethods().isEmpty())
      .map(RegionSymbol::getNode)
      .forEach(diagnosticStorage::addDiagnostic);

  }

}
