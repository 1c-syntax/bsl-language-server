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
package com.github._1c_syntax.bsl.languageserver.lsp;

import com.github._1c_syntax.bsl.languageserver.jsonrpc.DiagnosticParams;
import com.github._1c_syntax.bsl.languageserver.util.AotHints;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON-RPC слой LSP4J находит и вызывает методы сервисов рефлексией, а сообщения протокола
 * сериализует Gson'ом по полям.
 */
class Lsp4jRuntimeHintsTest {

  static Stream<Arguments> serviceMethods() {
    return Stream.of(
      Arguments.of(LanguageServer.class, "getNotebookDocumentService"),
      Arguments.of(BSLLanguageServer.class, "initialize"),
      Arguments.of(BSLTextDocumentService.class, "didOpen"),
      Arguments.of(BSLWorkspaceService.class, "executeCommand"),
      Arguments.of(BSLLanguageServer.class, "diagnostics")
    );
  }

  @ParameterizedTest
  @MethodSource("serviceMethods")
  void testServiceMethodsAreRegisteredForInvocation(Class<?> service, String method) {
    var hints = AotHints.hintsOf(BSLLanguageServer.class);

    assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(service, method)).accepts(hints);
  }

  @ParameterizedTest
  @MethodSource("protocolMessages")
  void testProtocolMessagesAreRegisteredForSerialization(Class<?> message) {
    var hints = AotHints.hintsOf(BSLLanguageServer.class);

    assertThat(RuntimeHintsPredicates.reflection().onType(message)
      .withMemberCategories(MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
      .accepts(hints);
  }

  static Stream<Class<?>> protocolMessages() {
    return Stream.of(Diagnostic.class, RequestMessage.class, DiagnosticParams.class);
  }

  @Test
  void testRegistrarIsImported() {
    assertThat(AotHints.hintsOf(BSLLanguageServer.class).reflection().typeHints()).isNotEmpty();
  }
}
