/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.ServerInfo;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Выводит версию приложения
 * Ключ команды:
 *  -v, (--version)
 */
@Slf4j
@Command(
  name = "version",
  aliases = {"-v", "--version"},
  description = "Print version",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2022|@")
@Component
@RequiredArgsConstructor
public class VersionCommand implements Callable<Integer> {

  private final ServerInfo serverInfo;

  public Integer call() {
    String version = serverInfo.getVersion();
    if (version.isEmpty()) {
      return 1;
    }

    System.out.printf(
      "version: %s%n",
      version
    );

    return 0;
  }
}
