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

import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Полные объявления и структура выражений одного пакета диагностик.
 * Значения потока и частичные объявления сюда не попадают.
 */
public final class DiagnosticDeclarationSession {
  private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
  private final Map<Key, TypeSet> completed = new ConcurrentHashMap<>();
  private final Map<MethodKey, Boolean> moduleReturnProofs = new ConcurrentHashMap<>();
  private final Map<BSLParser.ExpressionContext, Optional<BslExpression>> expressions = new ConcurrentHashMap<>();

  /** Синтаксис не зависит от режима или приближений потока; готовое дерево используется только для чтения. */
  static @Nullable BslExpression expressionTree(BSLParser.@Nullable ExpressionContext expression) {
    var session = current();
    if (session == null || expression == null) {
      return ExpressionTreeBuildingVisitor.buildExpressionTree(expression);
    }
    return session.expressions.computeIfAbsent(expression,
      key -> Optional.ofNullable(ExpressionTreeBuildingVisitor.buildExpressionTree(key))).orElse(null);
  }

  private record Key(VariableSymbol variable, long revision) {
    @Override
    public boolean equals(Object other) {
      return other instanceof Key(var otherVariable, var otherRevision)
        && variable == otherVariable && revision == otherRevision;
    }

    @Override
    public int hashCode() {
      return 31 * System.identityHashCode(variable) + Long.hashCode(revision);
    }
  }

  private record MethodKey(MethodSymbol method, long revision) {
    @Override
    public boolean equals(Object other) {
      return other instanceof MethodKey(var otherMethod, var otherRevision)
        && method == otherMethod && revision == otherRevision;
    }

    @Override
    public int hashCode() {
      return 31 * System.identityHashCode(method) + Long.hashCode(revision);
    }
  }

  /** Структурное доказательство не содержит типов потока и ограничено версией входов. */
  boolean moduleReturnsOnly(MethodSymbol method, long revision, BooleanSupplier proof) {
    var key = new MethodKey(method, revision);
    var known = moduleReturnProofs.get(key);
    if (known != null) {
      return known;
    }
    var computed = proof.getAsBoolean();
    var previous = moduleReturnProofs.putIfAbsent(key, computed);
    return previous == null ? computed : previous;
  }

  public Scope open() {
    var scope = new Scope(this, CURRENT.get());
    CURRENT.set(scope);
    return scope;
  }

  static @Nullable DiagnosticDeclarationSession current() {
    var scope = CURRENT.get();
    return scope == null ? null : scope.session;
  }

  /** Только метаданные компиляции: отдельный ленивый кеш задачи, сбрасываемый вместе с входами. */
  static CompilationContextMemo compilationMemo(@Nullable URI workspace, long revision) {
    var scope = CURRENT.get();
    if (scope == null) {
      return new CompilationContextMemo();
    }
    if (scope.compilation == null || scope.compilationRevision != revision
      || !Objects.equals(scope.workspace, workspace)) {
      scope.compilation = new CompilationContextMemo();
      scope.compilationRevision = revision;
      scope.workspace = workspace;
    }
    return scope.compilation;
  }

  @Nullable TypeSet get(VariableSymbol variable, long revision) {
    return completed.get(new Key(variable, revision));
  }

  void remember(VariableSymbol variable, long revision, TypeSet types) {
    completed.putIfAbsent(new Key(variable, revision), types);
  }

  /** Восстанавливает предыдущий пакет при вложенном запуске и очищает состояние потока после задачи. */
  public static final class Scope implements AutoCloseable {
    private final DiagnosticDeclarationSession session;
    private final @Nullable Scope previous;
    private @Nullable CompilationContextMemo compilation;
    private @Nullable URI workspace;
    private long compilationRevision;

    private Scope(DiagnosticDeclarationSession session, @Nullable Scope previous) {
      this.session = session;
      this.previous = previous;
    }

    @Override
    public void close() {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }
}
