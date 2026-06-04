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
package com.github._1c_syntax.bsl.languageserver.references.model;

import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище зарегистрированных аннотаций для одного workspace.
 * <p>
 * Имена аннотаций в BSL регистронезависимы, поэтому ключи хранятся в нижнем
 * регистре, а поиск ведётся регистронезависимо.
 */
@Component
@WorkspaceScope
public class AnnotationRepository {

  private final Map<String, AnnotationSymbol> annotations = new ConcurrentHashMap<>();

  /**
   * Зарегистрировать аннотацию.
   *
   * @param annotationSymbol символ аннотации
   */
  public void register(AnnotationSymbol annotationSymbol) {
    annotations.put(key(annotationSymbol.getName()), annotationSymbol);
  }

  /**
   * Найти аннотацию по имени (регистронезависимо).
   *
   * @param name имя аннотации
   * @return символ аннотации, если найден
   */
  public Optional<AnnotationSymbol> findByName(String name) {
    return Optional.ofNullable(annotations.get(key(name)));
  }

  private static String key(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  /**
   * Удалить все аннотации, зарегистрированные из документа с указанным URI.
   *
   * @param uri URI документа
   */
  public void removeByUri(URI uri) {
    annotations.values()
      .removeIf(annotationSymbol -> annotationSymbol.getOwner().getUri().equals(uri));
  }

  /**
   * Очистить все зарегистрированные аннотации.
   */
  public void clear() {
    annotations.clear();
  }
}
