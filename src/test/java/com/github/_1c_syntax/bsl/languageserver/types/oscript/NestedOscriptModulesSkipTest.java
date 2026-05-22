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
 * При обнаружении библиотек в {@code <workspace>/oscript_modules/<lib>}
 * транзитивные зависимости внутри {@code <lib>/oscript_modules/<lib2>} не
 * должны попадать в индекс верхнего workspace: только сам workspace и
 * библиотеки в его собственном {@code oscript_modules/} являются корнями
 * для {@link LibConfigDiscovery}.
 */
@CleanupContextBeforeClassAndAfterClass
class NestedOscriptModulesSkipTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/nested-deps-test";

  @Autowired
  private OScriptLibraryIndex index;

  @Test
  void directDependencyIndexedTransitiveSkipped() {
    // given workspace с oscript_modules/direct-dep (manifest есть)
    // и transitive-dep лежит под direct-dep/oscript_modules (тоже с manifest'ом),
    // имитируя случай "у моей зависимости есть свои зависимости".
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);

    // when
    index.reindex(context);

    // then
    assertThat(index.findByName("DirectClass"))
      .as("прямая зависимость из workspace/oscript_modules должна попасть в индекс")
      .isPresent();
    assertThat(index.findByName("TransitiveClass"))
      .as("транзитивная зависимость внутри oscript_modules направильной зависимости " +
        "не должна попадать в индекс верхнего workspace")
      .isEmpty();
  }
}
