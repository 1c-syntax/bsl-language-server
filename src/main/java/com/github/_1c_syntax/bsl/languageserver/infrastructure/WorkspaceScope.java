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

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom Spring Scope для per-workspace бинов.
 * <p>
 * Ключ scope — workspace URI из {@link WorkspaceContextHolder}.
 * Когда workspace URI не установлен (например, при старте приложения),
 * используется дефолтный ключ {@code __default__}.
 */
public class WorkspaceScope implements Scope {

  public static final String SCOPE_NAME = "workspace";
  static final String DEFAULT_WORKSPACE_KEY = "__default__";

  private final ConcurrentHashMap<String, Map<String, Object>> store = new ConcurrentHashMap<>();

  @Override
  public Object get(String name, ObjectFactory<?> objectFactory) {
    var key = resolveKey();
    return store
      .computeIfAbsent(key, k -> new ConcurrentHashMap<>())
      .computeIfAbsent(name, k -> objectFactory.getObject());
  }

  @Override
  @Nullable
  public Object remove(String name) {
    var key = resolveKey();
    var beans = store.get(key);
    return beans != null ? beans.remove(name) : null;
  }

  @Override
  public void registerDestructionCallback(String name, Runnable callback) {
    // destruction callbacks not needed for workspace-scoped beans
  }

  @Override
  @Nullable
  public Object resolveContextualObject(String key) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId() {
    return WorkspaceContextHolder.get();
  }

  /**
   * Удалить все бины workspace.
   */
  public void removeWorkspace(String workspaceUri) {
    store.remove(workspaceUri);
  }

  private static String resolveKey() {
    return Optional.ofNullable(WorkspaceContextHolder.get()).orElse(DEFAULT_WORKSPACE_KEY);
  }
}
