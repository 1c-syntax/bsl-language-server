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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессы для рекурсивного сбора implicit-записей внутри каталога-библиотеки,
 * найденной через manifest ({@code lib.config}).
 */
@CleanupContextBeforeClassAndAfterClass
class OScriptLibraryIndexImplicitDiscoveryTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/implicit-test";

  @Autowired
  private OScriptLibraryIndex index;

  @Test
  void manifestEntriesAreExplicit() {
    // given
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);

    // when
    index.reindex(context);

    // then
    var publicEntry = index.findByName("PublicHello").orElseThrow();
    assertThat(publicEntry.implicit())
      .as("manifest-объявленная запись не должна быть implicit")
      .isFalse();
  }

  @Test
  void undeclaredOsInConventionalDirRegisteredAsImplicit() {
    // given
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);

    // when
    index.reindex(context);

    // then
    var autoFound = index.findByName("AutoFound");
    assertThat(autoFound)
      .as("необъявленный .os в src/internal/Классы должен подцепиться рекурсивным сбором")
      .isPresent();
    assertThat(autoFound.get().implicit())
      .as("найденная неявно запись помечается implicit=true")
      .isTrue();
    assertThat(autoFound.get().kind())
      .as("kind определяется родительским каталогом convention'а: Классы → CLASS")
      .isEqualTo(OScriptLibraryIndex.EntryKind.CLASS);
  }

  @Test
  void fileOutsideConventionalDirIsNotPickedUpAsImplicit() {
    // given fixture, в которой src/InternalSecret.os лежит прямо в src/
    // (не в Классы/Модули) и в manifest не объявлен — implicit-сборщик не должен
    // его регистрировать.
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);

    // when
    index.reindex(context);

    // then
    assertThat(index.findByName("InternalSecret"))
      .as("файлы вне convention-каталогов не должны автоматически попадать в индекс")
      .isEmpty();
  }
}
