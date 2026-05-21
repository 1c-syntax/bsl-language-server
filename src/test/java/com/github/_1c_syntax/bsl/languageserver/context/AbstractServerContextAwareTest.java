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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.nio.file.Path;

@SpringBootTest
public abstract class AbstractServerContextAwareTest {

  private static final URI EMPTY_WORKSPACE_URI = Absolute.path("src/test/resources/empty-workspace").toUri();

  @Autowired
  protected ServerContextProvider serverContextProvider;

  protected ServerContext context;

  /**
   * Включается изнутри {@link #initServerContextOnce(Path)} и отключает
   * per-method очистку workspace в {@link #cleanupWorkspace()} (и сброс поля
   * {@link #context} в {@link #resetContext()}). Workspace, инициализированный
   * один раз, переживает между методами класса; очистка между тест-классами
   * остаётся через
   * {@link com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass}
   * (его {@code beforeTestClass}/{@code afterTestClass} зовёт liteCleanup).
   * <p>
   * Подкласс с {@code initServerContextOnce}-сценарием обязан:
   * <ul>
   *   <li>звать {@link #initServerContextOnce(Path)} в {@code @BeforeEach} (флаг
   *       выставляется автоматически);</li>
   *   <li>не мутировать workspace state между тест-методами (read-only тесты).</li>
   * </ul>
   * Уместно для тестов с тяжёлой populateContext-фикстурой (например,
   * {@code PATH_TO_METADATA}): per-method recreate workspace стоит ~1.5s
   * из-за повторной регистрации workspace-scoped провайдеров типов
   * ({@code ConfigurationTypesProvider}, {@code ConfigurationModuleMembersProvider} и др.).
   * <p>
   * JUnit 5 default lifecycle (PER_METHOD): новая инстанция тест-класса на
   * каждый метод. Поле устанавливается в {@code @BeforeEach} (child phase) и
   * читается в {@code @AfterEach} той же инстанции, поэтому per-method-lifecycle
   * не мешает.
   */
  protected boolean cleanupAfterClass = false;

  @BeforeEach
  void resetContext() {
    if (!cleanupAfterClass) {
      context = null;
    }
  }

  @AfterEach
  void cleanupWorkspace() {
    if (!cleanupAfterClass) {
      serverContextProvider.clear();
    }
  }

  /**
   * Initialize empty server context without metadata.
   * <p>
   * Возвращает {@link #cleanupAfterClass} в {@code false}: явное пересоздание
   * workspace говорит, что подкласс хочет per-method-семантику, и парные
   * {@code @AfterEach}/{@code @BeforeEach}-cleanup'ы должны снова работать.
   */
  protected void initServerContext() {
    cleanupAfterClass = false;
    serverContextProvider.clear();
    context = serverContextProvider.addWorkspace(EMPTY_WORKSPACE_URI);
    WorkspaceContextHolder.set(context.getWorkspaceUri());
  }

  /**
   * Идемпотентный аналог {@link #initServerContext(Path)}: если workspace уже
   * зарегистрирован в {@link ServerContextProvider}, переиспользует его без
   * пере-populate'а. Включает флаг {@link #cleanupAfterClass} для текущей
   * инстанции, чтобы {@code @AfterEach} не сбросил workspace.
   * Использовать в {@code @BeforeEach} подкласса.
   * <p>
   * Проверка идёт по URI workspace в провайдере, а не по полю {@link #context}:
   * JUnit 5 по умолчанию (LIFECYCLE.PER_METHOD) создаёт новый instance
   * тест-класса на каждый метод, поэтому поле всегда null на новой инстанции,
   * а state провайдера — singleton-bean — переживает между методами.
   */
  protected void initServerContextOnce(Path configurationRoot) {
    var uri = Absolute.uri(configurationRoot.toUri());
    var existing = serverContextProvider.getServerContext(uri);
    if (existing.isPresent()) {
      context = existing.get();
      WorkspaceContextHolder.set(uri);
    } else {
      initServerContext(configurationRoot);
    }
    // Включаем флаг ПОСЛЕ initServerContext (он его сбрасывает в false).
    cleanupAfterClass = true;
  }

  protected void initServerContext(String path) {
    var configurationRoot = Absolute.path(path);
    initServerContext(configurationRoot, true);
  }

  protected void initServerContext(Path configurationRoot) {
    initServerContext(configurationRoot, true);
  }

  protected void initServerContext(String path, boolean populate) {
    var configurationRoot = Absolute.path(path);
    initServerContext(configurationRoot, populate);
  }

  protected void initServerContext(Path configurationRoot, boolean populate) {
    cleanupAfterClass = false;
    serverContextProvider.clear();
    var uri = Absolute.uri(configurationRoot.toUri());
    context = serverContextProvider.addWorkspace(uri);
    context.setConfigurationRoot(configurationRoot);
    WorkspaceContextHolder.set(context.getWorkspaceUri());
    if (populate) {
      context.populateContext();
    }
  }
}
