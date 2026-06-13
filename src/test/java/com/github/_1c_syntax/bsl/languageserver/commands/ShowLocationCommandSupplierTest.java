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
package com.github._1c_syntax.bsl.languageserver.commands;

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentCapabilities;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShowLocationCommandSupplierTest {

  private final URI uri = Absolute.uri("file:///tmp/module.bsl");
  private final Range range = Ranges.create(3, 4, 3, 10);

  @Test
  void shouldShowDocumentWhenClientSupportsIt() {

    // given
    var languageClient = mock(LanguageClient.class);
    var clientHolder = new LanguageClientHolder();
    clientHolder.connect(languageClient);

    var capabilitiesHolder = capabilitiesHolderWithShowDocumentSupport(true);

    var supplier = new ShowLocationCommandSupplier(clientHolder, capabilitiesHolder);
    supplier.handleInitializeEvent();
    var arguments = new ShowLocationCommandArguments(uri, supplier.getId(), range);

    // when
    Optional<Object> result = supplier.execute(arguments);

    // then
    assertThat(result).isEmpty();

    var captor = ArgumentCaptor.forClass(ShowDocumentParams.class);
    verify(languageClient).showDocument(captor.capture());

    var params = captor.getValue();
    assertThat(params.getUri()).isEqualTo(uri.toString());
    assertThat(params.getSelection()).isEqualTo(range);
    assertThat(params.getTakeFocus()).isTrue();
  }

  @Test
  void shouldNotShowDocumentWhenClientDoesNotSupportIt() {

    // given
    var languageClient = mock(LanguageClient.class);
    var clientHolder = new LanguageClientHolder();
    clientHolder.connect(languageClient);

    var capabilitiesHolder = capabilitiesHolderWithShowDocumentSupport(false);

    var supplier = new ShowLocationCommandSupplier(clientHolder, capabilitiesHolder);
    supplier.handleInitializeEvent();
    var arguments = new ShowLocationCommandArguments(uri, supplier.getId(), range);

    // when
    Optional<Object> result = supplier.execute(arguments);

    // then
    assertThat(result).isEmpty();
    verify(languageClient, never()).showDocument(any(ShowDocumentParams.class));
  }

  @Test
  void shouldUseCachedSupportFlagAndNotReReadCapabilitiesOnExecute() {

    // given
    var languageClient = mock(LanguageClient.class);
    var clientHolder = new LanguageClientHolder();
    clientHolder.connect(languageClient);

    var capabilitiesHolder = mock(ClientCapabilitiesHolder.class);
    when(capabilitiesHolder.getCapabilities())
      .thenReturn(Optional.of(clientCapabilitiesWithShowDocumentSupport(true)));

    var supplier = new ShowLocationCommandSupplier(clientHolder, capabilitiesHolder);
    supplier.handleInitializeEvent();
    var arguments = new ShowLocationCommandArguments(uri, supplier.getId(), range);

    // when
    supplier.execute(arguments);
    supplier.execute(arguments);

    // then
    // флаг прочитан из холдера один раз — на initialize, далее команда использует кэш
    verify(capabilitiesHolder, times(1)).getCapabilities();
    verify(languageClient, times(2)).showDocument(any(ShowDocumentParams.class));
  }

  private static ClientCapabilitiesHolder capabilitiesHolderWithShowDocumentSupport(boolean support) {
    var capabilitiesHolder = new ClientCapabilitiesHolder();
    capabilitiesHolder.setCapabilities(clientCapabilitiesWithShowDocumentSupport(support));

    return capabilitiesHolder;
  }

  private static ClientCapabilities clientCapabilitiesWithShowDocumentSupport(boolean support) {
    var window = new WindowClientCapabilities();
    window.setShowDocument(new ShowDocumentCapabilities(support));

    var clientCapabilities = new ClientCapabilities();
    clientCapabilities.setWindow(window);

    return clientCapabilities;
  }
}
