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

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Workspace-scoped реестр сопоставлений
 * {@code URI .os-файла → ModuleType (OScriptClass | OScriptModule)}.
 * <p>
 * Используется как fallback для {@code DocumentContext.computeModuleType()}:
 * {@code Configuration.getModuleTypeByURI(uri)} ничего не знает о файлах
 * OneScript-библиотек и возвращает {@link ModuleType#UNKNOWN}. Этот резолвер
 * наполняется компонентом {@link OScriptLibraryIndex} в процессе индексации
 * библиотек и позволяет различать классы и модули OneScript на уровне
 * {@link com.github._1c_syntax.bsl.languageserver.context.DocumentContext}.
 */
@Component
@WorkspaceScope
public class OScriptModuleTypeResolver {

  private final ConcurrentMap<URI, ModuleType> typesByUri = new ConcurrentHashMap<>();

  /**
   * Сохранить тип модуля для .os-файла.
   *
   * @param uri        URI файла (ожидается нормализованным абсолютным)
   * @param moduleType {@link ModuleType#OScriptClass} или {@link ModuleType#OScriptModule}
   */
  public void register(URI uri, ModuleType moduleType) {
    // Если для одного .os файла регистрируется несколько ролей (модуль и класс),
    // оставляем ПЕРВУЮ — иначе DocumentContext поменяет moduleType "под собой"
    // после addMdoRefByUri и ссылка перестанет резолвиться через
    // ServerContext.getDocument(mdoRef, moduleType).
    typesByUri.putIfAbsent(uri, moduleType);
  }

  /**
   * Получить ранее зарегистрированный тип модуля.
   */
  public Optional<ModuleType> resolve(URI uri) {
    return Optional.ofNullable(typesByUri.get(uri));
  }

  /**
   * Удалить запись о файле (например, при удалении/пере-индексации).
   */
  public void unregister(URI uri) {
    typesByUri.remove(uri);
  }

  /**
   * Полная очистка реестра (например, перед re-index'ом всего workspace).
   */
  public void clear() {
    typesByUri.clear();
  }
}
