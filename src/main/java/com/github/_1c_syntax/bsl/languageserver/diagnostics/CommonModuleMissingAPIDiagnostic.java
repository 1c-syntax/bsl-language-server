package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.mdclasses.mdo.CommonModule;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.CommonModule
  },
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.SUSPICIOUS
  }

)
public class CommonModuleMissingAPIDiagnostic extends AbstractDiagnostic {

  private static final Pattern REGION_NAME = CaseInsensitivePattern.compile(
    "^(?:ПрограммныйИнтерфейс|СлужебныйПрограммныйИнтерфейс|Public|Internal)$"
  );

  @Override
  protected void check() {

    var symbolTree = documentContext.getSymbolTree();

    var moduleMethods = symbolTree.getMethods();
    if (moduleMethods.stream().count() == 0) {
      return;
    }

    var isModuleWithoutExportSub = moduleMethods
      .stream()
      .filter(MethodSymbol -> MethodSymbol.isExport())
      .findFirst()
      .isEmpty();

    var isModuleWithoutRegionAPI = symbolTree.getModuleLevelRegions()
      .stream()
      .filter(regionSymbol -> REGION_NAME.matcher(regionSymbol.getName()).matches())
      .findFirst()
      .isEmpty();

    var moduleRange = symbolTree.getModule().getRange();
    if (isModuleWithoutExportSub || isModuleWithoutRegionAPI) {
      diagnosticStorage.addDiagnostic(moduleRange, info.getMessage());
    }

//    if (isModuleWithoutExportSub) {
//      diagnosticStorage.addDiagnostic(moduleRange, "Модуль должен иметь хотя бы один экспортный метод.");
//    }
//
//    if (isModuleWithoutRegionAPI) {
//      diagnosticStorage.addDiagnostic(moduleRange, "Модуль должен иметь область API (ПрограммныйИнтерфейс или СлужебныйПрограммныйИнтерфейс).");
//    }

  }

}
