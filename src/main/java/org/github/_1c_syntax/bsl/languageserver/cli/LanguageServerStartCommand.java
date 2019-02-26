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

import org.apache.commons.cli.CommandLine;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.github._1c_syntax.bsl.languageserver.BSLLanguageServer;
import org.github._1c_syntax.bsl.languageserver.settings.LanguageServerSettings;

import java.io.InputStream;
import java.io.OutputStream;

public class LanguageServerStartCommand implements Command {
  private CommandLine cmd;

  public LanguageServerStartCommand(CommandLine cmd) {
    this.cmd = cmd;
  }

  @Override
  public int execute() {
    String diagnosticLanguage = cmd.getOptionValue("diagnosticLanguage", "en");
    LanguageServerSettings settings = new LanguageServerSettings(diagnosticLanguage);

    LanguageServer server = new BSLLanguageServer(settings);
    InputStream in = System.in;
    OutputStream out = System.out;

    Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, in, out);

    LanguageClient client = launcher.getRemoteProxy();
    ((LanguageClientAware) server).connect(client);

    launcher.startListening();
    return -1;
  }
}
