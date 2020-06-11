/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Regions;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GenerateStandardRegionsSupplier implements CodeActionSupplier{

  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {
    List<CodeAction> codeActions = new ArrayList<>();

    ModuleType moduleType = documentContext.getModuleType();
    Set<String> neededStandardRegions = Regions.getStandardRegionsNamesByModuleType(moduleType, Language.RU);
    Set<String> documentRegionsNames = documentContext.getSymbolTree().getModuleLevelRegions().stream()
      .map(RegionSymbol::getName)
      .collect(Collectors.toSet());
    neededStandardRegions.removeAll(documentRegionsNames);

    if (neededStandardRegions.isEmpty()) {
      return codeActions;
    }

    String regionFormat = "#Область %s%n#КонецОбласти%n";
    String result = neededStandardRegions.stream()
      .map(s -> String .format(regionFormat, s))
      .collect(Collectors.joining("\n"));
    TextEdit textEdit = new TextEdit(params.getRange(), result);

    WorkspaceEdit edit = new WorkspaceEdit();
    Map<String, List<TextEdit>> changes = new HashMap<>();
    changes.put(documentContext.getUri().toString(), Collections.singletonList(textEdit));
    edit.setChanges(changes);

    CodeAction codeAction = new CodeAction("Generate missing regions");
    codeAction.setDiagnostics(new ArrayList<>());
    codeAction.setKind(CodeActionKind.Refactor);
    codeAction.setEdit(edit);
    codeActions.add(codeAction);

    return codeActions;
  }
}
