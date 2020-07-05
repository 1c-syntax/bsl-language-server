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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Regions;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.mdclasses.metadata.additional.ScriptVariant;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code Supplier} {@code codeAction} для генерации отсутствующих
 * стандартных программных областей
 */
public class GenerateStandardRegionsSupplier implements CodeActionSupplier{

  /**
   * При необходимости создает {@code CodeAction} для генерации отсутствующих
   * стандартных областей 1С
   * @param params параметры вызова генерации {@code codeAction}
   * @param documentContext представление программного модуля
   * @return {@code List<CodeAction>} если модуль не содержит всех стандартных областей,
   * пустой {@code List} если генерация областей не требуется
   */
  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {

    ModuleType moduleType = documentContext.getModuleType();
    FileType fileType = documentContext.getFileType();
    ScriptVariant configurationLanguage = documentContext.getServerContext().getConfiguration().getScriptVariant();
    Set<String> neededStandardRegions;

    if (fileType == FileType.BSL) {
      neededStandardRegions = Regions.getStandardRegionsNamesByModuleType(moduleType, configurationLanguage);
    } else {
      neededStandardRegions = Regions.getOneScriptStandardRegions(configurationLanguage);
    }

    Set<String> documentRegionsNames = documentContext.getSymbolTree().getModuleLevelRegions().stream()
      .map(RegionSymbol::getName)
      .collect(Collectors.toSet());
    neededStandardRegions.removeAll(documentRegionsNames);

    if (neededStandardRegions.isEmpty()) {
      return Collections.emptyList();
    }

    String regionFormat =
      configurationLanguage == ScriptVariant.ENGLISH ? "#Region %s%n%n#EndRegion%n" : "#Область %s%n%n#КонецОбласти%n";

    String result = neededStandardRegions.stream()
      .map(s -> String .format(regionFormat, s))
      .collect(Collectors.joining("\n"));
    TextEdit textEdit = new TextEdit(params.getRange(), result);

    WorkspaceEdit edit = new WorkspaceEdit();
    Map<String, List<TextEdit>> changes = Map.of(documentContext.getUri().toString(),
      Collections.singletonList(textEdit));
    edit.setChanges(changes);

    CodeAction codeAction = new CodeAction("Generate missing regions");
    codeAction.setDiagnostics(new ArrayList<>());
    codeAction.setKind(CodeActionKind.Refactor);
    codeAction.setEdit(edit);
    return List.of(codeAction);
  }
}
