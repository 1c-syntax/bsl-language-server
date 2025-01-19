/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.InitializeParams;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@CacheConfig(cacheNames = "testIds")
public abstract class AbstractRunTestsCodeLensSupplier<T extends CodeLensData>
  implements CodeLensSupplier<T> {

  protected final LanguageServerConfiguration configuration;

  private boolean clientIsSupported;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Анализирует тип подключенного клиента и управляет применимостью линзы.
   *
   * @param event Событие
   */
  @EventListener
  @CacheEvict(allEntries = true)
  public void handleEvent(LanguageServerInitializeRequestReceivedEvent event) {
    var clientName = Optional.of(event)
      .map(LanguageServerInitializeRequestReceivedEvent::getParams)
      .map(InitializeParams::getClientInfo)
      .map(ClientInfo::getName)
      .orElse("");
    clientIsSupported = "Visual Studio Code".equals(clientName);
  }

  @EventListener
  @CacheEvict(allEntries = true)
  public void handleLanguageServerConfigurationChange(LanguageServerConfigurationChangedEvent event) {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Cacheable
  public boolean isApplicable(DocumentContext documentContext) {
    var uri = documentContext.getUri();
    var testSources = getTestSources(documentContext);

    return documentContext.getFileType() == FileType.OS
      && testSources.stream().anyMatch(testSource -> isInside(uri, testSource))
      && clientIsSupported;
  }

  public Set<URI> getTestSources(DocumentContext documentContext) {
    var configurationRoot = Optional.ofNullable(documentContext.getServerContext().getConfigurationRoot())
      .map(Path::toString)
      .orElse("");

    return configuration.getCodeLensOptions().getTestRunnerAdapterOptions().getTestSources()
      .stream()
      .map(testDir -> Path.of(configurationRoot, testDir))
      .map(path -> Absolute.path(path).toUri())
      .collect(Collectors.toSet());
  }

  private static boolean isInside(URI childURI, URI parentURI) {
    return !parentURI.relativize(childURI).isAbsolute();
  }
}
