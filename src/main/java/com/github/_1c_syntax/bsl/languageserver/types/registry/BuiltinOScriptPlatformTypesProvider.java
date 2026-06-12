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
 * Платформенные типы OneScript-движка (классы и системные перечисления),
 * сконвертированные из {@code oscriptStdLib.json} проекта
 * {@code vsc-language-1c-bsl}. Регистрируется как параллельный
 * {@link PlatformTypesProvider} наряду с {@link BuiltinPlatformTypesProvider}.
 * <p>
 * Поведение и устройство ресурса полностью совпадает с
 * {@code builtin-platform-types.json}, поэтому используется тот же loader.
 * Различение языкового скоупа (BSL vs OS vs BOTH) — отдельная задача
 * {@code language-gating}, в этом провайдере не реализуется.
 */
@Component
@WorkspaceScope
public class BuiltinOScriptPlatformTypesProvider implements PlatformTypesProvider {

  private static final String RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-platform-types.json";

  /** JSON парсится общим {@link BuiltinTypesJsonLoader} один раз на JVM. */
  private static final List<TypeDecl> CACHED_TYPES = List.copyOf(BuiltinTypesJsonLoader.load(RESOURCE_PATH));

  private final List<TypeDecl> types;

  public BuiltinOScriptPlatformTypesProvider() {
    this.types = CACHED_TYPES;
  }

  @Override
  public Collection<TypeDecl> getTypes() {
    return types;
  }

  @Override
  public FileType getFileType() {
    return FileType.OS;
  }
}
