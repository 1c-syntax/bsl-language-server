/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.mdo.support.ScriptVariant;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ServerContextTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_CATALOG_FILE = "Catalogs/Справочник1/Ext/ManagerModule.bsl";
  private static final String PATH_TO_CATALOG_MODULE_FILE = "Catalogs/Справочник1/Ext/ObjectModule.bsl";

  @Autowired
  private ServerContext serverContext;

  @Test
  void testConfigurationMetadata() {
    Path path = Absolute.path(PATH_TO_METADATA);
    serverContext.setConfigurationRoot(path);
    var configurationMetadata = serverContext.getConfiguration();

    assertThat(configurationMetadata).isNotNull();

    assertThat(configurationMetadata.getScriptVariant()).isEqualTo(ScriptVariant.RUSSIAN);

    assertThat(configurationMetadata.getConfigurationSource()).isEqualTo(ConfigurationSource.DESIGNER);

    assertThat(configurationMetadata.getCompatibilityMode().getMinor()).isEqualTo(3);
    assertThat(configurationMetadata.getCompatibilityMode().getVersion()).isEqualTo(10);

    File file = new File(PATH_TO_METADATA, PATH_TO_MODULE_FILE);
    ModuleType type = configurationMetadata.getModuleTypeByURI(Absolute.uri(file.toURI()));
    assertThat(type).isEqualTo(ModuleType.CommonModule);
  }

  @Test
  void testMdoRefs() {
    var path = Absolute.path(PATH_TO_METADATA);
    serverContext.setConfigurationRoot(path);
    var mdoRefCommonModule = "CommonModule.ПервыйОбщийМодуль";

    var documentContext = addDocumentContext(serverContext, PATH_TO_MODULE_FILE);
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
    Path path = Absolute.path(PATH_TO_METADATA + "test");

    serverContext.setConfigurationRoot(path);
    var configurationMetadata = serverContext.getConfiguration();

    assertThat(configurationMetadata).isNotNull();
    assertThat(configurationMetadata.getModulesByType()).isEmpty();
  }

  @Test
  void testPopulateContext() {
    // given
    Path path = Absolute.path(PATH_TO_METADATA);
    serverContext.setConfigurationRoot(path);

    assertThat(serverContext.getDocuments()).isEmpty();

    // when
    serverContext.populateContext();

    // then
    assertThat(serverContext.getDocuments()).hasSizeGreaterThan(0);
  }

  private DocumentContext addDocumentContext(ServerContext serverContext, String path) {
    var file = new File(PATH_TO_METADATA, path);
    var uri = Absolute.uri(file);
    var documentContext = serverContext.addDocument(uri);
    serverContext.rebuildDocument(documentContext);
    return documentContext;
  }
}
