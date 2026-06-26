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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.lsp.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * Сборка LSP-команд навигации для линз (CodeLens) с учётом подключённого клиента.
 * <p>
 * Встроенные команды VS Code ({@code editor.action.goToLocations} /
 * {@code editor.action.showReferences}) нельзя вызвать из линзы напрямую: сервер
 * передаёт аргументы сырым JSON (URI строкой, {@link Position}/{@link Location}
 * обычными объектами), а команды требуют нативных типов VS Code и отвергают такой
 * вызов. Поэтому для VS Code и совместимых форков целью команды служит обёртка
 * расширения {@code language-1c-bsl}, оживляющая аргументы; прочие клиенты
 * (например, LSP4IJ, нативно эмулирующий {@code editor.action.showReferences})
 * получают стандартную команду редактора. Аргументы в обоих случаях одинаковы —
 * различается только идентификатор команды.
 *
 * @see <a href="https://github.com/microsoft/vscode-languageserver-node/issues/555">
 *   vscode-languageserver-node#555</a>
 */
@Component
@RequiredArgsConstructor
public class NavigationCommandBuilder {

  /** Клиентская команда-обёртка расширения для перехода к одной/нескольким целям. */
  static final String VS_CODE_GOTO_COMMAND = "language-1c-bsl.languageServer.gotoLocations";
  /** Клиентская команда-обёртка расширения для показа списка ссылок в поповере. */
  static final String VS_CODE_REFERENCES_COMMAND = "language-1c-bsl.languageServer.showReferences";
  /** Встроенная команда VS Code для перехода (одна цель — прыжок, несколько — поповер). */
  static final String BUILTIN_GOTO_COMMAND = "editor.action.goToLocations";
  /** Встроенная команда VS Code для показа списка ссылок в поповере. */
  static final String BUILTIN_REFERENCES_COMMAND = "editor.action.showReferences";

  /** Режим {@code editor.action.goToLocations}: прыжок к единственной цели. */
  private static final String MULTIPLE_GOTO = "goto";
  /** Режим {@code editor.action.goToLocations}: поповер при нескольких целях. */
  private static final String MULTIPLE_PEEK = "peek";

  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  /**
   * Закэшированный признак клиента на базе VS Code. Вычисляется один раз при
   * получении {@link LanguageServerInitializeRequestReceivedEvent}, чтобы не
   * читать {@link ClientCapabilitiesHolder} на каждый запрос линзы.
   */
  private boolean vsCodeLikeClient;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Один раз определяет и кэширует тип подключённого клиента: в момент события
   * {@link ClientCapabilitiesHolder} уже содержит {@code ClientInfo}.
   *
   * @param event Событие получения запроса инициализации.
   */
  @EventListener
  public void handleInitializeEvent(LanguageServerInitializeRequestReceivedEvent event) {
    vsCodeLikeClient = clientCapabilitiesHolder.isVsCodeLikeClient();
  }

  /**
   * Команда перехода к производителю(-ям): прыжок к единственной цели, поповер при нескольких.
   * Подходит прямой линзе «точка внедрения → производитель».
   *
   * @param title    Заголовок линзы.
   * @param uri      URI документа, из которого выполняется переход.
   * @param position Позиция курсора, от которой выполняется переход.
   * @param targets  Цели перехода (объявления производителей).
   * @return Команда с идентификатором, выбранным под клиента.
   */
  public Command gotoCommand(String title, URI uri, Position position, List<Location> targets) {
    var multiple = targets.size() == 1 ? MULTIPLE_GOTO : MULTIPLE_PEEK;
    var commandId = isVsCodeLike() ? VS_CODE_GOTO_COMMAND : BUILTIN_GOTO_COMMAND;
    return new Command(title, commandId, List.of(uri.toString(), position, targets, multiple));
  }

  /**
   * Команда показа списка использований в поповере. Подходит обратной линзе
   * «производитель → точки внедрения».
   *
   * @param title     Заголовок линзы.
   * @param uri       URI документа, из которого выполняется показ.
   * @param position  Позиция курсора, от которой выполняется показ.
   * @param locations Местоположения использований.
   * @return Команда с идентификатором, выбранным под клиента.
   */
  public Command referencesCommand(String title, URI uri, Position position, List<Location> locations) {
    var commandId = isVsCodeLike() ? VS_CODE_REFERENCES_COMMAND : BUILTIN_REFERENCES_COMMAND;
    return new Command(title, commandId, List.of(uri.toString(), position, locations));
  }

  /**
   * Является ли подключённый клиент редактором на базе VS Code (сам VS Code, Cursor, Antigravity
   * и т.п.), способным исполнять клиентские команды-обёртки расширения {@code language-1c-bsl}.
   *
   * @return {@code true}, если клиент совместим с VS Code; иначе {@code false}.
   */
  private boolean isVsCodeLike() {
    return vsCodeLikeClient;
  }
}
