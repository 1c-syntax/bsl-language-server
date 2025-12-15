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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import lombok.Setter;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Map;

/**
 * Расширение штатного {@link ConsoleAppender}, выводящего сообщения
 * в {@link org.eclipse.lsp4j.services.LanguageClient}, если он подключен,
 * или в штатные потоки вывода в обратном случае.
 */
public class LanguageClientAwareAppender
  extends ConsoleAppender<ILoggingEvent> {

  /**
   * Singletone-like хранилище проинициализированного инфраструктурой Logback аппендера
   * для последующего возврата его через {@link LogbackConfiguration#languageClientAwareAppender()}.
   */
  protected static LanguageClientAwareAppender INSTANCE;

  private static final Map<Level, MessageType> loggingLevels = Map.of(
    Level.TRACE, MessageType.Log,
    Level.DEBUG, MessageType.Log,
    Level.ERROR, MessageType.Error,
    Level.INFO, MessageType.Info,
    Level.WARN, MessageType.Warning
  );

  /**
   * Хранилище возможно подключенного LanguageClient.
   */
  @Nullable
  @Setter(onMethod_ = {@Autowired})
  private LanguageClientHolder clientHolder;

  /**
   * Конструктор по умолчанию.
   * <p>
   * Сохраняет сконструированный объект в переменную {@link LanguageClientAwareAppender#INSTANCE}.
   */
  public LanguageClientAwareAppender() {
    super();
    // hacky hack
    INSTANCE = this;
  }

  /**
   * Общий метод вывода информации, проверяющий наличие подключенного LanguageClient.
   *
   * @param event Логируемое событие
   * @throws IOException Выбрасывает исключение в случае ошибок записи в стандартные потоки вывода.
   */
  @Override
  protected void writeOut(ILoggingEvent event) throws IOException {
    if (clientHolder != null && clientHolder.isConnected()) {
      var languageClient = clientHolder.getClient().orElseThrow();

      var messageType = loggingLevels.getOrDefault(event.getLevel(), MessageType.Log);
      String message = "[%s - %s] [%s] [%s]: %s".formatted(
        event.getLevel(),
        event.getInstant(),
        event.getThreadName(),
        event.getLoggerName(),
        event.getFormattedMessage()
      );
      var params = new MessageParams(messageType, message);
      languageClient.logMessage(params);

      return;
    }
    super.writeOut(event);
  }
}
