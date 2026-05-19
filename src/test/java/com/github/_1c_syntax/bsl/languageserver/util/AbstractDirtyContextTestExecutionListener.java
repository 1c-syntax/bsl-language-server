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
package com.github._1c_syntax.bsl.languageserver.util;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import org.springframework.core.Ordered;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

/**
 * Вспомогательный базовый класс для обработчиков, помечающих контекст как грязный.
 */
public class AbstractDirtyContextTestExecutionListener extends AbstractTestExecutionListener {
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  /**
   * Lite-cleanup: убираем все workspace-data, но НЕ перезагружаем Spring контекст.
   * <p>
   * {@link ServerContextProvider#clear()} итерирует по всем workspace'ам и для
   * каждого:
   * <ul>
   *   <li>{@code removeDocument(uri)} на каждом документе — публикует
   *       {@code ServerContextDocumentRemovedEvent} через AOP, на котором
   *       зависят downstream-индексы ({@code ReferenceIndex},
   *       {@code OScriptLibraryIndex}, и т.п.);</li>
   *   <li>{@code WorkspaceScope.removeWorkspace(uri)} — уничтожает
   *       workspace-scoped beans (TypeRegistry, GlobalScopeProvider,
   *       BslContextHolder, ...) с их destruction callbacks. При следующем
   *       обращении они создадутся заново.</li>
   * </ul>
   * <p>
   * Полный Spring teardown ({@code markApplicationContextDirty}) не делаем —
   * это дорого (~3–7s на цикл) и в подавляющем большинстве кейсов не нужно:
   * singleton-beans проектно не хранят per-workspace state, поэтому переживание
   * между тест-классами для них корректно. Старое поведение остаётся в {@link #dirtyContext}
   * на случай, если какому-то тесту понадобится «полный» сброс.
   */
  protected static void liteCleanup(TestContext testContext) {
    try {
      var provider = testContext.getApplicationContext().getBean(ServerContextProvider.class);
      provider.clear();
    } catch (Exception e) {
      // Ignore if provider not available yet
    }
    try {
      var configuration = testContext.getApplicationContext().getBean(LanguageServerConfiguration.class);
      configuration.reset();
    } catch (Exception e) {
      // Ignore if configuration not available yet
    }
  }

  /**
   * «Жёсткий» cleanup: помимо {@link #liteCleanup(TestContext)} помечает
   * ApplicationContext как dirty — Spring пересоздаст его перед следующим тестом.
   * Дорого; использовать только если lite-вариант не подходит.
   */
  protected static void dirtyContext(TestContext testContext) {
    liteCleanup(testContext);
    testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
    testContext.setAttribute(DependencyInjectionTestExecutionListener.REINJECT_DEPENDENCIES_ATTRIBUTE, Boolean.TRUE);
  }
}
