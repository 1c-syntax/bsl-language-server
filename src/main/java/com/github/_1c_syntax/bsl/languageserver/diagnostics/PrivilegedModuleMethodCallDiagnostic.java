/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.SECURITY_HOTSPOT,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 60,
  tags = {
    DiagnosticTag.SUSPICIOUS
  },
  scope = DiagnosticScope.BSL
)
@RequiredArgsConstructor
public class PrivilegedModuleMethodCallDiagnostic extends AbstractDiagnostic {

  private static final boolean VALIDATE_NESTED_CALLS = true;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + VALIDATE_NESTED_CALLS
  )
  private boolean validateNestedCalls = VALIDATE_NESTED_CALLS;

  private final ReferenceIndex referenceIndex;
  private List<ModuleSymbol> privilegedModuleSymbols = new ArrayList<>();

  @Override
  protected void check() {
    if (privilegedModuleSymbols.isEmpty()) {
      privilegedModuleSymbols = getPrivilegedModuleSymbols();
    }
    if (privilegedModuleSymbols.isEmpty()) {
      return;
    }

    referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method).stream()
      .filter(this::isReferenceToModules)
      .forEach(this::fireIssue);
  }

  private List<ModuleSymbol> getPrivilegedModuleSymbols() {
    return documentContext.getServerContext().getConfiguration().getCommonModules()
      .stream()
      .filter(CommonModule::isPrivileged)
      .flatMap(mdCommonModule -> getPrivilegedModuleSymbol(mdCommonModule).stream())
      .toList();
  }

  private Optional<ModuleSymbol> getPrivilegedModuleSymbol(CommonModule mdCommonModule) {
    return documentContext.getServerContext().getDocument(mdCommonModule.getMdoRef(), ModuleType.CommonModule)
      .map(documentContext1 -> documentContext1.getSymbolTree().getModule());
  }

  private boolean isReferenceToModules(Reference reference) {
    if (!validateNestedCalls && reference.uri().equals(documentContext.getUri())) {
      return false;
    }
    return reference.getSourceDefinedSymbol()
      .flatMap(sourceDefinedSymbol -> sourceDefinedSymbol.getRootParent(SymbolKind.Module))
      .filter(ModuleSymbol.class::isInstance)
      .map(ModuleSymbol.class::cast)
      .filter(privilegedModuleSymbols::contains)
      .isPresent();
  }

  private void fireIssue(Reference reference) {
    diagnosticStorage.addDiagnostic(reference.selectionRange(),
      info.getMessage(reference.symbol().getName()));
  }
}
