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

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.commands.ShowLocationCommandArguments;
import com.github._1c_syntax.bsl.languageserver.commands.ShowLocationCommandSupplier;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NavigationCommandBuilderTest {

  private static final URI URI_VALUE = Absolute.uri("file:///module.os");
  private static final Position POSITION = new Position(1, 4);

  @Mock
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  @Mock
  private ShowLocationCommandSupplier showLocationCommandSupplier;

  private NavigationCommandBuilder newBuilder() {
    return new NavigationCommandBuilder(clientCapabilitiesHolder, showLocationCommandSupplier);
  }

  private void connectClient(String clientName) {
    when(clientCapabilitiesHolder.getClientInfo())
      .thenReturn(Optional.of(new ClientInfo(clientName, "1.0.0")));
  }

  @Test
  void gotoCommandForVsCodeLikeClientUsesWrapperCommand() {
    // given
    connectClient("Cursor");
    var builder = newBuilder();
    var targets = List.of(location(10));

    // when
    var command = builder.gotoCommand("title", URI_VALUE, POSITION, targets);

    // then
    assertThat(command.getCommand()).isEqualTo(NavigationCommandBuilder.VS_CODE_GOTO_COMMAND);
    assertThat(command.getArguments())
      .containsExactly(URI_VALUE.toString(), POSITION, targets, "goto");
  }

  @Test
  void gotoCommandForCodeServerUsesWrapperCommand() {
    // given: code-server — VS Code-совместимый клиент с расширением language-1c-bsl
    connectClient("code-server");
    var builder = newBuilder();

    // when
    var command = builder.gotoCommand("title", URI_VALUE, POSITION, List.of(location(10)));

    // then
    assertThat(command.getCommand()).isEqualTo(NavigationCommandBuilder.VS_CODE_GOTO_COMMAND);
  }

  @Test
  void gotoCommandForOtherClientUsesBuiltinCommand() {
    // given
    connectClient("Neovim");
    var builder = newBuilder();
    var targets = List.of(location(10));

    // when
    var command = builder.gotoCommand("title", URI_VALUE, POSITION, targets);

    // then
    assertThat(command.getCommand()).isEqualTo(NavigationCommandBuilder.BUILTIN_GOTO_COMMAND);
  }

  @Test
  void gotoCommandForUnknownClientUsesBuiltinCommand() {
    // given
    when(clientCapabilitiesHolder.getClientInfo()).thenReturn(Optional.empty());
    var builder = newBuilder();
    var targets = List.of(location(10));

    // when
    var command = builder.gotoCommand("title", URI_VALUE, POSITION, targets);

    // then
    assertThat(command.getCommand()).isEqualTo(NavigationCommandBuilder.BUILTIN_GOTO_COMMAND);
  }

  @Test
  void gotoCommandForSingleTargetWithShowDocumentSupportUsesShowLocationCommand() {
    // given: не-VS-Code-клиент, заявивший window/showDocument; одна цель перехода
    connectClient("Neovim");
    lenient().when(showLocationCommandSupplier.isShowDocumentSupported()).thenReturn(true);
    lenient().when(showLocationCommandSupplier.getId()).thenReturn("showLocation");
    lenient().when(showLocationCommandSupplier.createCommand(anyString(), any(ShowLocationCommandArguments.class)))
      .thenCallRealMethod();
    var builder = newBuilder();
    var target = location(10);

    // when
    var command = builder.gotoCommand("title", URI_VALUE, POSITION, List.of(target));

    // then: переход идёт серверной командой showLocation с аргументами целевой локации
    assertThat(command.getCommand()).isEqualTo("showLocation");
    assertThat(command.getArguments()).hasSize(1);
    assertThat(command.getArguments().getFirst()).isInstanceOf(ShowLocationCommandArguments.class);
    var arguments = (ShowLocationCommandArguments) command.getArguments().getFirst();
    assertThat(arguments.getUri()).isEqualTo(Absolute.uri(target.getUri()));
    assertThat(arguments.getRange()).isEqualTo(target.getRange());
  }

  @Test
  void gotoCommandForSingleTargetWithoutShowDocumentSupportUsesBuiltinCommand() {
    // given: не-VS-Code-клиент без поддержки window/showDocument
    connectClient("Neovim");
    lenient().when(showLocationCommandSupplier.isShowDocumentSupported()).thenReturn(false);
    var builder = newBuilder();
    var targets = List.of(location(10));

    // when
    var command = builder.gotoCommand("title", URI_VALUE, POSITION, targets);

    // then: остаётся прежний путь через встроенную команду редактора
    assertThat(command.getCommand()).isEqualTo(NavigationCommandBuilder.BUILTIN_GOTO_COMMAND);
    assertThat(command.getArguments()).containsExactly(URI_VALUE.toString(), POSITION, targets, "goto");
  }

  @Test
  void gotoCommandForVsCodeLikeSingleTargetIgnoresShowLocationEvenWithSupport() {
    // given: VS Code-клиент с поддержкой window/showDocument и одной целью
    connectClient("Visual Studio Code");
    lenient().when(showLocationCommandSupplier.isShowDocumentSupported()).thenReturn(true);
    var builder = newBuilder();
    var targets = List.of(location(10));

    // when
    var command = builder.gotoCommand("title", URI_VALUE, POSITION, targets);

    // then: для VS Code сохраняется команда-обёртка расширения
    assertThat(command.getCommand()).isEqualTo(NavigationCommandBuilder.VS_CODE_GOTO_COMMAND);
  }

  @Test
  void gotoCommandWithSeveralTargetsRequestsPeek() {
    // given
    connectClient("Visual Studio Code");
    var builder = newBuilder();
    var targets = List.of(location(10), location(20));

    // when
    var command = builder.gotoCommand("title", URI_VALUE, POSITION, targets);

    // then
    assertThat(command.getArguments()).containsExactly(URI_VALUE.toString(), POSITION, targets, "peek");
  }

  @Test
  void referencesCommandForVsCodeLikeClientUsesWrapperCommand() {
    // given
    connectClient("Antigravity");
    var builder = newBuilder();
    var locations = List.of(location(10), location(20));

    // when
    var command = builder.referencesCommand("title", URI_VALUE, POSITION, locations);

    // then
    assertThat(command.getCommand()).isEqualTo(NavigationCommandBuilder.VS_CODE_REFERENCES_COMMAND);
    assertThat(command.getArguments()).containsExactly(URI_VALUE.toString(), POSITION, locations);
  }

  @Test
  void referencesCommandForOtherClientUsesBuiltinCommand() {
    // given
    connectClient("Neovim");
    var builder = newBuilder();
    var locations = List.of(location(10));

    // when
    var command = builder.referencesCommand("title", URI_VALUE, POSITION, locations);

    // then
    assertThat(command.getCommand()).isEqualTo(NavigationCommandBuilder.BUILTIN_REFERENCES_COMMAND);
  }

  private static Location location(int line) {
    var position = new Position(line, 0);
    return new Location(URI_VALUE.toString(), new Range(position, position));
  }
}
