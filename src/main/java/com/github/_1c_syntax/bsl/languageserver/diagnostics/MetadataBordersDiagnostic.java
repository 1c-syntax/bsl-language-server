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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.*;
import com.github._1c_syntax.bsl.parser.*;
import com.github._1c_syntax.utils.*;
import org.antlr.v4.runtime.tree.*;

import java.util.*;
import java.util.regex.*;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR
  }

)
public class MetadataBordersDiagnostic extends AbstractVisitorDiagnostic {

  private static final String METADATA_BORDERS_DEFAULT = "";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = ""
  )
  private HashMap<String, String> metadataBordersParameters = MapFromJSON(METADATA_BORDERS_DEFAULT);

  private static HashMap<String, String> MapFromJSON(String userSettings) {
    ObjectMapper mapper = new ObjectMapper();
    MapType mapType = mapper.getTypeFactory().constructMapType(HashMap.class, String.class, String.class);
    try {
      return mapper.readValue(userSettings, mapType);
    } catch (JsonProcessingException e) {
      return new HashMap<>();
    }
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    this.metadataBordersParameters = MapFromJSON(
              (String) configuration.getOrDefault("metadataBordersParameters", METADATA_BORDERS_DEFAULT));
  }

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext ctx){

    for (Map.Entry<String, String> entry: metadataBordersParameters.entrySet()) {

      if (entry.getKey().isBlank() || entry.getValue().isBlank()) {
        continue;
      }

      Matcher matcher = CaseInsensitivePattern.compile(entry.getKey())
                        .matcher(ctx.getText());

      boolean insideBorders = CaseInsensitivePattern.compile(entry.getValue())
                              .matcher(this.documentContext.getUri().getPath())
                              .find();

      while (matcher.find() && ! insideBorders) {
        diagnosticStorage.addDiagnostic(ctx);
      }
    }
    return super.visitStatement(ctx);
  }
}
