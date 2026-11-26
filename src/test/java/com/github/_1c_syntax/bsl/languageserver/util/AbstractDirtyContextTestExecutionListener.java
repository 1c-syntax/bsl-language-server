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
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceBeanScope;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
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
   *   <li>{@code WorkspaceBeanScope.removeWorkspace(uri)} — уничтожает
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
    ServerContextProvider provider;
    WorkspaceBeanScope workspaceScope;
    LanguageServerConfiguration configBean;
    try {
      provider = testContext.getApplicationContext().getBean(ServerContextProvider.class);
      workspaceScope = testContext.getApplicationContext().getBean(WorkspaceBeanScope.class);
      configBean = testContext.getApplicationContext().getBean(LanguageServerConfiguration.class);
    } catch (Exception e) {
      return; // Spring контекст ещё не готов.
    }

    // Workspace-scoped beans живы пока scope их держит. Сбрасываем конфигурацию КАЖДОГО
    // workspace в scope (включая искусственный file:///test-workspace из
    // WorkspaceContextTestExecutionListener'а), затем сносим всё через provider.clear().
    // Перебираем uris из самого WorkspaceBeanScope, а не из provider.getAllContexts() —
    // часть workspace'ов (test-default) проходят мимо ServerContextProvider.
    for (var uri : workspaceScope.getRegisteredWorkspaceUris()) {
      // two-arg forUri — чтобы не зависеть от наличия URI в WORKSPACE_NAMES
      // (для async-propagated workspace'ов запись там может отсутствовать).
      try (var ctx = WorkspaceContextHolder.forUri(uri, uri.toString())) {
        configBean.reset();
      } catch (Exception e) {
        // Workspace мог быть уже уничтожен — пропускаем.
      }
    }

    provider.clear();

    // provider.clear() уничтожает workspace-scoped beans только для тех URI, что были
    // зарегистрированы через addWorkspace (живут в provider.contexts). Виртуальные
    // workspace'ы — например, file:///test-workspace из WorkspaceContextTestExecutionListener'а
    // или async-propagated — обходят provider, но создают beans в WorkspaceBeanScope.store.
    // Без явного removeWorkspace их TypeRegistry / OScriptLibraryIndex / GlobalScopeProvider
    // переживают cleanup и подкидывают флэйк (см. ConventionalLibraryDiscoveryTest pollution).
    for (var uri : workspaceScope.getRegisteredWorkspaceUris()) {
      workspaceScope.removeWorkspace(uri);
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
