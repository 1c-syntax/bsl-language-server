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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class ConfigurationTypesProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void registersCatalogTypesWithRuAndEnAliases() {
    initServerContext(PATH_TO_METADATA);
    // прогреваем lazy-конфигурацию
    context.getConfiguration();
    provider.tryRegister();

    var ru = typeRegistry.resolve("Справочники.Справочник1");
    var en = typeRegistry.resolve("Catalogs.Справочник1");

    assertThat(ru).isPresent();
    assertThat(en).isPresent();
    assertThat(en.get()).isEqualTo(ru.get());
  }

  @Test
  void registersCollectionNamespacesWithMetadataMembers() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    provider.tryRegister();

    var nsRu = typeRegistry.resolveNamespace("Справочники");
    var nsEn = typeRegistry.resolveNamespace("Catalogs");

    assertThat(nsRu).isPresent();
    assertThat(nsEn).isPresent();
    assertThat(nsEn.get()).isEqualTo(nsRu.get());

    var members = typeRegistry.getMembers(nsRu.get());
    assertThat(members)
      .extracting(m -> m.name())
      .contains("Справочник1");

    var member = members.stream()
      .filter(m -> "Справочник1".equals(m.name()))
      .findFirst()
      .orElseThrow();
    assertThat(member.returnType().qualifiedName()).isEqualTo("Справочники.Справочник1");
  }
}
