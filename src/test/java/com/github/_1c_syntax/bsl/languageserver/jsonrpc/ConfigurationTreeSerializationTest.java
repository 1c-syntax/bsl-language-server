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
package com.github._1c_syntax.bsl.languageserver.jsonrpc;

import com.google.gson.Gson;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что DTO-записи пакета {@code jsonrpc} корректно (де)сериализуются тем же стоковым
 * Gson, который LSP4J использует на JSON-RPC проводе, и что имена полей на проводе не изменились
 * при переходе с Lombok {@code @Value} на {@code record}.
 */
class ConfigurationTreeSerializationTest {

  // Тот же Gson, что и у LSPLauncher.createServerLauncher (стоковая конфигурация без кастомизации).
  private final Gson gson = new MessageJsonHandler(Collections.emptyMap()).getGson();

  @Test
  void configurationTreeRoundTripsThroughLsp4jGson() {
    // given
    var attribute = new AttributeNode("Реквизит1", "Синоним реквизита");
    var standardAttribute = new AttributeNode("Код", "");
    var object = new MetadataObjectNode(
      "Справочник1", "Синоним справочника", "CATALOG", "Справочник.Справочник1",
      List.of(attribute), List.of(standardAttribute));
    var configuration = new MdClassNode(
      "ИмяКонфигурации", "Синоним конфигурации", MdClassNode.KIND_CONFIGURATION,
      null, null, List.of(object));
    var extension = new MdClassNode(
      "РасширениеABC", "", MdClassNode.KIND_EXTENSION, "CUSTOMIZATION", "абв_", List.of());
    var tree = new ConfigurationTree("file:///ws", configuration, List.of(extension));

    // when
    var json = gson.toJson(tree);
    var restored = gson.fromJson(json, ConfigurationTree.class);

    // then
    // имена полей на проводе — это имена компонент record (совпадают с прежними Lombok-полями)
    assertThat(json)
      .contains("\"workspaceUri\"", "\"configuration\"", "\"extensions\"",
        "\"kind\"", "\"objects\"", "\"mdoType\"", "\"mdoRef\"",
        "\"attributes\"", "\"standardAttributes\"", "\"namePrefix\"", "\"purpose\"");
    assertThat(restored).isEqualTo(tree);
  }

  @Test
  void configurationTreeParamsRoundTripsAndSupportsPartialInput() {
    // given
    var params = new ConfigurationTreeParams("file:///ws", null);

    // when
    var json = gson.toJson(params);
    var restored = gson.fromJson(json, ConfigurationTreeParams.class);

    // then
    assertThat(json).contains("\"workspaceUri\"");
    assertThat(restored).isEqualTo(params);

    // частичный вход (только имя) десериализуется без ошибок, отсутствующее поле → null
    var byName = gson.fromJson("{\"workspaceName\":\"designer\"}", ConfigurationTreeParams.class);
    assertThat(byName.workspaceName()).isEqualTo("designer");
    assertThat(byName.workspaceUri()).isNull();
  }
}
