/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  activatedByDefault = false,
  tags = {
    DiagnosticTag.DESIGN
  }

)
public class MetadataBordersDiagnostic extends AbstractVisitorDiagnostic {

  private static final String METADATA_BORDERS_DEFAULT = "";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = ""
  )
  private Map<Pattern, Pattern> metadataBordersParameters = MapFromJSON(METADATA_BORDERS_DEFAULT);

  private List<Pattern> statementPatterns = Collections.emptyList();

  private static Map<Pattern, Pattern> MapFromJSON(String userSettings) {
    ObjectMapper mapper = new ObjectMapper();
    MapType mapType = mapper.getTypeFactory().constructMapType(HashMap.class, String.class, String.class);
    try {
      Map<String, String> stringMap = mapper.readValue(userSettings, mapType);
      return stringMap.entrySet().stream()
        .filter(entry -> ! entry.getKey().isBlank() && ! entry.getValue().isBlank())
        .collect(Collectors.toMap(
            entry -> CaseInsensitivePattern.compile(entry.getKey()),
            entry -> CaseInsensitivePattern.compile(entry.getValue())));
    } catch (JsonProcessingException e) {
      return Collections.emptyMap();
    }
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    metadataBordersParameters = MapFromJSON(
              (String) configuration.getOrDefault("metadataBordersParameters", METADATA_BORDERS_DEFAULT));
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    statementPatterns = metadataBordersParameters.entrySet().stream()
                      .filter(entry -> ! entry.getValue().matcher(this.documentContext.getUri().getPath()).find())
                      .map(Map.Entry::getKey)
                      .collect(Collectors.toList());

    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx){

    statementPatterns.forEach(pattern -> {
      Matcher matcher = pattern.matcher(ctx.getText());
      while (matcher.find()) {
        diagnosticStorage.addDiagnostic(ctx);
      }
    });

    return super.visitStatement(ctx);
  }
}
