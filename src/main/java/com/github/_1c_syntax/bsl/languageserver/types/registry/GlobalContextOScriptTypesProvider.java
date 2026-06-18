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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.utils.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Поставщик членов глобального контекста OneScript: наполняет
 * {@link TypeRegistry#GLOBAL_CONTEXT} для {@link FileType#OS} членами из
 * встроенных JSON-ресурсов OneScript (платформа bsl-context здесь не
 * применяется — OneScript-глобалы всегда из ресурсов):
 * <ul>
 *   <li>{@code builtin-oscript-globals.json} — глобальные функции/переменные;</li>
 *   <li>{@code builtin-oscript-platform-types.json} — {@code exposedAsGlobal}-типы
 *       (перечисления) как свойства-члены.</li>
 * </ul>
 * Зеркалит {@link GlobalContextTypesProvider} (BSL); OneScript-вариант выделен в
 * отдельный {@link PlatformTypesProvider} с {@link FileType#OS}, потому что члены
 * {@code GLOBAL_CONTEXT} регистрируются в разрезе языка провайдера.
 */
@Component
@WorkspaceScope
public class GlobalContextOScriptTypesProvider implements PlatformTypesProvider {

  private static final String OSCRIPT_GLOBALS_RESOURCE =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-globals.json";
  private static final String OSCRIPT_PLATFORM_TYPES_RESOURCE =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-platform-types.json";

  private final Lazy<List<TypeDecl>> cached = new Lazy<>(GlobalContextOScriptTypesProvider::build);

  @Override
  public Collection<TypeDecl> getTypes() {
    return cached.getOrCompute();
  }

  @Override
  public FileType getFileType() {
    return FileType.OS;
  }

  private static List<TypeDecl> build() {
    var members = new ArrayList<MemberDescriptor>(
      GlobalScopeProvider.globalContextMembers(OSCRIPT_GLOBALS_RESOURCE));
    members.addAll(BuiltinTypesJsonLoader.enumGlobalProperties(OSCRIPT_PLATFORM_TYPES_RESOURCE));
    if (members.isEmpty()) {
      return List.of();
    }
    return List.of(GlobalContextTypesProvider.globalContextDecl(members));
  }
}
