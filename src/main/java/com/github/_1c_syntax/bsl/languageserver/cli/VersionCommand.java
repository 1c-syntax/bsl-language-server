/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Выводит версию приложения
 * Ключ команды:
 *  -v, (--version)
 */
@Slf4j
@picocli.CommandLine.Command(
  name = "version",
  aliases = {"-v", "--version"},
  description = "Print version",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2020|@")
public class VersionCommand implements Callable<Integer> {

  public Integer call() {
    final InputStream mfStream = Thread.currentThread()
      .getContextClassLoader()
      .getResourceAsStream("META-INF/MANIFEST.MF");

    Manifest manifest = new Manifest();
    try {
      manifest.read(mfStream);
    } catch (IOException e) {
      LOGGER.error("Can't read manifest", e);
      return 1;
    }

    System.out.print(
      String.format(
        "version: %s%n",
        manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION)
      ));

    return 0;
  }
}
