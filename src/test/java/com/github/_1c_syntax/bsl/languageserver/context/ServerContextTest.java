/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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

import org.github._1c_syntax.mdclasses.metadata.Configuration;
import org.github._1c_syntax.mdclasses.metadata.additional.ConfigurationSource;
import org.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.github._1c_syntax.mdclasses.metadata.additional.ScriptVariant;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerContextTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_MODULE_FILE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";

  @Test
  void testConfigurationMetadata() {

    Path path = Paths.get(PATH_TO_METADATA).toAbsolutePath();
    ServerContext serverContext = new ServerContext();
    serverContext.setPathToConfigurationMetadata(path);
    Configuration configurationMetadata = serverContext.getConfiguration();

    assertThat(configurationMetadata).isNotEqualTo(null);

    assertThat(configurationMetadata.getScriptVariant()).isEqualTo(ScriptVariant.RUSSIAN);

    assertThat(configurationMetadata.getConfigurationSource()).isEqualTo(ConfigurationSource.DESIGNER);

    assertThat(configurationMetadata.getCompatibilityMode().getMajor()).isEqualTo(8);
    assertThat(configurationMetadata.getCompatibilityMode().getMinor()).isEqualTo(3);
    assertThat(configurationMetadata.getCompatibilityMode().getVersion()).isEqualTo(10);

    File file = new File(PATH_TO_METADATA, PATH_TO_MODULE_FILE);
    ModuleType type = configurationMetadata.getModuleType(file.toURI());
    assertThat(type).isEqualTo(ModuleType.CommonModule);

  }

  @Test
  void testErrorConfigurationMetadata() {
    Path path = Paths.get(PATH_TO_METADATA, "test").toAbsolutePath();

    ServerContext serverContext = new ServerContext();
    serverContext.setPathToConfigurationMetadata(path);
    Configuration configurationMetadata = serverContext.getConfiguration();

    assertThat(configurationMetadata).isNotNull();
    assertThat(configurationMetadata.getModulesByType()).hasSize(0);
  }

}
