package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Optional;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1
)
public class NonExportMethodsInApiRegionDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern REGION_NAME = Pattern.compile(
    "^(?:ПрограммныйИнтерфейс|СлужебныйПрограмныйИнтерфейс|Public|Internal)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {

    Optional<MethodSymbol> methodSymbolOption = documentContext.getMethodSymbol(ctx);

    if (!methodSymbolOption.isPresent()) {
      return ctx;
    }

    MethodSymbol methodSymbol = methodSymbolOption.get();

    if (methodSymbol.isExport()) {
      return ctx;
    }

    RegionSymbol methodRegion = methodSymbol.getRegion();
    if (methodRegion == null) {
      return ctx;
    }

    documentContext.getRegions()
      .stream()
      .filter(regionSymbol -> findRecursivelyRegion(regionSymbol, methodRegion))
      .filter(regionSymbol -> REGION_NAME.matcher(regionSymbol.getName()).matches())
      .findFirst()
      .ifPresent(regionSymbol -> {
        String message = getDiagnosticMessage(methodSymbol.getName(), regionSymbol.getName());
        diagnosticStorage.addDiagnostic(methodSymbol.getSubNameRange(), message);
      });

    return ctx;
  }

  private boolean findRecursivelyRegion(RegionSymbol parent, RegionSymbol toFind) {
    if (parent.equals(toFind)) {
      return true;
    }

    return parent.getChildren().stream().anyMatch(regionSymbol -> findRecursivelyRegion(regionSymbol, (toFind)));
  }

}
