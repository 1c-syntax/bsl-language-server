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

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom Spring Scope для per-workspace бинов.
 * <p>
 * Ключ scope — workspace URI из {@link WorkspaceContextHolder}.
 * Если workspace URI не установлен, выбрасывается исключение.
 */
@Slf4j
public class WorkspaceBeanScope implements Scope {

  public static final String SCOPE_NAME = "workspace";

  private final ConcurrentHashMap<URI, Map<String, Object>> store = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<URI, Map<String, Runnable>> destructionCallbacks = new ConcurrentHashMap<>();

  @Override
  public Object get(String name, ObjectFactory<?> objectFactory) {
    var key = resolveKey();
    var beans = getOrCreate(store, key);
    var existing = beans.get(name);
    if (existing != null) {
      return existing;
    }
    // ВАЖНО: фабрика бина может рекурсивно запросить другой workspace-scoped бин
    // (например, через CGLIB-прокси), поэтому здесь нельзя использовать
    // computeIfAbsent — он держит bin-lock CHM и при рекурсивном входе с другим
    // ключом, попадающим в тот же bin, приводит к дедлоку между параллельными
    // потоками. Создаём бин ВНЕ блокировки и атомарно регистрируем через
    // putIfAbsent; в редкой гонке другой поток вернёт уже созданный экземпляр.
    var created = objectFactory.getObject();
    var previous = beans.putIfAbsent(name, created);
    return previous != null ? previous : created;
  }

  private static <K, V> Map<K, V> getOrCreate(ConcurrentHashMap<URI, Map<K, V>> outer, URI key) {
    var inner = outer.get(key);
    if (inner != null) {
      return inner;
    }
    Map<K, V> fresh = new ConcurrentHashMap<>();
    var previous = outer.putIfAbsent(key, fresh);
    return previous != null ? previous : fresh;
  }

  @Override
  public @Nullable Object remove(String name) {
    var key = resolveKey();
    // Контракт Scope.remove: вместе с бином снимаем и его destruction callback,
    // иначе он останется в карте и повторно выполнится при removeWorkspace().
    var callbacks = destructionCallbacks.get(key);
    if (callbacks != null) {
      callbacks.remove(name);
    }
    var beans = store.get(key);
    return beans != null ? beans.remove(name) : null;
  }

  @Override
  public void registerDestructionCallback(String name, Runnable callback) {
    var key = resolveKey();
    getOrCreate(destructionCallbacks, key).put(name, callback);
  }

  @Override
  public @Nullable Object resolveContextualObject(String key) {
    return null;
  }

  @Override
  public @Nullable String getConversationId() {
    var uri = WorkspaceContextHolder.get();
    return uri != null ? uri.toString() : null;
  }

  /**
   * Удалить все бины workspace, предварительно вызвав destruction callbacks.
   */
  public void removeWorkspace(URI workspaceUri) {
    var callbacks = destructionCallbacks.remove(workspaceUri);
    if (callbacks != null) {
      // Каждый callback изолируем: падение одного не должно срывать остальные
      // и последующую очистку store (иначе workspace-scoped бины протекают между запусками/тестами).
      callbacks.forEach((name, callback) -> {
        try {
          callback.run();
        } catch (RuntimeException e) {
          LOGGER.warn("Destruction callback for bean '{}' in workspace {} failed", name, workspaceUri, e);
        }
      });
    }
    store.remove(workspaceUri);
  }

  /**
   * Get all instances of a bean across all workspaces.
   *
   * @param beanName the bean name
   * @param type the expected type
   * @return collection of all instances
   */
  public <T> Collection<T> getAllInstances(String beanName, Class<T> type) {
    return store.values().stream()
      .map(beans -> beans.get(beanName))
      .filter(Objects::nonNull)
      .map(type::cast)
      .toList();
  }

  /**
   * @return URI всех зарегистрированных в scope workspace'ов. Полезно для test-cleanup'а,
   * который должен пройтись по каждому scope для сброса состояния workspace-scoped beans.
   */
  public Collection<URI> getRegisteredWorkspaceUris() {
    return java.util.List.copyOf(store.keySet());
  }

  private static URI resolveKey() {
    var key = WorkspaceContextHolder.get();
    if (key == null) {
      throw new IllegalStateException(
        "Workspace context is not set. Use WorkspaceContextHolder.forUri() before accessing workspace-scoped beans."
      );
    }
    return key;
  }
}
