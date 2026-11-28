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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Встроенный ресурс, который не читается (битый JSON или данные не той формы), не валит
 * загрузку: каждый загрузчик пишет ошибку в журнал и отдаёт пустые данные — сервер стартует
 * с урезанными сведениями, а не не стартует вовсе.
 */
class BuiltinResourceLoadersTest {

  private static final String DIRECTORY = "com/github/_1c_syntax/bsl/languageserver/types/registry/";

  @ParameterizedTest
  @ValueSource(strings = {"broken-builtin.json", "wrong-shape-builtin.json"})
  void globalMembersLoaderLogsAndReturnsNothing(String resource) {
    assertLogsErrorAndReturnsEmpty(GlobalScopeProvider.class,
      () -> GlobalScopeProvider.globalContextMembers(DIRECTORY + resource));
  }

  @ParameterizedTest
  @ValueSource(strings = {"broken-builtin.json", "wrong-shape-builtin.json"})
  void platformTypesLoaderLogsAndReturnsNothing(String resource) {
    assertLogsErrorAndReturnsEmpty(BuiltinTypesJsonLoader.class,
      () -> BuiltinTypesJsonLoader.load(DIRECTORY + resource));
  }

  @ParameterizedTest
  @ValueSource(strings = {"broken-builtin.json", "wrong-shape-builtin.json"})
  void queryTablesLoaderLogsAndReturnsNothing(String resource) {
    assertLogsErrorAndReturnsEmpty(BuiltinQueryTables.class,
      () -> BuiltinQueryTables.read(DIRECTORY + resource));
  }

  @ParameterizedTest
  @ValueSource(strings = {"broken-builtin.json"})
  void keywordsLoaderLogsAndReturnsNothing(String resource) {
    assertLogsErrorAndReturnsEmpty(KeywordMetadataLoader.class,
      () -> KeywordMetadataLoader.load(DIRECTORY + resource).descriptions().keySet());
  }

  private static void assertLogsErrorAndReturnsEmpty(Class<?> loader, Supplier<Collection<?>> load) {
    var log = (Logger) LoggerFactory.getLogger(loader);
    var logged = new ListAppender<ILoggingEvent>();
    logged.start();
    log.addAppender(logged);
    Collection<?> result;
    try {
      result = load.get();
    } finally {
      log.detachAppender(logged);
    }

    assertThat(result).isEmpty();
    assertThat(logged.list).anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
  }
}
