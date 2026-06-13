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

import lombok.experimental.UtilityClass;
import org.eclipse.lsp4j.ClientInfo;

import java.util.Set;

/**
 * Доменное знание о «VS Code-совместимом клиенте» для линз (CodeLens).
 * <p>
 * Редакторы на базе VS Code работают через VS Code extension API: ставят расширение
 * {@code language-1c-bsl} с командами-обёртками навигации и поддерживают встроенные команды
 * запуска и отладки тестов. По этому признаку линзы выбирают идентификатор команды навигации
 * и решают, показывать ли линзы запуска/отладки тестов.
 */
@UtilityClass
public class VsCodeCompatibleClient {

  /**
   * Канонический набор имён ({@link ClientInfo#getName()}, оно же {@code vscode.env.appName})
   * редакторов на базе VS Code. Единый источник истины о «VS Code-совместимом клиенте».
   */
  private static final Set<String> CLIENT_NAMES = Set.of(
    "Visual Studio Code",
    "Cursor",
    "Antigravity",
    "code-server"
  );

  /**
   * Является ли редактор с указанным именем VS Code-совместимым по каноническому набору имён.
   * Имя — это {@link ClientInfo#getName()} (оно же {@code vscode.env.appName}).
   *
   * @param clientName Имя клиента из {@link ClientInfo#getName()}.
   * @return {@code true}, если имя входит в канонический набор VS Code-совместимых редакторов;
   *   иначе {@code false}.
   */
  public static boolean isVsCodeCompatible(String clientName) {
    return CLIENT_NAMES.contains(clientName);
  }
}
