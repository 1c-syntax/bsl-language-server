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
package com.github._1c_syntax.bsl.languageserver.configuration.databind;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import static com.github._1c_syntax.bsl.languageserver.configuration.codelens.TestRunnerAdapterOptions.DEFAULT_ANNOTATIONS;

/**
 * Служебный класс-десериализатор для регистронезависимого списка имен аннотаций.
 */
@Slf4j
public class AnnotationsDeserializer extends JsonDeserializer<Set<String>> {

  @Override
  public Set<String> deserialize(
    JsonParser p,
    DeserializationContext context
  ) throws IOException {

    JsonNode annotations = p.getCodec().readTree(p);

    if (annotations == null) {
      return DEFAULT_ANNOTATIONS;
    }

    Set<String> annotationsSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    var objectMapper = (ObjectMapper) p.getCodec();
    objectMapper.readerForUpdating(annotationsSet).readValue(annotations);

    return annotationsSet;
  }

}
