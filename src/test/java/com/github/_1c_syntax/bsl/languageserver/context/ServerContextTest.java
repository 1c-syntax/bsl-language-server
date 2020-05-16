/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.mdclasses.metadata.Configuration;
import com.github._1c_syntax.mdclasses.metadata.additional.ConfigurationSource;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.mdclasses.metadata.additional.ScriptVariant;
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerContextTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_MODULE_FILE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_CATALOG_FILE = "Catalogs/Справочник1/Ext/ManagerModule.bsl";
  private static final String PATH_TO_CATALOG_MODULE_FILE = "Catalogs/Справочник1/Ext/ObjectModule.bsl";

  @Test
  void testConfigurationMetadata() {

    Path path = Absolute.path(PATH_TO_METADATA);
    ServerContext serverContext = new ServerContext(path);
    Configuration configurationMetadata = serverContext.getConfiguration();

    assertThat(configurationMetadata).isNotEqualTo(null);

    assertThat(configurationMetadata.getScriptVariant()).isEqualTo(ScriptVariant.RUSSIAN);

    assertThat(configurationMetadata.getConfigurationSource()).isEqualTo(ConfigurationSource.DESIGNER);

    assertThat(configurationMetadata.getCompatibilityMode().getMajor()).isEqualTo(8);
    assertThat(configurationMetadata.getCompatibilityMode().getMinor()).isEqualTo(3);
    assertThat(configurationMetadata.getCompatibilityMode().getVersion()).isEqualTo(10);

    File file = new File(PATH_TO_METADATA, PATH_TO_MODULE_FILE);
    ModuleType type = configurationMetadata.getModuleType(Absolute.uri(file.toURI()));
    assertThat(type).isEqualTo(ModuleType.CommonModule);

  }

  @Test
  void testMdoRefs() throws IOException {

    var path = Absolute.path(PATH_TO_METADATA);
    var serverContext = new ServerContext(path);
    var mdoRefCommonModule = "CommonModule.ПервыйОбщийМодуль";

    DocumentContext documentContext = addDocumentContext(serverContext, PATH_TO_MODULE_FILE);
    assertThat(serverContext.getDocument(mdoRefCommonModule, documentContext.getModuleType()))
      .isPresent()
      .get()
      .isEqualTo(documentContext);
    assertThat(serverContext.getDocuments(mdoRefCommonModule))
      .hasSize(1)
      .containsKey(documentContext.getModuleType())
      .containsValue(documentContext);

    addDocumentContext(serverContext, PATH_TO_CATALOG_MODULE_FILE);
    addDocumentContext(serverContext, PATH_TO_CATALOG_FILE);

    // для проверки на дубль
    addDocumentContext(serverContext, PATH_TO_CATALOG_FILE);

    assertThat(serverContext.getDocuments("Catalog.Справочник1"))
      .hasSize(2)
      .containsKeys(ModuleType.ManagerModule, ModuleType.ObjectModule);

    serverContext.removeDocument(Absolute.uri(new File(PATH_TO_METADATA, PATH_TO_MODULE_FILE)));
    assertThat(serverContext.getDocument(mdoRefCommonModule, ModuleType.CommonModule))
      .isNotPresent();
  }

  @Test
  void testErrorConfigurationMetadata() {
    Path path = Absolute.path(Paths.get(PATH_TO_METADATA, "test"));

    ServerContext serverContext = new ServerContext();
    serverContext.setConfigurationRoot(path);
    Configuration configurationMetadata = serverContext.getConfiguration();

    assertThat(configurationMetadata).isNotNull();
    assertThat(configurationMetadata.getModulesByType()).hasSize(0);
  }

  @Test
  void testPopulateContext() {
    // given
    Path path = Absolute.path(PATH_TO_METADATA);
    ServerContext serverContext = new ServerContext(path);

    assertThat(serverContext.getDocuments()).hasSize(0);

    // when
    serverContext.populateContext();

    // then
    assertThat(serverContext.getDocuments()).hasSizeGreaterThan(0);
  }

  private DocumentContext addDocumentContext(ServerContext serverContext, String path) throws IOException {
    var file = new File(PATH_TO_METADATA, path);
    var uri = Absolute.uri(file);
    return serverContext.addDocument(uri, FileUtils.readFileToString(file, StandardCharsets.UTF_8));
  }

}
