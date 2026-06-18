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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LanguageClientAwareAppenderTest {

  @ParameterizedTest
  @CsvSource({
    "TRACE, Debug",
    "DEBUG, Debug",
    "INFO, Info",
    "WARN, Warning",
    "ERROR, Error"
  })
  void shouldMapLogbackLevelToMessageType(String levelName, String messageTypeName) throws IOException {
    // given
    var level = Level.valueOf(levelName);
    var expectedMessageType = MessageType.valueOf(messageTypeName);

    var languageClient = mock(LanguageClient.class);
    var clientHolder = mock(LanguageClientHolder.class);
    when(clientHolder.isConnected()).thenReturn(true);
    when(clientHolder.getClient()).thenReturn(Optional.of(languageClient));

    var appender = new LanguageClientAwareAppender();
    appender.setClientHolder(clientHolder);

    var event = mockEvent(level);

    // when
    appender.writeOut(event);

    // then
    var captor = ArgumentCaptor.forClass(MessageParams.class);
    verify(languageClient).logMessage(captor.capture());

    assertThat(captor.getValue().getType()).isEqualTo(expectedMessageType);
  }

  @Test
  void shouldNotSendToClientWhenNotConnected() throws IOException {
    // given
    var languageClient = mock(LanguageClient.class);
    var clientHolder = mock(LanguageClientHolder.class);
    when(clientHolder.isConnected()).thenReturn(false);

    var appender = new LanguageClientAwareAppender();
    appender.setClientHolder(clientHolder);
    appender.setEncoder(mock(ch.qos.logback.core.encoder.Encoder.class));

    var event = mockEvent(Level.DEBUG);

    // when
    appender.writeOut(event);

    // then
    verifyNoInteractions(languageClient);
  }

  private static ILoggingEvent mockEvent(Level level) {
    var event = mock(ILoggingEvent.class);
    when(event.getLevel()).thenReturn(level);
    when(event.getInstant()).thenReturn(Instant.EPOCH);
    when(event.getThreadName()).thenReturn("main");
    when(event.getLoggerName()).thenReturn("com.example.Logger");
    when(event.getFormattedMessage()).thenReturn("message");
    return event;
  }
}
