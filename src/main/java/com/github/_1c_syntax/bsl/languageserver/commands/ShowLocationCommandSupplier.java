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
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ShowDocumentCapabilities;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Поставщик серверной команды перехода к одной целевой локации.
 * <p>
 * В отличие от навигационных линз, исполняющих встроенные команды редактора
 * ({@code editor.action.goToLocations} и т.п.) с сырыми JSON-аргументами, эта команда
 * перекладывает переход на сервер: сервер сам инициирует запрос {@code window/showDocument}
 * к клиенту с URI документа, выделяемым диапазоном и {@code takeFocus = true}.
 * <p>
 * Команда работает только при заявленной клиентом возможности {@code window.showDocument.support};
 * если возможность не заявлена, команда безопасно ничего не делает.
 */
@Component
@RequiredArgsConstructor
public class ShowLocationCommandSupplier implements CommandSupplier<ShowLocationCommandArguments> {

  private final LanguageClientHolder clientHolder;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  // Кэшируется на initialize. Флаг поддержки клиентом возможности window.showDocument.
  // Если клиент её не заявил, команда становится безопасным no-op.
  private boolean showDocumentSupported;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Кэширует клиентскую возможность {@code window.showDocument.support}, определяющую,
   * способен ли клиент обработать инициированный сервером запрос {@code window/showDocument}.
   */
  @EventListener(LanguageServerInitializeRequestReceivedEvent.class)
  public void handleInitializeEvent() {
    showDocumentSupported = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getWindow)
      .map(WindowClientCapabilities::getShowDocument)
      .map(ShowDocumentCapabilities::isSupport)
      .orElse(Boolean.FALSE);
  }

  /**
   * Признак того, что подключённый клиент заявил поддержку {@code window/showDocument} и способен
   * обработать инициированный сервером запрос открытия документа.
   * <p>
   * Значение кэшируется на {@link LanguageServerInitializeRequestReceivedEvent}; используется как
   * самой командой, так и потребителями (например, навигационными линзами), решающими, можно ли
   * перенаправить переход на серверную команду {@code showLocation}.
   *
   * @return {@code true}, если клиент поддерживает {@code window/showDocument}; иначе {@code false}.
   */
  public boolean isShowDocumentSupported() {
    return showDocumentSupported;
  }

  /**
   * Получение класса аргументов команды.
   *
   * @return Класс аргументов команды.
   */
  @Override
  public Class<ShowLocationCommandArguments> getCommandArgumentsClass() {
    return ShowLocationCommandArguments.class;
  }

  /**
   * Выполнить переход к целевой локации через запрос {@code window/showDocument}.
   * <p>
   * Если клиент не заявил поддержку {@code window.showDocument}, команда является безопасным no-op.
   *
   * @param arguments Аргументы команды с URI документа и выделяемым диапазоном.
   * @return Всегда пустой результат: команда не возвращает данных клиенту.
   */
  @Override
  public Optional<Object> execute(ShowLocationCommandArguments arguments) {
    if (!showDocumentSupported) {
      return Optional.empty();
    }

    var params = new ShowDocumentParams(arguments.getUri().toString());
    params.setSelection(arguments.getRange());
    params.setTakeFocus(true);

    clientHolder.execIfConnected(client -> client.showDocument(params));

    return Optional.empty();
  }
}
