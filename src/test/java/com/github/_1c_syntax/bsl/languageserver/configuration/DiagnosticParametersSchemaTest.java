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
package com.github._1c_syntax.bsl.languageserver.configuration;

import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticInfos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт «JSON-схема параметров диагностик соответствует коду».
 * <p>
 * Схемы {@code schema.json} и {@code parameters-schema.json} генерируются задачей
 * {@code updateJsonSchema} из {@code @DiagnosticParameter}, но хранятся в репозитории. Тест ловит
 * диагностику, добавленную без перегенерации схемы (редактор не подскажет её параметры), и
 * параметр, дефолт которого в коде и в схеме разошёлся.
 */
@SpringBootTest
class DiagnosticParametersSchemaTest {

  private static final String RESOURCES = "/com/github/_1c_syntax/bsl/languageserver/configuration/";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Autowired
  private DiagnosticInfos diagnosticInfos;

  private JsonNode references;
  private JsonNode definitions;

  @BeforeEach
  void readSchemas() throws IOException {
    try (var schema = getClass().getResourceAsStream(RESOURCES + "schema.json");
         var parametersSchema = getClass().getResourceAsStream(RESOURCES + "parameters-schema.json")) {
      references = MAPPER.readTree(schema).path("definitions").path("diagnosticParameters").path("properties");
      definitions = MAPPER.readTree(parametersSchema).path("definitions");
    }
  }

  @Test
  void everyDiagnosticIsDescribedWithItsParameterDefaults() {
    var mismatches = new ArrayList<String>();

    diagnosticInfos.getByCode().forEach((code, info) -> {
      var expectedReference = "parameters-schema.json#/definitions/" + code;
      if (!expectedReference.equals(references.path(code).path("$ref").asString(""))) {
        mismatches.add(code + ": нет ссылки в schema.json");
      }
      var definition = definitions.path(code);
      if (definition.isMissingNode()) {
        mismatches.add(code + ": нет определения в parameters-schema.json");
        return;
      }
      info.getParameters().forEach(parameter -> {
        var schemaDefault = definition.path("properties").path(parameter.getName()).path("default");
        if (schemaDefault.isMissingNode()) {
          mismatches.add(code + "." + parameter.getName() + ": нет параметра в parameters-schema.json");
        } else if (!sameValue(schemaDefault, parameter.getDefaultValue())) {
          mismatches.add(code + "." + parameter.getName() + ": в схеме " + schemaDefault
            + ", в коде " + parameter.getDefaultValue());
        }
      });
    });

    assertThat(mismatches).isEmpty();
  }

  @Test
  void schemaDescribesOnlyExistingDiagnostics() {
    var codes = diagnosticInfos.getByCode().keySet();
    var stale = new HashSet<String>();
    definitions.propertyNames().forEach(stale::add);
    references.propertyNames().forEach(stale::add);
    stale.removeAll(codes);

    assertThat(stale).isEmpty();
  }

  private static boolean sameValue(JsonNode schemaDefault, Object codeDefault) {
    if (codeDefault instanceof Number number) {
      // Через десятичную запись: Float 0.9 и JSON 0.9 равны по смыслу, но не как double.
      return schemaDefault.isNumber()
        && new BigDecimal(String.valueOf(number)).compareTo(schemaDefault.decimalValue()) == 0;
    }
    if (codeDefault instanceof Boolean bool) {
      return schemaDefault.isBoolean() && schemaDefault.asBoolean() == bool;
    }
    return schemaDefault.asString("").equals(String.valueOf(codeDefault));
  }
}
