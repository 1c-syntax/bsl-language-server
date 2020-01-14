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

  private static final Pattern PUBLIC_REGION_NAME = Pattern.compile(
    "^(?:ПрограммныйИнтерфейс|Public)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern INTERNAL_REGION_NAME = Pattern.compile(
    "^(?:СлужебныйПрограммныйИнтерфейс|Internal)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern PRIVATE_REGION_NAME = Pattern.compile(
    "^(?:СлужебныеПроцедурыИФункции|Private)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern EVENT_HANDLERS_REGION_NAME = Pattern.compile(
    "^(?:ОбработчикиСобытий|EventHandlers)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern FORM_EVENT_HANDLERS_REGION_NAME = Pattern.compile(
    "^(?:ОбработчикиСобытийФормы|FormEventHandlers)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION_NAME = Pattern.compile(
    "^(?:ОбработчикиСобытийЭлементовШапкиФормы|FormHeaderItemsEventHandlers)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_NAME = Pattern.compile(
    "^(?:ОбработчикиСобытийЭлементовТаблицыФормы|FormTableItemsEventHandlers)[\\w]*$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern FORM_COMMANDS_EVENT_HANDLERS_REGION_NAME = Pattern.compile(
    "^(?:ОбработчикиКомандФормы|FormCommandsEventHandlers)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern VARIABLES_REGION_NAME = Pattern.compile(
    "^(?:ОписаниеПеременных|Variables)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private static final Pattern INITIALIZE_REGION_NAME = Pattern.compile(
    "^(?:Инициализация|Initialize)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public NonStandardRegionDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  private static Set<Pattern> getStandardRegions(ModuleType moduleType) {

    Set<Pattern> standardRegions = new HashSet<>();

    if (moduleType == ModuleType.FormModule) {
      standardRegions.add(VARIABLES_REGION_NAME);
      standardRegions.add(FORM_EVENT_HANDLERS_REGION_NAME);
      standardRegions.add(FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION_NAME);
      standardRegions.add(FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_NAME);
      standardRegions.add(FORM_COMMANDS_EVENT_HANDLERS_REGION_NAME);
    }

    if (moduleType == ModuleType.ObjectModule
      || moduleType == ModuleType.RecordSetModule
      || moduleType == ModuleType.ValueManagerModule) {
      standardRegions.add(VARIABLES_REGION_NAME);
      standardRegions.add(PUBLIC_REGION_NAME);
      standardRegions.add(EVENT_HANDLERS_REGION_NAME);
      standardRegions.add(INTERNAL_REGION_NAME);
    }

    if (moduleType == ModuleType.CommonModule) {
      standardRegions.add(PUBLIC_REGION_NAME);
      standardRegions.add(INTERNAL_REGION_NAME);
    }

    if (moduleType == ModuleType.ApplicationModule
      || moduleType == ModuleType.ManagedApplicationModule
      || moduleType == ModuleType.OrdinaryApplicationModule) {
      standardRegions.add(VARIABLES_REGION_NAME);
      standardRegions.add(PUBLIC_REGION_NAME);
      standardRegions.add(EVENT_HANDLERS_REGION_NAME);
    }

    if (moduleType == ModuleType.CommandModule
      || moduleType == ModuleType.SessionModule) {
      standardRegions.add(EVENT_HANDLERS_REGION_NAME);
    }

    if (moduleType == ModuleType.ExternalConnectionModule) {
      standardRegions.add(PUBLIC_REGION_NAME);
      standardRegions.add(EVENT_HANDLERS_REGION_NAME);
    }

    if (moduleType == ModuleType.ManagerModule) {
      standardRegions.add(PUBLIC_REGION_NAME);
      standardRegions.add(EVENT_HANDLERS_REGION_NAME);
      standardRegions.add(INTERNAL_REGION_NAME);
    }

    // у всех типов модулей есть такая область
    standardRegions.add(PRIVATE_REGION_NAME);

    if (moduleType == ModuleType.FormModule
      || moduleType == ModuleType.ObjectModule) {
      standardRegions.add(INITIALIZE_REGION_NAME);
    }
    return standardRegions;
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
