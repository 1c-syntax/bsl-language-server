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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class TestSourcesProviderTest extends AbstractServerContextAwareTest {

  private static final Path CONFIGURATION_ROOT = Path.of("src/test/resources/codelenses");

  @Autowired
  private TestSourcesProvider testSourcesProvider;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    initServerContext();
    publishConfigurationChanged();
  }

  @Test
  void testTestSourcesAreResolvedAgainstConfigurationRoot() {
    // given
    setTestSources(Set.of("tests", "features"));

    // when
    var testSources = testSourcesProvider.getTestSources(CONFIGURATION_ROOT);

    // then
    assertThat(testSources).containsExactlyInAnyOrder(
      Absolute.path(CONFIGURATION_ROOT.resolve("tests")).toUri(),
      Absolute.path(CONFIGURATION_ROOT.resolve("features")).toUri()
    );
  }

  @Test
  void testTestSourcesAreCachedUntilConfigurationChanged() {
    // given
    setTestSources(Set.of("tests"));
    var cached = testSourcesProvider.getTestSources(CONFIGURATION_ROOT);

    // when
    setTestSources(Set.of("features"));
    var beforeEvent = testSourcesProvider.getTestSources(CONFIGURATION_ROOT);
    publishConfigurationChanged();
    var afterEvent = testSourcesProvider.getTestSources(CONFIGURATION_ROOT);

    // then
    assertThat(beforeEvent).isEqualTo(cached);
    assertThat(afterEvent).containsExactly(Absolute.path(CONFIGURATION_ROOT.resolve("features")).toUri());
  }

  private void setTestSources(Set<String> testSources) {
    context.getLanguageServerConfiguration()
      .getCodeLensOptions()
      .getTestRunnerAdapterOptions()
      .setTestSources(testSources);
  }

  private void publishConfigurationChanged() {
    eventPublisher.publishEvent(new LanguageServerConfigurationChangedEvent(context.getLanguageServerConfiguration()));
  }
}
