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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonModuleAccessorsConfigurationTest {

  @Test
  void cachedPatternsFollowChangesWithoutChangingPreviousSnapshots() {
    var configuration = new LanguageServerConfiguration();
    var options = configuration.getReferencesOptions();
    options.setCommonModuleAccessors(new ArrayList<>(List.of("CommonModule", "CommonUse.Module")));
    var first = configuration.getParsedCommonModuleAccessors();

    assertThat(configuration.getParsedCommonModuleAccessors()).isSameAs(first);
    options.getCommonModuleAccessors().set(0, "OtherMethod");
    var changed = configuration.getParsedCommonModuleAccessors();
    assertThat(changed.localMethods()).containsExactly("othermethod");
    assertThat(first.localMethods()).containsExactly("commonmodule");

    options.setCommonModuleAccessors(List.of("Restored"));
    assertThat(configuration.getParsedCommonModuleAccessors().localMethods()).containsExactly("restored");
    assertThatThrownBy(() -> first.localMethods().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> first.moduleMethodPairs().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> first.moduleMethodPairs().get("commonuse").clear())
      .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void cachedPatternsFollowFileUpdateAndReset(@TempDir Path directory) throws IOException {
    var configuration = new LanguageServerConfiguration();
    var defaults = configuration.getParsedCommonModuleAccessors();
    var file = directory.resolve("config.json");
    Files.writeString(file, """
      {"references": {"commonModuleAccessors": ["CustomModule.CustomMethod"]}}
      """);

    configuration.update(file.toFile());
    var updated = configuration.getParsedCommonModuleAccessors();
    assertThat(updated.localMethods()).isEmpty();
    assertThat(updated.moduleMethodPairs()).containsOnlyKeys("custommodule");
    assertThat(updated.moduleMethodPairs().get("custommodule")).containsExactly("custommethod");

    configuration.reset();
    assertThat(configuration.getParsedCommonModuleAccessors()).isEqualTo(defaults);
  }

  @Test
  void cacheIsNotPartOfSerializedSettingsOrEquality() {
    var configuration = new LanguageServerConfiguration();
    var mapper = new JsonMapper();
    var before = mapper.writeValueAsString(configuration);
    var hashBefore = configuration.hashCode();
    var descriptionBefore = configuration.toString();
    configuration.getParsedCommonModuleAccessors();

    assertThat(mapper.writeValueAsString(configuration)).isEqualTo(before);
    assertThat(configuration.hashCode()).isEqualTo(hashBefore);
    assertThat(configuration.toString()).isEqualTo(descriptionBefore);
  }
}
