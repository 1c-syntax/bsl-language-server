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

import com.github._1c_syntax.bsl.languageserver.configuration.references.ReferencesOptions;
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
  void setterUpdatesPatternsWithoutChangingPreviousSnapshots() {
    var configuration = new LanguageServerConfiguration();
    var options = configuration.getReferencesOptions();
    var patterns = new ArrayList<>(List.of("CommonModule", "CommonUse.Module"));
    options.setCommonModuleAccessors(patterns);
    configuration.setReferencesOptions(options);
    var first = configuration.getReferencesOptions().getModuleAccessors();

    assertThat(configuration.getReferencesOptions().getModuleAccessors()).isSameAs(first);
    patterns.set(0, "OtherMethod");
    assertThat(configuration.getReferencesOptions().getCommonModuleAccessors()).contains("CommonModule");
    assertThat(options.getCommonModuleAccessors()).contains("CommonModule");
    assertThatThrownBy(() -> options.getCommonModuleAccessors().clear())
      .isInstanceOf(UnsupportedOperationException.class);
    options.setCommonModuleAccessors(patterns);
    configuration.setReferencesOptions(options);
    var changed = configuration.getReferencesOptions().getModuleAccessors();
    assertThat(changed.localMethods()).containsExactly("othermethod");
    assertThat(first.localMethods()).containsExactly("commonmodule");

    options.setCommonModuleAccessors(List.of("Restored"));
    configuration.setReferencesOptions(options);
    assertThat(configuration.getReferencesOptions().getModuleAccessors().localMethods()).containsExactly("restored");
    var localMethods = first.localMethods();
    var moduleMethods = first.moduleMethodPairs();
    var commonUseMethods = moduleMethods.get("commonuse");
    assertThatThrownBy(localMethods::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(moduleMethods::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(commonUseMethods::clear)
      .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void jsonDeserializationUsesTheReferencesSetter() {
    var mapper = new JsonMapper();
    var configuration = mapper.readValue("""
      {"references": {"commonModuleAccessors": ["CustomMethod"]}}
      """, LanguageServerConfiguration.class);

    assertThat(configuration.getReferencesOptions().getModuleAccessors().localMethods()).containsExactly("custommethod");
    assertThat(configuration.getReferencesOptions().getModuleAccessors().moduleMethodPairs()).isEmpty();
    assertThat(mapper.writeValueAsString(configuration)).doesNotContain("\"moduleAccessors\"");
  }

  @Test
  void referencesOptionsOwnTheCacheAndRefreshItOnAssignment() {
    var options = new ReferencesOptions(List.of("CommonUse.Module"));
    var original = options.getModuleAccessors();
    assertThat(options.getModuleAccessors()).isSameAs(original);

    options.setCommonModuleAccessors(List.of("LocalMethod"));

    assertThat(options.getModuleAccessors().localMethods()).containsExactly("localmethod");
    assertThat(options.getModuleAccessors().moduleMethodPairs()).isEmpty();
    assertThat(original.moduleMethodPairs().get("commonuse")).containsExactly("module");
  }

  @Test
  void resetRestoresInitializedReferencesSnapshot() {
    var configuration = new LanguageServerConfiguration();
    configuration.setReferencesOptions(new ReferencesOptions(List.of("InitialMethod")));
    configuration.init();
    var changed = configuration.getReferencesOptions();
    changed.setCommonModuleAccessors(List.of("ChangedMethod"));
    configuration.setReferencesOptions(changed);

    configuration.reset();

    assertThat(configuration.getReferencesOptions().getModuleAccessors().localMethods()).containsExactly("initialmethod");
  }

  @Test
  void cachedPatternsFollowFileUpdateAndReset(@TempDir Path directory) throws IOException {
    var configuration = new LanguageServerConfiguration();
    var defaults = configuration.getReferencesOptions().getModuleAccessors();
    var file = directory.resolve("config.json");
    Files.writeString(file, """
      {"references": {"commonModuleAccessors": ["CustomModule.CustomMethod"]}}
      """);

    configuration.update(file.toFile());
    var updated = configuration.getReferencesOptions().getModuleAccessors();
    assertThat(updated.localMethods()).isEmpty();
    assertThat(updated.moduleMethodPairs()).containsOnlyKeys("custommodule");
    assertThat(updated.moduleMethodPairs().get("custommodule")).containsExactly("custommethod");

    configuration.reset();
    assertThat(configuration.getReferencesOptions().getModuleAccessors()).isEqualTo(defaults);
  }

  @Test
  void cacheIsNotPartOfSerializedSettingsOrEquality() {
    var configuration = new LanguageServerConfiguration();
    var mapper = new JsonMapper();
    var before = mapper.writeValueAsString(configuration);
    var hashBefore = configuration.hashCode();
    var descriptionBefore = configuration.toString();
    configuration.setReferencesOptions(configuration.getReferencesOptions());

    assertThat(mapper.writeValueAsString(configuration)).isEqualTo(before);
    assertThat(configuration.hashCode()).isEqualTo(hashBefore);
    assertThat(configuration).hasToString(descriptionBefore);
  }
}
