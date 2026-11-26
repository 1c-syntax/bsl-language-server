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

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Fallback-провайдер платформенных типов BSL из JSON-ресурса, упакованного
 * вместе с bsl-language-server. Используется, когда полноценный источник
 * через {@link BslContextPlatformTypesProvider} (синтакс-помощник
 * установленной платформы) недоступен — например, на CI или у пользователя
 * без 1С. Содержит минимальный набор примитивов и ключевых коллекций для
 * базового вывода типов из литералов и {@code Новый X()}.
 * <p>
 * Парсинг JSON-ресурса вынесен в общий {@link BuiltinTypesJsonLoader} —
 * тот же, что использует OneScript-провайдер.
 */
@Component
@WorkspaceScope
public class BuiltinPlatformTypesProvider implements PlatformTypesProvider {

  private static final String RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-platform-types.json";

  /**
   * Кэш десериализованных деклараций. JSON-ресурс упакован в jar и неизменен,
   * поэтому парсим его один раз на JVM (десятки workspace-контекстов в тестах
   * иначе перепарсивают одно и то же — ощутимый оверхед памяти/CPU).
   */
  private static final List<TypeDecl> CACHED_TYPES = List.copyOf(BuiltinTypesJsonLoader.load(RESOURCE_PATH));

  private final List<TypeDecl> types;
  private final BslContextHolder bslContextHolder;

  public BuiltinPlatformTypesProvider(BslContextHolder bslContextHolder) {
    this.bslContextHolder = bslContextHolder;
    this.types = CACHED_TYPES;
  }

  /**
   * Возвращает встроенный JSON-fallback только тогда, когда полноценный
   * {@code bsl-context}-источник недоступен (платформа 1С не установлена
   * либо парсинг HBK не удался). Если bsl-context дал данные —
   * {@link BslContextPlatformTypesProvider} полностью покрывает то же
   * множество типов, поэтому здесь возвращаем пустой список, чтобы
   * избежать дублей и устаревшей JSON-разметки.
   */
  @Override
  public Collection<TypeDecl> getTypes() {
    if (bslContextHolder.get().isPresent()) {
      return List.of();
    }
    return types;
  }

  @Override
  public FileType getFileType() {
    return FileType.BSL;
  }
}
