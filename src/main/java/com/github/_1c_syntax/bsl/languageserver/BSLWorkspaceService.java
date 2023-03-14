/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.providers.SymbolProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.beanutils.PropertyUtils;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class BSLWorkspaceService implements WorkspaceService {

  private final LanguageServerConfiguration configuration;
  private final SymbolProvider symbolProvider;

  @Override
  @SuppressWarnings("deprecation")
  public CompletableFuture<Either<List<? extends SymbolInformation>,List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
    return CompletableFuture.supplyAsync(() -> Either.forRight(symbolProvider.getSymbols(params)));
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {
    try {
      PropertyUtils.copyProperties(configuration, params.getSettings());
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    // no-op
  }
}
