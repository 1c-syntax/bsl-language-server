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
package com.github._1c_syntax.bsl.languageserver.lsp;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterEachTestMethod
class ConfigurationTreeBuilderTest extends AbstractServerContextAwareTest {

  @Autowired
  private ConfigurationTreeBuilder builder;

  @Test
  void returnsConfigurationTreeWithObjectsAndAttributes() {
    // given
    initServerContext(TestUtils.PATH_TO_METADATA, false);
    var params = new ConfigurationTreeParams(context.getWorkspaceUri().toString(), null);

    // when
    var result = builder.getConfigurationTree(params);

    // then
    assertThat(result).isPresent();
    var tree = result.orElseThrow();
    assertThat(tree.getExtensions()).isEmpty();

    var configuration = tree.getConfiguration();
    assertThat(configuration.getKind()).isEqualTo(MdClassNode.KIND_CONFIGURATION);
    assertThat(configuration.getObjects())
      .extracting(MetadataObjectNode::getName)
      .contains("Справочник1", "Документ1");

    var catalog = configuration.getObjects().stream()
      .filter(object -> object.getName().equals("Справочник1"))
      .findFirst()
      .orElseThrow();

    assertThat(catalog.getAttributes()).isNotEmpty();
    assertThat(catalog.getStandardAttributes()).isNotEmpty();
  }

  @Test
  void returnsEmptyWhenWorkspaceIsUnknown() {
    // given
    initServerContext(TestUtils.PATH_TO_METADATA, false);
    var params = new ConfigurationTreeParams("file:///no/such/workspace", null);

    // when
    var result = builder.getConfigurationTree(params);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void resolvesWorkspaceByName() {
    // given
    initServerContext(TestUtils.PATH_TO_METADATA, false);
    var params = new ConfigurationTreeParams(null, "designer");

    // when
    var result = builder.getConfigurationTree(params);

    // then
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().getConfiguration().getKind())
      .isEqualTo(MdClassNode.KIND_CONFIGURATION);
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
    var tree = builder.buildTree("file:///workspace", solution);

    // then
    assertThat(tree.getConfiguration().getKind()).isEqualTo(MdClassNode.KIND_CONFIGURATION);
    assertThat(tree.getExtensions()).hasSize(1);

    var extensionNode = tree.getExtensions().get(0);
    assertThat(extensionNode.getKind()).isEqualTo(MdClassNode.KIND_EXTENSION);
    assertThat(extensionNode.getName()).isEqualTo("РасширениеABC");
    assertThat(extensionNode.getNamePrefix()).isEqualTo("абв_");
    assertThat(extensionNode.getPurpose()).isEqualTo(ConfigurationExtensionPurpose.CUSTOMIZATION.name());
    assertThat(extensionNode.getObjects()).isEmpty();
  }
}
