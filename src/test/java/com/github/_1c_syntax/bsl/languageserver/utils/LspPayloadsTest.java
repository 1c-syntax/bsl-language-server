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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LspPayloadsTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Test
  void typedPayloadIsReturnedAsIs() {
    var payload = new Payload("value");

    assertThat(LspPayloads.read(MAPPER, payload, Payload.class)).isSameAs(payload);
  }

  @Test
  void jsonTreePayloadIsDeserialized() {
    var json = new JsonObject();
    json.addProperty("name", "value");

    assertThat(LspPayloads.read(MAPPER, json, Payload.class)).isEqualTo(new Payload("value"));
  }

  @Test
  void mapPayloadIsConverted() {
    assertThat(LspPayloads.read(MAPPER, Map.of("name", "value"), Payload.class)).isEqualTo(new Payload("value"));
  }

  @Test
  void missingPayloadIsNull() {
    assertThat(LspPayloads.read(MAPPER, null, Payload.class)).isNull();
  }

  record Payload(String name) {
  }
}
