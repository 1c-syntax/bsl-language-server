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
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
public class DuplicateRegionDiagnostic extends AbstractVisitorDiagnostic {
  private final Map<String, String> regionNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  public DuplicateRegionDiagnostic(DiagnosticInfo info) {
    super(info);
    regionNames.put("ПрограммныйИнтерфейс", "Public");
    regionNames.put("Public", "Public");
    regionNames.put("СлужебныйПрограммныйИнтерфейс", "Internal");
    regionNames.put("Internal", "Internal");
    regionNames.put("СлужебныеПроцедурыИФункции", "Private");
    regionNames.put("Private", "Private");
    regionNames.put("ОбработчикиСобытий", "EventHandlers");
    regionNames.put("EventHandlers", "EventHandlers");
    regionNames.put("ОбработчикиСобытийФормы", "FormEventHandlers");
    regionNames.put("FormEventHandlers", "FormEventHandlers");
    regionNames.put("ОбработчикиСобытийЭлементовШапкиФормы", "FormHeaderItemsEventHandlers");
    regionNames.put("FormHeaderItemsEventHandlers", "FormHeaderItemsEventHandlers");
    regionNames.put("ОбработчикиКомандФормы", "FormCommandsEventHandlers");
    regionNames.put("FormCommandsEventHandlers", "FormCommandsEventHandlers");
    regionNames.put("ОписаниеПеременных", "Variables");
    regionNames.put("Variables", "Variables");
    regionNames.put("Инициализация", "Initialize");
    regionNames.put("Initialize", "Initialize");
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
          Ranges.create(region))
      ))
      .collect(Collectors.toList());

    // анализировать модуль без областей не будем
    if (regions.isEmpty()) {
      return ctx;
    }

    // считаем дубли с учетом альтернативных имен для стандартных областей
    regions.stream()
      .collect(Collectors.groupingBy(regionSymbol ->
        regionNames.getOrDefault(regionSymbol.getName(), regionSymbol.getName())))
      .forEach((String name, List<RegionSymbol> regionsList) -> {
          if (regionsList.size() > 1) {

            List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();
            RegionSymbol currentRegion = regionsList.get(0);
            Range currentRange = Ranges.create(currentRegion.getStartNode().getStart(),
              currentRegion.getStartNode().getStop());

            regionsList.stream()
              .map(region ->
                RelatedInformation.create(
                  documentContext.getUri(),
                  Ranges.create(region.getStartNode().getStart(), region.getStartNode().getStop()),
                  "+1"
                )
              )
              .collect(Collectors.toCollection(() -> relatedInformation));

            diagnosticStorage.addDiagnostic(
              currentRange,
              info.getMessage(currentRegion.getName()),
              relatedInformation);
          }
        }
      );

    return ctx;
  }
}
