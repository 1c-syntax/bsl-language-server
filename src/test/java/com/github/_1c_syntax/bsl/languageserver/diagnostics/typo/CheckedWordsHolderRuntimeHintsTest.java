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
package com.github._1c_syntax.bsl.languageserver.diagnostics.typo;

import com.github._1c_syntax.bsl.languageserver.util.AotHints;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Статусы слов хранятся в персистентном кэше Java-сериализацией и регистрируются для native-image.
 */
class CheckedWordsHolderRuntimeHintsTest {

  @Test
  void testWordStatusIsRegisteredForSerialization() {
    var hints = AotHints.hintsOf(CheckedWordsHolder.class);

    assertThat(RuntimeHintsPredicates.serialization().onType(WordStatus.class)).accepts(hints);
  }
}
