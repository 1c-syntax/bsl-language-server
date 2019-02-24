/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.cli;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import static org.github._1c_syntax.bsl.languageserver.BSLLSPLauncher.APP_NAME;

public class HelpCommand implements Command {

  private final Options options;

  public HelpCommand(Options options) {
    this.options = options;
  }

  @Override
  public int execute() {
    HelpFormatter formatter = new HelpFormatter();

    formatter.printHelp(APP_NAME, options, true);
    return 0;
  }
}
