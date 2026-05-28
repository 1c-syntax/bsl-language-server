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
package com.github._1c_syntax.bsl.languageserver.diagnostics.platform;

import com.github._1c_syntax.bsl.support.CompatibilityMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Чисто-функциональные ветви {@link PlatformMemberCalls}: парсинг версий,
 * sentinel {@code "*"} (oscript-конвенция), префикс-фильтр «Удалить»/«Delete».
 * Полный путь резолва покрывается тестами диагностик {@code DeprecatedMethodCall}
 * и {@code UnavailableMemberCall}.
 */
class PlatformMemberCallsTest {

  @Test
  void firesDeprecatedTriggersWhenTargetReachesVersion() {
    var target = new CompatibilityMode("8.3.20");
    assertThat(PlatformMemberCalls.firesDeprecated("8.3.10", target)).isTrue();
    assertThat(PlatformMemberCalls.firesDeprecated("8.3.20", target)).isTrue();
  }

  @Test
  void firesDeprecatedSilentForOlderTarget() {
    var target = new CompatibilityMode("8.3.10");
    assertThat(PlatformMemberCalls.firesDeprecated("8.3.17", target)).isFalse();
  }

  @Test
  void firesDeprecatedSilentForEmptyOrInvalidVersion() {
    var target = new CompatibilityMode("8.3.20");
    assertThat(PlatformMemberCalls.firesDeprecated("", target)).isFalse();
    assertThat(PlatformMemberCalls.firesDeprecated(null, target)).isFalse();
    assertThat(PlatformMemberCalls.firesDeprecated("8.3", target)).isFalse();
    assertThat(PlatformMemberCalls.firesDeprecated("кривая", target)).isFalse();
  }

  @Test
  void firesDeprecatedFiresOnSentinelRegardlessOfTarget() {

    // oscript-конвенция: deprecated без версионирования.
    var anyTarget = new CompatibilityMode("8.3.10");
    assertThat(PlatformMemberCalls.firesDeprecated(PlatformMemberCalls.DEPRECATED_ALWAYS, anyTarget)).isTrue();
  }

  @Test
  void firesUnavailableTriggersWhenTargetBelowSince() {
    var target = new CompatibilityMode("8.3.10");
    assertThat(PlatformMemberCalls.firesUnavailable("8.3.18", target)).isTrue();
  }

  @Test
  void firesUnavailableSilentAtSinceOrNewer() {
    var target = new CompatibilityMode("8.3.18");
    assertThat(PlatformMemberCalls.firesUnavailable("8.3.18", target)).isFalse();
    assertThat(PlatformMemberCalls.firesUnavailable("8.3.10", new CompatibilityMode("8.3.20"))).isFalse();
  }

  @Test
  void firesUnavailableSilentForEmptyOrInvalidVersion() {
    var target = new CompatibilityMode("8.3.10");
    assertThat(PlatformMemberCalls.firesUnavailable("", target)).isFalse();
    assertThat(PlatformMemberCalls.firesUnavailable(null, target)).isFalse();
    assertThat(PlatformMemberCalls.firesUnavailable("8.3", target)).isFalse();
  }

  @Test
  void hasDeletedPrefixCoversRuAndEnPrefixes() {
    assertThat(PlatformMemberCalls.hasDeletedPrefix("УдалитьСтароеПоле")).isTrue();
    assertThat(PlatformMemberCalls.hasDeletedPrefix("DeleteOld")).isTrue();
    // регистронезависимая префиксная проверка
    assertThat(PlatformMemberCalls.hasDeletedPrefix("удалитьЛегаси")).isTrue();
    assertThat(PlatformMemberCalls.hasDeletedPrefix("DELETElegacy")).isTrue();
  }

  @Test
  void hasDeletedPrefixRejectsBareAndShorterNames() {
    // длина равна префиксу — не считается «помеченным», т.к. нет постфикса.
    assertThat(PlatformMemberCalls.hasDeletedPrefix("Удалить")).isFalse();
    assertThat(PlatformMemberCalls.hasDeletedPrefix("Delete")).isFalse();
    // не префикс
    assertThat(PlatformMemberCalls.hasDeletedPrefix("Поле")).isFalse();
    assertThat(PlatformMemberCalls.hasDeletedPrefix(null)).isFalse();
    assertThat(PlatformMemberCalls.hasDeletedPrefix("")).isFalse();
  }
}
