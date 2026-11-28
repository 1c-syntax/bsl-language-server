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

import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensSupplier;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.inlayhints.InlayHintSupplier;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт «код ↔ схема» для переключателей линз и подсказок: идентификатор каждого supplier'а —
 * ключ {@code codeLens.parameters} / {@code inlayHint.parameters}, которым пользователь включает и
 * выключает фичу, поэтому он обязан быть объявлен в JSON-схеме, а в схеме не должно быть ключей
 * без supplier'а. {@code ConfigurationSchemaDefaultsTest} проверяет только направление
 * «схема → код».
 */
@CleanupContextBeforeClassAndAfterClass
class SupplierIdsSchemaTest extends AbstractServerContextAwareTest {

  private static final String SCHEMA = "/com/github/_1c_syntax/bsl/languageserver/configuration/schema.json";

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  void everyCodeLensAndInlayHintIdIsDeclaredInSchema() throws IOException {
    initServerContext();
    JsonNode definitions;
    try (var stream = getClass().getResourceAsStream(SCHEMA)) {
      definitions = JsonMapper.builder().build().readTree(stream).get("definitions");
    }

    assertThat(declared(definitions, "codeLensParameters"))
      .as("ключи codeLens.parameters в схеме")
      .isEqualTo(ids(CodeLensSupplier.class, CodeLensSupplier::getId));
    assertThat(declared(definitions, "inlayHintParameters"))
      .as("ключи inlayHint.parameters в схеме")
      .isEqualTo(ids(InlayHintSupplier.class, InlayHintSupplier::getId));
  }

  private static Set<String> declared(JsonNode definitions, String definition) {
    return new TreeSet<>(definitions.get(definition).get("properties").propertyNames());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private <T> Set<String> ids(Class<T> type, Function<T, String> id) {
    var result = new TreeSet<String>();
    applicationContext.getBeansOfType(type).values().forEach(bean -> result.add(id.apply(bean)));
    return result;
  }
}
