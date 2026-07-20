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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ThreadLocal-хранилище текущего workspace URI и имени.
 * Используется {@link WorkspaceBeanScope} для определения ключа scope,
 * а также для именования потоков в per-workspace ForkJoinPool.
 * <p>
 * URI должен быть нормализован вызывающим кодом перед передачей в {@code set()}.
 * <p>
 * Предпочтительный способ использования — через try-with-resources:
 * <pre>{@code
 * try (var ctx = WorkspaceContextHolder.forUri(workspaceUri)) {
 *   // workspace-scoped proxy beans resolve здесь
 * }
 * }</pre>
 * или через wrap-методы:
 * <pre>{@code
 * WorkspaceContextHolder.run(workspaceUri, () -> {
 *   // workspace-scoped proxy beans resolve здесь
 * });
 *
 * var result = WorkspaceContextHolder.call(workspaceUri, () -> computeResult());
 * }</pre>
 */
public final class WorkspaceContextHolder {

  private static final ThreadLocal<URI> CURRENT_WORKSPACE = new ThreadLocal<>();
  private static final ThreadLocal<String> CURRENT_WORKSPACE_NAME = new ThreadLocal<>();
  private static final Map<URI, String> WORKSPACE_NAMES = new ConcurrentHashMap<>();

  private WorkspaceContextHolder() {
    // utility class
  }

  /**
   * Зарегистрировать имя workspace для данного URI.
   * После регистрации вызовы {@link #forUri(URI)} будут использовать это имя.
   */
  public static void registerWorkspace(URI workspaceUri, String name) {
    WORKSPACE_NAMES.put(workspaceUri, name);
  }

  /**
   * Удалить регистрацию workspace.
   */
  public static void unregisterWorkspace(URI workspaceUri) {
    WORKSPACE_NAMES.remove(workspaceUri);
  }

  /**
   * Проверить, зарегистрирован ли workspace.
   */
  public static boolean isRegistered(URI workspaceUri) {
    return workspaceUri != null && WORKSPACE_NAMES.containsKey(workspaceUri);
  }

  /**
   * Создать AutoCloseable-контекст workspace с URI и именем.
   * При закрытии восстанавливает предыдущее значение ThreadLocal.
   */
  public static WorkspaceContext forUri(URI workspaceUri, String workspaceName) {
    var previous = new WorkspaceContext(get(), getName());
    set(workspaceUri, workspaceName);
    return previous;
  }

  /**
   * Создать AutoCloseable-контекст workspace с URI.
   * Имя извлекается из последнего сегмента пути URI.
   * При закрытии восстанавливает предыдущее значение ThreadLocal.
   */
  public static WorkspaceContext forUri(URI workspaceUri) {
    var previous = new WorkspaceContext(get(), getName());
    set(workspaceUri);
    return previous;
  }

  /**
   * Выполнить действие в контексте workspace с URI.
   * Имя извлекается из последнего сегмента пути URI.
   * При завершении восстанавливает предыдущее значение ThreadLocal.
   */
  public static void run(URI workspaceUri, Runnable action) {
    try (var ctx = forUri(workspaceUri)) {
      action.run();
    }
  }

  /**
   * Выполнить действие в контексте workspace с URI и именем.
   * При завершении восстанавливает предыдущее значение ThreadLocal.
   */
  public static void run(URI workspaceUri, String workspaceName, Runnable action) {
    try (var ctx = forUri(workspaceUri, workspaceName)) {
      action.run();
    }
  }

  /**
   * Вычислить значение в контексте workspace с URI.
   * Имя извлекается из последнего сегмента пути URI.
   * При завершении восстанавливает предыдущее значение ThreadLocal.
   */
  public static <T> T call(URI workspaceUri, Callable<T> action) throws Exception {
    try (var ctx = forUri(workspaceUri)) {
      return action.call();
    }
  }

  /**
   * Вычислить значение в контексте workspace с URI и именем.
   * При завершении восстанавливает предыдущее значение ThreadLocal.
   */
  public static <T> T call(URI workspaceUri, String workspaceName, Callable<T> action) throws Exception {
    try (var ctx = forUri(workspaceUri, workspaceName)) {
      return action.call();
    }
  }

  /**
   * Установить workspace URI и имя. URI должен быть уже нормализован вызывающим кодом.
   */
  public static void set(URI workspaceUri, String workspaceName) {
    CURRENT_WORKSPACE.set(workspaceUri);
    CURRENT_WORKSPACE_NAME.set(workspaceName);
  }

  /**
   * Установить workspace URI. Имя берётся из реестра зарегистрированных workspace.
   * URI должен быть уже нормализован и зарегистрирован через {@link #registerWorkspace}.
   *
   * @throws IllegalStateException если workspace не зарегистрирован
   */
  public static void set(URI workspaceUri) {
    var name = WORKSPACE_NAMES.get(workspaceUri);
    if (name == null) {
      throw new IllegalStateException(
        "Workspace not registered: " + workspaceUri + ". Call registerWorkspace() first."
      );
    }
    CURRENT_WORKSPACE.set(workspaceUri);
    CURRENT_WORKSPACE_NAME.set(name);
  }

  public static @Nullable URI get() {
    return CURRENT_WORKSPACE.get();
  }

  public static @Nullable String getName() {
    return CURRENT_WORKSPACE_NAME.get();
  }

  public static void clear() {
    CURRENT_WORKSPACE.remove();
    CURRENT_WORKSPACE_NAME.remove();
  }


  /**
   * AutoCloseable-обёртка для workspace-контекста.
   * При закрытии восстанавливает предыдущее значение ThreadLocal.
   */
  public static final class WorkspaceContext implements AutoCloseable {
    private final @Nullable URI previousUri;
    private final @Nullable String previousName;

    private WorkspaceContext(@Nullable URI previousUri, @Nullable String previousName) {
      this.previousUri = previousUri;
      this.previousName = previousName;
    }

    @Override
    public void close() {
      if (previousUri != null) {
        CURRENT_WORKSPACE.set(previousUri);
        CURRENT_WORKSPACE_NAME.set(previousName);
      } else {
        clear();
      }
    }
  }
}
