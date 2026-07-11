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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.ConfigurationTreeParams;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.MdClassNode;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.MetadataObjectNode;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.mdclasses.Configuration;
import com.github._1c_syntax.bsl.mdclasses.ConfigurationExtension;
import com.github._1c_syntax.bsl.mdclasses.Solution;
import com.github._1c_syntax.bsl.mdo.support.ConfigurationExtensionPurpose;
import com.github._1c_syntax.bsl.types.MultiLanguageString;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterEachTestMethod
class ConfigurationTreeProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private ConfigurationTreeProvider provider;

  @Test
  void returnsConfigurationTreeWithObjectsAndAttributes() throws Exception {
    // given
    initServerContext(TestUtils.PATH_TO_METADATA, false);
    var params = new ConfigurationTreeParams(context.getWorkspaceUri().toString(), null);

    // when
    var tree = provider.configurationTree(params).get();

    // then
    assertThat(tree.extensions()).isEmpty();

    var configuration = tree.configuration();
    assertThat(configuration.kind()).isEqualTo(MdClassNode.KIND_CONFIGURATION);
    assertThat(configuration.objects())
      .extracting(MetadataObjectNode::name)
      .contains("Справочник1", "Документ1");

    var catalog = configuration.objects().stream()
      .filter(object -> object.name().equals("Справочник1"))
      .findFirst()
      .orElseThrow();

    assertThat(catalog.attributes()).isNotEmpty();
    assertThat(catalog.standardAttributes()).isNotEmpty();
  }

  @Test
  void resolvesWorkspaceByName() throws Exception {
    // given
    initServerContext(TestUtils.PATH_TO_METADATA, false);
    var params = new ConfigurationTreeParams(null, "designer");

    // when
    var tree = provider.configurationTree(params).get();

    // then
    assertThat(tree.configuration().kind()).isEqualTo(MdClassNode.KIND_CONFIGURATION);
  }

  @Test
  void failsWithInvalidParamsWhenWorkspaceIsUnknown() {
    // given
    initServerContext(TestUtils.PATH_TO_METADATA, false);
    var params = new ConfigurationTreeParams("file:///no/such/workspace", null);

    // when / then
    assertThat(invalidParamsCodeOf(params)).isEqualTo(ResponseErrorCode.InvalidParams.getValue());
  }

  @Test
  void failsWithInvalidParamsWhenNoWorkspaceIdentifierProvided() {
    // given
    initServerContext(TestUtils.PATH_TO_METADATA, false);
    var params = new ConfigurationTreeParams(null, null);

    // when / then
    assertThat(invalidParamsCodeOf(params)).isEqualTo(ResponseErrorCode.InvalidParams.getValue());
  }

  @Test
  void rendersExtensionNodeSeparatelyFromConfiguration() {
    // given
    var extension = ConfigurationExtension.builder()
      .name("РасширениеABC")
      .synonym(MultiLanguageString.EMPTY)
      .namePrefix("абв_")
      .configurationExtensionPurpose(ConfigurationExtensionPurpose.CUSTOMIZATION)
      .build();
    var solution = Solution.builder()
      .mergedConfiguration(Configuration.EMPTY)
      .baseConfiguration(Configuration.EMPTY)
      .extensions(List.of(extension))
      .build();

    // when
    var tree = ConfigurationTreeProvider.buildTree("file:///workspace", solution);

    // then
    assertThat(tree.configuration().kind()).isEqualTo(MdClassNode.KIND_CONFIGURATION);
    assertThat(tree.extensions()).hasSize(1);

    var extensionNode = tree.extensions().get(0);
    assertThat(extensionNode.kind()).isEqualTo(MdClassNode.KIND_EXTENSION);
    assertThat(extensionNode.name()).isEqualTo("РасширениеABC");
    assertThat(extensionNode.namePrefix()).isEqualTo("абв_");
    assertThat(extensionNode.purpose()).isEqualTo(ConfigurationExtensionPurpose.CUSTOMIZATION.name());
    assertThat(extensionNode.objects()).isEmpty();
  }

  /**
   * Прогоняет запрос, ожидает {@link ResponseErrorException} (LSP4J сам обернёт его в JSON-RPC
   * ответ) и возвращает её код ошибки.
   */
  private int invalidParamsCodeOf(ConfigurationTreeParams params) {
    try {
      provider.configurationTree(params);
      throw new AssertionError("Ожидалось исключение ResponseErrorException, но запрос завершился успешно");
    } catch (ResponseErrorException e) {
      return e.getResponseError().getCode();
    }
  }
}
