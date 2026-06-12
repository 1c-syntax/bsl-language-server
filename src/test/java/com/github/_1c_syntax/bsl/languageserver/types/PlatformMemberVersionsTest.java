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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.platform.V8PlatformOptions;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.support.CompatibilityMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Версионная применимость платформенного члена {@link PlatformMemberVersions}:
 * парсинг версий, sentinel {@code "*"} (oscript-конвенция), выбор целевой версии
 * платформы (явная настройка vs режим совместимости конфигурации).
 */
class PlatformMemberVersionsTest {

  @Test
  void firesDeprecatedTriggersWhenTargetReachesVersion() {
    var target = new CompatibilityMode("8.3.20");
    assertThat(PlatformMemberVersions.firesDeprecated("8.3.10", target)).isTrue();
    assertThat(PlatformMemberVersions.firesDeprecated("8.3.20", target)).isTrue();
  }

  @Test
  void firesDeprecatedSilentForOlderTarget() {
    var target = new CompatibilityMode("8.3.10");
    assertThat(PlatformMemberVersions.firesDeprecated("8.3.17", target)).isFalse();
  }

  @Test
  void firesDeprecatedSilentForEmptyOrInvalidVersion() {
    var target = new CompatibilityMode("8.3.20");
    assertThat(PlatformMemberVersions.firesDeprecated("", target)).isFalse();
    assertThat(PlatformMemberVersions.firesDeprecated(null, target)).isFalse();
    assertThat(PlatformMemberVersions.firesDeprecated("кривая", target)).isFalse();
  }

  @Test
  void firesDeprecatedNormalizesTwoComponentToLastPatchOfFamily() {
    // «Устарел с 8.2» по семантике СП = «устарел к последнему патчу 8.2», т.е. 8.2.99.
    // Любая 8.3+ платформа — после этого, диагностика срабатывает.
    var target8320 = new CompatibilityMode("8.3.20");
    assertThat(PlatformMemberVersions.firesDeprecated("8.2", target8320)).isTrue();
    assertThat(PlatformMemberVersions.firesDeprecated("8.3", target8320)).isFalse(); // 8.3.99 > 8.3.20

    // Промежуточная версия внутри того же семейства не должна давать ложное срабатывание.
    var target8205 = new CompatibilityMode("8.2.5");
    assertThat(PlatformMemberVersions.firesDeprecated("8.2", target8205)).isFalse();

    // Target ниже семейства устаревания — точно не срабатывает.
    var target810 = new CompatibilityMode("8.1.0");
    assertThat(PlatformMemberVersions.firesDeprecated("8.2", target810)).isFalse();
  }

  @Test
  void firesUnavailableNormalizesTwoComponentToFirstPatchOfFamily() {
    // «Доступно с 8.2» = «доступно с первого патча 8.2», т.е. 8.2.0.
    // На любой target >= 8.2.0 — доступно (не срабатывает unavailable).
    var target8205 = new CompatibilityMode("8.2.5");
    assertThat(PlatformMemberVersions.firesUnavailable("8.2", target8205)).isFalse();

    // На target ниже семейства — недоступно (срабатывает).
    var target815 = new CompatibilityMode("8.1.5");
    assertThat(PlatformMemberVersions.firesUnavailable("8.2", target815)).isTrue();
  }

  @Test
  void firesDeprecatedFiresOnSentinelRegardlessOfTarget() {

    // oscript-конвенция: deprecated без версионирования.
    var anyTarget = new CompatibilityMode("8.3.10");
    assertThat(PlatformMemberVersions.firesDeprecated(PlatformMemberVersions.DEPRECATED_ALWAYS, anyTarget)).isTrue();
  }

  @Test
  void firesUnavailableTriggersWhenTargetBelowSince() {
    var target = new CompatibilityMode("8.3.10");
    assertThat(PlatformMemberVersions.firesUnavailable("8.3.18", target)).isTrue();
  }

  @Test
  void firesUnavailableSilentAtSinceOrNewer() {
    var target = new CompatibilityMode("8.3.18");
    assertThat(PlatformMemberVersions.firesUnavailable("8.3.18", target)).isFalse();
    assertThat(PlatformMemberVersions.firesUnavailable("8.3.10", new CompatibilityMode("8.3.20"))).isFalse();
  }

  @Test
  void firesUnavailableSilentForEmptyOrInvalidVersion() {
    var target = new CompatibilityMode("8.3.10");
    assertThat(PlatformMemberVersions.firesUnavailable("", target)).isFalse();
    assertThat(PlatformMemberVersions.firesUnavailable(null, target)).isFalse();
    assertThat(PlatformMemberVersions.firesUnavailable("8.3", target)).isFalse();
  }

  @Test
  void targetCompatibilityModePrefersExplicitOption() {

    // given — задана явная настройка v8platform.targetVersion=8.3.20.
    var doc = mock(DocumentContext.class);
    var config = mock(LanguageServerConfiguration.class);
    var v8opts = mock(V8PlatformOptions.class);
    when(config.getV8PlatformOptions()).thenReturn(v8opts);
    when(v8opts.getTargetVersion()).thenReturn("8.3.20");

    // when
    var mode = PlatformMemberVersions.targetCompatibilityMode(doc, config);

    // then — конфиг-режим не запрашивается, отдан explicit.
    assertThat(CompatibilityMode.compareTo(mode, new CompatibilityMode("8.3.20"))).isZero();
  }

  @Test
  void targetCompatibilityModeFallsBackToConfigurationMode() {

    // given — explicit не задан / невалиден; CompatibilityMode берётся из конфигурации.
    var doc = mock(DocumentContext.class);
    var config = mock(LanguageServerConfiguration.class);
    var v8opts = mock(V8PlatformOptions.class);
    when(config.getV8PlatformOptions()).thenReturn(v8opts);
    when(v8opts.getTargetVersion()).thenReturn("");
    var serverContext = mock(ServerContext.class);
    var mdConfig = mock(CF.class);
    when(doc.getServerContext()).thenReturn(serverContext);
    when(serverContext.getConfiguration()).thenReturn(mdConfig);
    var configMode = new CompatibilityMode("8.3.14");
    when(mdConfig.getCompatibilityMode()).thenReturn(configMode);

    // when
    var mode = PlatformMemberVersions.targetCompatibilityMode(doc, config);

    // then
    assertThat(mode).isSameAs(configMode);
  }
}
