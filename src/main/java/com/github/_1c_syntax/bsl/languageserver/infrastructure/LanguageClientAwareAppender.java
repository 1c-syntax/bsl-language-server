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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import jakarta.annotation.Nullable;
import lombok.Setter;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.IOException;

public class LanguageClientAwareAppender
  extends ConsoleAppender<ILoggingEvent> {

  protected static LanguageClientAwareAppender INSTANCE;

  @Setter
  @Nullable
  private LanguageClientHolder clientHolder;

  public LanguageClientAwareAppender() {
    super();
    // hacky hack
    INSTANCE = this;
  }

  @Override
  protected void writeOut(ILoggingEvent event) throws IOException {
    if (clientHolder != null && clientHolder.isConnected()) {
      LanguageClient languageClient = clientHolder.getClient().orElseThrow();

      var params = new MessageParams(MessageType.Info, event.getFormattedMessage());
      languageClient.logMessage(params);

      return;
    }
    super.writeOut(event);
  }
}
