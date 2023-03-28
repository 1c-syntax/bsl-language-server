/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Regions;
import com.github._1c_syntax.bsl.mdo.support.ScriptVariant;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

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
@Component
public class GenerateStandardRegionsSupplier implements CodeActionSupplier {

  private final LanguageServerConfiguration languageServerConfiguration;

  public GenerateStandardRegionsSupplier(LanguageServerConfiguration languageServerConfiguration) {
    this.languageServerConfiguration = languageServerConfiguration;
  }

  /**
   * При необходимости создает {@code CodeAction} для генерации отсутствующих
   * стандартных областей 1С
   *
   * @param params          параметры вызова генерации {@code codeAction}
   * @param documentContext представление программного модуля
   * @return {@code List<CodeAction>} если модуль не содержит всех стандартных областей,
   * пустой {@code List} если генерация областей не требуется
   */
  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {

    var moduleType = documentContext.getModuleType();
    var fileType = documentContext.getFileType();

    ScriptVariant regionsLanguage = getRegionsLanguage(documentContext, fileType);
    Set<String> neededStandardRegions;

    if (fileType == FileType.BSL) {
      neededStandardRegions = Regions.getStandardRegionsNamesByModuleType(moduleType, regionsLanguage);
    } else {
      neededStandardRegions = Regions.getOneScriptStandardRegions(regionsLanguage);
    }

    Set<String> documentRegionsNames = documentContext.getSymbolTree().getModuleLevelRegions().stream()
      .map(RegionSymbol::getName)
      .collect(Collectors.toSet());
    neededStandardRegions.removeAll(documentRegionsNames);

    if (neededStandardRegions.isEmpty()) {
      return Collections.emptyList();
    }

    String regionFormat =
      regionsLanguage == ScriptVariant.ENGLISH ? "#Region %s%n%n#EndRegion%n" : "#Область %s%n%n#КонецОбласти%n";

    String result = neededStandardRegions.stream()
      .map(s -> String.format(regionFormat, s))
      .collect(Collectors.joining("\n"));
    var textEdit = new TextEdit(calculateFixRange(params.getRange()), result);

    var edit = new WorkspaceEdit();
    Map<String, List<TextEdit>> changes = Map.of(documentContext.getUri().toString(),
      Collections.singletonList(textEdit));
    edit.setChanges(changes);

    var codeAction = new CodeAction("Generate missing regions");
    codeAction.setDiagnostics(new ArrayList<>());
    codeAction.setKind(CodeActionKind.Refactor);
    codeAction.setEdit(edit);
    return List.of(codeAction);
  }

  private ScriptVariant getRegionsLanguage(DocumentContext documentContext, FileType fileType) {

    ScriptVariant regionsLanguage;
    var configuration = documentContext.getServerContext().getConfiguration();
    if (configuration.getConfigurationSource() == ConfigurationSource.EMPTY || fileType == FileType.OS) {
      regionsLanguage = getScriptVariantFromConfigLanguage();
    } else {
      regionsLanguage = documentContext.getServerContext().getConfiguration().getScriptVariant();
    }
    return regionsLanguage;
  }

  @NotNull
  private ScriptVariant getScriptVariantFromConfigLanguage() {
    ScriptVariant regionsLanguage;
    if (languageServerConfiguration.getLanguage() == Language.EN) {
      regionsLanguage = ScriptVariant.ENGLISH;
    } else {
      regionsLanguage = ScriptVariant.RUSSIAN;
    }
    return regionsLanguage;
  }

  private Range calculateFixRange(Range range) {

    Position start = range.getStart();
    if (start == null) {
      start = new Position(0, 0);
    } else {
      start.setCharacter(0);
    }

    Position end = range.getEnd();
    if (end == null) {
      end = new Position(0, 0);
    } else {
      end.setCharacter(0);
    }

    range.setStart(start);
    range.setEnd(end);
    return range;
  }
}
