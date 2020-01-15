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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  },
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_1
)
public class NonStandardRegionDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern PUBLIC_REGION_NAME =
    createPattern(Keywords.PUBLIC_REGION_RU, Keywords.PUBLIC_REGION_EN);

  private static final Pattern INTERNAL_REGION_NAME =
    createPattern(Keywords.INTERNAL_REGION_RU, Keywords.INTERNAL_REGION_EN);

  private static final Pattern PRIVATE_REGION_NAME =
    createPattern(Keywords.PRIVATE_REGION_RU, Keywords.PRIVATE_REGION_EN);

  private static final Pattern EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.EVENTHANDLERS_REGION_RU, Keywords.EVENTHANDLERS_REGION_EN);

  private static final Pattern FORM_EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.FORMEVENTHANDLERS_REGION_RU, Keywords.FORMEVENTHANDLERS_REGION_EN);

  private static final Pattern FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.FORMHEADERITEMSEVENTHANDLERS_REGION_RU, Keywords.FORMHEADERITEMSEVENTHANDLERS_REGION_EN);

  private static final Pattern FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_NAME =
    createPattern("ОбработчикиСобытийЭлементовТаблицыФормы",
      "FormTableItemsEventHandlers", "^(?:%s|%s)[\\w]*$");

  private static final Pattern FORM_COMMANDS_EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.FORMCOMMANDSEVENTHANDLERS_REGION_RU, Keywords.FORMCOMMANDSEVENTHANDLERS_REGION_EN);

  private static final Pattern VARIABLES_REGION_NAME =
    createPattern(Keywords.VARIABLES_REGION_RU, Keywords.VARIABLES_REGION_EN);

  private static final Pattern INITIALIZE_REGION_NAME =
    createPattern(Keywords.INITIALIZE_REGION_RU, Keywords.INITIALIZE_REGION_EN);

  public NonStandardRegionDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  private static Set<Pattern> getStandardRegions(ModuleType moduleType) {

    Set<Pattern> standardRegions = new HashSet<>();

    switch (moduleType) {
      case FormModule:
        standardRegions.add(VARIABLES_REGION_NAME);
        standardRegions.add(FORM_EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(FORM_COMMANDS_EVENT_HANDLERS_REGION_NAME);
        break;
      case ObjectModule:
      case RecordSetModule:
      case ValueManagerModule:
        standardRegions.add(VARIABLES_REGION_NAME);
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(INTERNAL_REGION_NAME);
        break;
      case CommonModule:
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(INTERNAL_REGION_NAME);
        break;
      case ApplicationModule:
      case ManagedApplicationModule:
      case OrdinaryApplicationModule:
        standardRegions.add(VARIABLES_REGION_NAME);
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        break;
      case CommandModule:
      case SessionModule:
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        break;
      case ExternalConnectionModule:
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        break;
      case ManagerModule:
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(INTERNAL_REGION_NAME);
        break;
      default:
        // для Unknown ничего
        return standardRegions;
    }

    // у всех типов модулей есть такая область
    standardRegions.add(PRIVATE_REGION_NAME);

    if (moduleType == ModuleType.FormModule
      || moduleType == ModuleType.ObjectModule) {
      standardRegions.add(INITIALIZE_REGION_NAME);
    }
    return standardRegions;
  }

  private static Pattern createPattern(String keywordRu, String keywordEn) {
    return createPattern(keywordRu, keywordEn, "^(?:%s|%s)$");
  }

  private static Pattern createPattern(String keywordRu, String keywordEn, String template) {
    return Pattern.compile(
      String.format(template, keywordRu, keywordEn),
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {

    List<RegionSymbol> regions = documentContext.getFileLevelRegions();

    // чтобы не было лишних FP, анализировать модуль без областей не будем
    // вешать диагностику тож не будем, пусть вешается "CodeOutOfRegionDiagnostic"
    if (regions.isEmpty()) {
      return ctx;
    }

    ModuleType moduleType = documentContext
      .getServerContext()
      .getConfiguration()
      .getModuleType(documentContext.getUri());

    Set<Pattern> standardRegions = getStandardRegions(moduleType);

    // проверим, что область находится в списке доступных
    regions.forEach((RegionSymbol region) -> {
      if (standardRegions.stream().noneMatch(regionName -> regionName.matcher(region.getName()).find())) {
        diagnosticStorage.addDiagnostic(
          Ranges.create(region.getStartNode().getStart(), region.getStartNode().getStop()),
          info.getMessage(region.getName())
        );
      }
    });

    return ctx;
  }
}
