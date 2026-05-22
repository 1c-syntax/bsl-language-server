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
 * Регрессы для рекурсивного сбора implicit-записей в convention-обнаруженных
 * библиотеках (без {@code lib.config}). Покрывает сценарий разработчика
 * библиотеки (workspace ≡ корень исходников) и multi-package layout
 * с подсистемами, как у autumn/opentelemetry.
 */
@CleanupContextBeforeClassAndAfterClass
class ConventionalLibraryImplicitDiscoveryTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/convention-implicit-test";

  @Autowired
  private OScriptLibraryIndex index;

  @Test
  void topLevelConventionalClassesRemainExplicit() {
    // given
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);

    // when
    index.reindex(context);

    // then
    var publicConv = index.findByName("PublicConv").orElseThrow();
    assertThat(publicConv.implicit())
      .as("класс из src/Классы (top-level convention) регистрируется как explicit")
      .isFalse();
    var publicMod = index.findByName("PublicMod").orElseThrow();
    assertThat(publicMod.implicit())
      .as("модуль из src/Модули (top-level convention) регистрируется как explicit")
      .isFalse();
  }

  @Test
  void deeperConventionalClassPickedUpAsImplicit() {
    // given
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);

    // when
    index.reindex(context);

    // then
    var internal = index.findByName("InternalConv");
    assertThat(internal)
      .as("класс в src/internal/Классы за пределами top-level convention должен подцепиться рекурсивным сбором")
      .isPresent();
    assertThat(internal.get().implicit())
      .as("найденный неявно класс помечается implicit=true")
      .isTrue();
    assertThat(internal.get().kind())
      .as("kind определяется родительским каталогом: Классы → CLASS")
      .isEqualTo(OScriptLibraryIndex.EntryKind.CLASS);
  }

  @Test
  void oscriptModulesInsideConventionalLibraryIsSkipped() {
    // given fixture с oscript_modules/trans/Классы/TransConv.os внутри my-lib
    // (имитация транзитивной зависимости convention-библиотеки)
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);

    // when
    index.reindex(context);

    // then
    assertThat(index.findByName("TransConv"))
      .as("oscript_modules внутри обнаруженной convention-библиотеки не должен индексироваться " +
        "ни через ConventionalLibraryDiscovery.walk, ни через collectImplicitEntries")
      .isEmpty();
  }
}
