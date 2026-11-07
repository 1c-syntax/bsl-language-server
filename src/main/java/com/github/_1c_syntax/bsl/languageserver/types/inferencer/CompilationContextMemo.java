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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.eclipse.lsp4j.Position;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import com.github._1c_syntax.bsl.parser.BSLParser;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Метаданные компиляции одного запроса или диагностики при неизменной версии входов. */
final class CompilationContextMemo {
  private final Map<DocumentContext, Map<Optional<CompilerDirectiveKind>, Optional<MethodContext>>> profiles
    = new IdentityHashMap<>();
  private final Map<BSLParser.CodeBlockContext, Optional<Set<ExecutionContext>>> bodies = new IdentityHashMap<>();

  Optional<MethodContext> contextAt(DocumentContext document, Position position,
                                     CompilationContextResolver resolver) {
    if (document.getFileType() != FileType.BSL) {
      return Optional.empty();
    }
    var symbol = document.getSymbolTree().getSymbolAtPosition(position);
    var method = symbol instanceof MethodSymbol enclosing ? Optional.of(enclosing)
      : symbol.getRootParent(MethodSymbol.class).map(MethodSymbol.class::cast);
    var profile = methodContext(document, method.flatMap(MethodSymbol::getCompilerDirectiveKind), resolver);
    return resolver.contextAt(document, position, profile);
  }

  Optional<MethodContext> methodContext(DocumentContext document, Optional<CompilerDirectiveKind> directive,
                                         CompilationContextResolver resolver) {
    synchronized (this) {
      var known = profiles.get(document);
      if (known != null && known.containsKey(directive)) return known.get(directive);
    }
    var computed = resolver.methodContext(document, directive);
    synchronized (this) {
      var known = profiles.computeIfAbsent(document, ignored -> new HashMap<>());
      var previous = known.putIfAbsent(directive, computed);
      return previous == null ? computed : previous;
    }
  }

  Optional<Set<ExecutionContext>> possibleContexts(DocumentContext document, BSLParser.CodeBlockContext body,
                                                   CompilationContextResolver resolver) {
    synchronized (this) {
      var known = bodies.get(body);
      if (known != null) return known;
    }
    var computed = resolver.possibleContexts(document, body);
    synchronized (this) {
      var previous = bodies.putIfAbsent(body, computed);
      return previous == null ? computed : previous;
    }
  }
}
