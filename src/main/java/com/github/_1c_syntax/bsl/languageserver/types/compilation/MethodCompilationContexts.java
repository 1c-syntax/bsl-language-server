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
package com.github._1c_syntax.bsl.languageserver.types.compilation;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.types.ModuleType;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Ограничения директив методов управляемых форм, команд и общих модулей. */
final class MethodCompilationContexts {
  private static final Set<CompilerDirectiveKind> FORM_DIRECTIVES = EnumSet.of(
    CompilerDirectiveKind.AT_CLIENT, CompilerDirectiveKind.AT_SERVER,
    CompilerDirectiveKind.AT_SERVER_NO_CONTEXT, CompilerDirectiveKind.AT_CLIENT_AT_SERVER_NO_CONTEXT);
  private static final Set<CompilerDirectiveKind> COMMAND_DIRECTIVES = EnumSet.of(
    CompilerDirectiveKind.AT_CLIENT, CompilerDirectiveKind.AT_SERVER, CompilerDirectiveKind.AT_CLIENT_AT_SERVER);


  private MethodCompilationContexts() {
  }

  static Optional<MethodContext> formMethodContext(DocumentContext document, CompilerDirectiveKind kind,
                                                          Set<ExecutionContext> contexts) {
    var allowed = document.getModuleType() == ModuleType.CommandModule ? COMMAND_DIRECTIVES : FORM_DIRECTIVES;
    if (!allowed.contains(kind)) {
      return Optional.empty();
    }
    restrictToDirective(contexts, kind);
    return Optional.of(new MethodContext(CompilerDirectiveKind.of(kind.getTokenType()), Set.copyOf(contexts)));
  }

  static Optional<MethodContext> commonMethodContext(DocumentContext document, CompilerDirectiveKind kind,
                                                            Set<ExecutionContext> contexts) {
    if (document.getMdObject().filter(CommonModule.class::isInstance).isEmpty()) {
      return Optional.empty();
    }
    var directive = CompilerDirectiveKind.of(kind.getTokenType());
    if (kind != CompilerDirectiveKind.AT_CLIENT && kind != CompilerDirectiveKind.AT_SERVER) {
      return Optional.of(new MethodContext(directive, Set.of()));
    }
    restrictToDirective(contexts, kind);
    return Optional.of(new MethodContext(directive, Set.copyOf(contexts)));
  }

  private static void restrictToDirective(Set<ExecutionContext> contexts, CompilerDirectiveKind directive) {
    switch (directive) {
      case AT_CLIENT -> contexts.retainAll(ExecutionContext.CLIENTS);
      case AT_SERVER, AT_SERVER_NO_CONTEXT -> contexts.retainAll(ExecutionContext.SERVER_CONTEXTS);
      default -> { /* директива разрешает обе стороны */ }
    }
  }
}
