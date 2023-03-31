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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.commands.CommandArguments;
import com.github._1c_syntax.bsl.languageserver.commands.CommandSupplier;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Провайдер, обрабатывающий запросы {@code workspace/executeCommans}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_executeCommand">Execute a command specification</a>.
 */
@Component
@RequiredArgsConstructor
public class CommandProvider {

  private final Map<String, CommandSupplier<CommandArguments>> commandSuppliersById;
  private final ObjectMapper objectMapper;
  private final CodeLensProvider codeLensProvider;

  public Object executeCommand(CommandArguments arguments) {
    var commandId = arguments.getId();

    var commandSupplier = commandSuppliersById.get(commandId);
    if (commandSupplier == null) {
      throw new RuntimeException("Unknown command id: " + commandId);
    }

    var result = commandSupplier
      .execute(arguments)
      .orElse(null);

    CompletableFuture.runAsync(() -> {
      if (commandSupplier.refreshCodeLensesAfterExecuteCommand()) {
        codeLensProvider.refreshCodeLenses();
      }
    });

    return result;
  }

  public List<String> getCommandIds() {
    return List.copyOf(commandSuppliersById.keySet());
  }

  @SneakyThrows
  public CommandArguments extractArguments(ExecuteCommandParams codeLens) {
    var rawArguments = codeLens.getArguments();

    if (rawArguments.isEmpty()) {
      throw new RuntimeException("Command arguments is empty");
    }

    var rawArgument = rawArguments.get(0);

    if (rawArgument instanceof CommandArguments) {
      return (CommandArguments) rawArgument;
    }

    return objectMapper.readValue(rawArgument.toString(), CommandArguments.class);
  }

}
