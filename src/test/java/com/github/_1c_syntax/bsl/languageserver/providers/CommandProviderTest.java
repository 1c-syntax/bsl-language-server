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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.commands.CommandSupplier;
import com.github._1c_syntax.bsl.languageserver.commands.DefaultCommandArguments;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CommandProviderTest {

  @Autowired
  private CommandProvider commandProvider;

  @Test
  void commandsIdsContainsTestCommand() {
    // given
    // TestCommandSupplier test component.

    // when
    var commandIds = commandProvider.getCommandIds();

    // then
    assertThat(commandIds).contains("test");
  }

  @Test
  void executeCommandArgumentsExtractedCorrectly() {
    // given
    var rawCommandArguments = "{ \"id\": \"test\", \"uri\": \"someUri\"}";
    var params = new ExecuteCommandParams("Some command", List.of(rawCommandArguments));

    // when
    var commandArguments = commandProvider.extractArguments(params);

    // then
    assertThat(commandArguments)
      .isOfAnyClassIn(DefaultCommandArguments.class)
    ;
  }

  @Test
  void commandExecutesCorrectly() {
    // given
    var commandArguments = new DefaultCommandArguments(URI.create("fake:///fake-uri"), "test");

    // when
    var result = commandProvider.executeCommand(commandArguments);

    // then
    assertThat(result)
      .isEqualTo(1);
  }

  @TestConfiguration
  static class Configuration {
    @Bean
    CommandSupplier<DefaultCommandArguments> commandSupplier() {
      return new TestCommandSupplier();
    }
  }

  static class TestCommandSupplier implements CommandSupplier<DefaultCommandArguments> {
    @Override
    public Class<DefaultCommandArguments> getCommandArgumentsClass() {
      return DefaultCommandArguments.class;
    }

    @Override
    public Optional<Object> execute(DefaultCommandArguments arguments) {
      return Optional.of(1);
    }
  }
}