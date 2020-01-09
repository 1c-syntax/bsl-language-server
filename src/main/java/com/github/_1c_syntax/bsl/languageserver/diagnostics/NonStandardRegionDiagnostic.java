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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  },
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_1
)
public class NonStandardRegionDiagnostic extends AbstractVisitorDiagnostic {

  public NonStandardRegionDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  private static List<Pattern> getStandardRegions(ModuleType moduleType) {

    List<Pattern> standardRegions = new ArrayList<>();

    Pattern publicRegionName = Pattern.compile(
      "^(?:ПрограммныйИнтерфейс|Public)$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Pattern internalRegionName = Pattern.compile(
      "^(?:СлужебныйПрограммныйИнтерфейс|Internal)$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Pattern privateRegionName = Pattern.compile(
      "^(?:СлужебныеПроцедурыИФункции|Private)$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Pattern eventHandlersRegionName = Pattern.compile(
      "^(?:ОбработчикиСобытий|EventHandlers)$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Pattern formEventHandlersRegionName = Pattern.compile(
      "^(?:ОбработчикиСобытийФормы|FormEventHandlers)$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Pattern formHeaderItemsEventHandlersRegionName = Pattern.compile(
      "^(?:ОбработчикиСобытийЭлементовШапкиФормы|FormHeaderItemsEventHandlers)$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Pattern formTableItemsEventHandlersRegionName = Pattern.compile(
      "^(?:ОбработчикиСобытийЭлементовТаблицыФормы|FormTableItemsEventHandlers)[\\w]*$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Pattern formCommandsEventHandlersRegionName = Pattern.compile(
      "^(?:ОбработчикиКомандФормы|FormCommandsEventHandlers)$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Pattern variablesRegionName = Pattern.compile(
      "^(?:ОписаниеПеременных|Variables)$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Pattern initializeRegionName = Pattern.compile(
      "^(?:Инициализация|Initialize)$",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    if (moduleType == ModuleType.FormModule) {
      standardRegions.add(variablesRegionName);
      standardRegions.add(formEventHandlersRegionName);
      standardRegions.add(formHeaderItemsEventHandlersRegionName);
      standardRegions.add(formTableItemsEventHandlersRegionName);
      standardRegions.add(formCommandsEventHandlersRegionName);
    }

    if (moduleType == ModuleType.ObjectModule
      || moduleType == ModuleType.RecordSetModule
      || moduleType == ModuleType.ValueManagerModule) {
      standardRegions.add(variablesRegionName);
      standardRegions.add(publicRegionName);
      standardRegions.add(eventHandlersRegionName);
      standardRegions.add(internalRegionName);
    }

    if (moduleType == ModuleType.CommonModule) {
      standardRegions.add(publicRegionName);
      standardRegions.add(internalRegionName);
    }

    if (moduleType == ModuleType.ApplicationModule
      || moduleType == ModuleType.ManagedApplicationModule
      || moduleType == ModuleType.OrdinaryApplicationModule) {
      standardRegions.add(variablesRegionName);
      standardRegions.add(publicRegionName);
      standardRegions.add(eventHandlersRegionName);
    }

    if (moduleType == ModuleType.CommandModule
      || moduleType == ModuleType.SessionModule) {
      standardRegions.add(eventHandlersRegionName);
    }

    if (moduleType == ModuleType.ExternalConnectionModule) {
      standardRegions.add(publicRegionName);
      standardRegions.add(eventHandlersRegionName);
    }

    if (moduleType == ModuleType.ManagerModule) {
      standardRegions.add(publicRegionName);
      standardRegions.add(eventHandlersRegionName);
      standardRegions.add(internalRegionName);
    }

    // у всех типов модулей есть такая область
    standardRegions.add(privateRegionName);

    if (moduleType == ModuleType.FormModule
      || moduleType == ModuleType.ObjectModule) {
      standardRegions.add(initializeRegionName);
    }
    return standardRegions;
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {

    // получим "честные" области первого уровня
    List<Range> methodRanges = documentContext.getMethods().stream()
      .map(MethodSymbol::getRange).collect(Collectors.toList());
    List<RegionSymbol> regions = documentContext.getRegions()
      .stream()
      .filter(region -> methodRanges.stream().noneMatch(methodRange ->
        Ranges.containsRange(methodRange,
          Ranges.create(region.getStartNode().getStart(), region.getEndNode().getStop()))
      ))
      .collect(Collectors.toList());

    // чтобы не было лишних FP, анализировать модуль без областей не будем
    // вешать диагностику тож не будем, пусть вешается "CodeOutOfRegionDiagnostic"
    if (regions.isEmpty()) {
      return ctx;
    }

    ModuleType moduleType = documentContext
      .getServerContext()
      .getConfiguration()
      .getModuleType(documentContext.getUri().normalize());

    List<Pattern> standardRegions = getStandardRegions(moduleType);

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
