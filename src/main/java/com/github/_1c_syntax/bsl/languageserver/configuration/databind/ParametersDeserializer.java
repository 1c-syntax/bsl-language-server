/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.configuration.databind;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Служебный класс-десериализатор для коллекции настроек.
 */
@Slf4j
public class ParametersDeserializer extends JsonDeserializer<Map<String, Either<Boolean, Map<String, Object>>>> {

  @Override
  public Map<String, Either<Boolean, Map<String, Object>>> deserialize(
    JsonParser p,
    DeserializationContext context
  ) throws IOException {

    JsonNode parameters = p.getCodec().readTree(p);

    if (parameters == null) {
      return Collections.emptyMap();
    }

    var mapper = new ObjectMapper();
    Map<String, Either<Boolean, Map<String, Object>>> parametersMap = new HashMap<>();

    Iterator<Map.Entry<String, JsonNode>> parametersNodes = parameters.fields();
    parametersNodes.forEachRemaining((Map.Entry<String, JsonNode> entry) -> {
      JsonNode parameterConfig = entry.getValue();
      if (parameterConfig.isBoolean()) {
        parametersMap.put(entry.getKey(), Either.forLeft(parameterConfig.asBoolean()));
      } else {
        Map<String, Object> parameterConfiguration = getParameterConfiguration(mapper, entry.getValue());
        parametersMap.put(entry.getKey(), Either.forRight(parameterConfiguration));
      }
    });

    return parametersMap;
  }

  private static Map<String, Object> getParameterConfiguration(
    ObjectMapper mapper,
    JsonNode parameterConfig
  ) {
    Map<String, Object> parameterConfiguration;
    try {
      JavaType type = mapper.getTypeFactory().constructType(new TypeReference<Map<String, Object>>() {});
      parameterConfiguration = mapper.readValue(mapper.treeAsTokens(parameterConfig), type);
    } catch (IOException e) {
      LOGGER.error("Can't deserialize parameter configuration", e);
      return Collections.emptyMap();
    }
    return parameterConfiguration;
  }

}
