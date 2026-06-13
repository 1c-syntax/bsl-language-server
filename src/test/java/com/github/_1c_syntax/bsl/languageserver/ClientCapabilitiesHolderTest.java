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
package com.github._1c_syntax.bsl.languageserver;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FoldingRangeCapabilities;
import org.eclipse.lsp4j.FoldingRangeSupportCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientCapabilitiesHolderTest {

  @Test
  void collapsedTextSupportedWhenClientDeclaresIt() {

    // given
    var holder = new ClientCapabilitiesHolder();
    holder.setCapabilities(capabilitiesWithCollapsedText(true));

    // when
    var supported = holder.isFoldingRangeCollapsedTextSupported();

    // then
    assertThat(supported).isTrue();
  }

  @Test
  void collapsedTextNotSupportedWhenClientDeclaresFalse() {

    // given
    var holder = new ClientCapabilitiesHolder();
    holder.setCapabilities(capabilitiesWithCollapsedText(false));

    // when
    var supported = holder.isFoldingRangeCollapsedTextSupported();

    // then
    assertThat(supported).isFalse();
  }

  @Test
  void collapsedTextNotSupportedWhenCapabilitiesAbsent() {

    // given
    var holder = new ClientCapabilitiesHolder();

    // when
    var supported = holder.isFoldingRangeCollapsedTextSupported();

    // then
    assertThat(supported).isFalse();
  }

  @Test
  void collapsedTextNotSupportedWhenFoldingRangeCapabilitiesAbsent() {

    // given
    var holder = new ClientCapabilitiesHolder();
    var clientCapabilities = new ClientCapabilities();
    clientCapabilities.setTextDocument(new TextDocumentClientCapabilities());
    holder.setCapabilities(clientCapabilities);

    // when
    var supported = holder.isFoldingRangeCollapsedTextSupported();

    // then
    assertThat(supported).isFalse();
  }

  private static ClientCapabilities capabilitiesWithCollapsedText(boolean supported) {
    var foldingRangeSupportCapabilities = new FoldingRangeSupportCapabilities(supported);
    var foldingRangeCapabilities = new FoldingRangeCapabilities();
    foldingRangeCapabilities.setFoldingRange(foldingRangeSupportCapabilities);
    var textDocumentClientCapabilities = new TextDocumentClientCapabilities();
    textDocumentClientCapabilities.setFoldingRange(foldingRangeCapabilities);
    var clientCapabilities = new ClientCapabilities();
    clientCapabilities.setTextDocument(textDocumentClientCapabilities);
    return clientCapabilities;
  }
}
