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
import com.github._1c_syntax.bsl.languageserver.references.WorkspaceNames;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

/**
 * {@link WorkspaceNames} поверх глобального контекста ({@link GlobalScopeProvider}) и индекса
 * библиотек OneScript ({@link OScriptLibraryIndex}).
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class WorkspaceNamesImpl implements WorkspaceNames {

  private final GlobalScopeProvider globalScopeProvider;
  private final OScriptLibraryIndex oScriptLibraryIndex;

  @Override
  public boolean hasModuleType(URI documentUri) {
    return globalScopeProvider.moduleTypeRefByUri(documentUri).isPresent();
  }

  @Override
  public boolean isGlobalFunction(String name, FileType fileType) {
    return globalScopeProvider.globalFunction(name, fileType).isPresent();
  }

  @Override
  public boolean isGlobalProperty(String name, FileType fileType) {
    return globalScopeProvider.globalProperty(name, fileType).isPresent();
  }

  @Override
  public Optional<URI> libraryClassUri(String className) {
    return oScriptLibraryIndex.findClassUri(className);
  }

  @Override
  public Optional<URI> libraryModuleUri(String moduleName) {
    return oScriptLibraryIndex.findModuleUri(moduleName);
  }
}
