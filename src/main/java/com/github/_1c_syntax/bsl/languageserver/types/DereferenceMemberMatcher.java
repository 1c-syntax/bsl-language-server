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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionAtPosition;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.SkippedCallArgumentNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TerminalSymbolNode;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Резолв члена через dereference ({@code ресивер.член}): локализация AST-узла
 * dereference'а в выражении под курсором, инференс типов ресивера и подбор
 * членов по union-кандидатам владельца. Выделено из {@link TypeService},
 * чтобы фасад типов оставался в рамках одного класса.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class DereferenceMemberMatcher {

  private final TypeRegistry typeRegistry;
  private final ExpressionTypeInferencer inferencer;

  /**
   * Подобрать члены, к которым обращается выражение {@code ресивер.член} в
   * позиции курсора. Возвращает по одному {@link TypedMember} на каждый
   * тип-владелец из union'а ресивера; пустой список, если выражение/тип
   * ресивера не резолвятся.
   */
  public List<TypedMember> matchAt(TerminalNode terminal, DocumentContext documentContext, Position position) {
    var dereference = findDereferenceTree(documentContext, position, terminal);
    if (dereference == null) {
      return List.of();
    }
    var leftTypes = inferencer.infer(dereference.getLeft(), documentContext);
    if (leftTypes.isEmpty()) {
      return List.of();
    }
    return matchMembers(terminal, documentContext, dereference.getRight(), leftTypes);
  }

  /**
   * Типы ресивера в позиции для выражения {@code ресивер.член}: инферит
   * {@code left} и возвращает {@code TypeSet}. Empty, если AST или dereference
   * не локализуются.
   */
  public Optional<TypeSet> receiverTypesAt(DocumentContext documentContext, Position position,
                                           TerminalNode terminal) {
    var dereference = findDereferenceTree(documentContext, position, terminal);
    if (dereference == null) {
      return Optional.empty();
    }
    return Optional.of(inferencer.infer(dereference.getLeft(), documentContext));
  }

  private static @Nullable BinaryOperationNode findDereferenceTree(DocumentContext documentContext, Position position,
                                                         TerminalNode terminal) {
    var expression = ExpressionAtPosition.findExpressionTree(documentContext, position).orElse(null);
    if (expression == null) {
      return null;
    }
    return findDereferenceForTerminal(expression, terminal);
  }

  /**
   * Собирает члены с именем {@code terminal} по всем кандидатам-владельцам из
   * {@code leftTypes} (union), сопоставляя по виду (метод/свойство).
   */
  private List<TypedMember> matchMembers(TerminalNode terminal, DocumentContext documentContext,
                                         BslExpression right, TypeSet leftTypes) {
    var ctx = new MatchContext(
      (right instanceof MethodCallNode) ? MemberKind.METHOD : MemberKind.PROPERTY,
      terminal.getText(),
      Ranges.create(terminal),
      (right instanceof MethodCallNode call) ? countMeaningfulArgs(call) : -1,
      (right instanceof MethodCallNode call) ? inferArgTypes(call, documentContext) : List.of(),
      documentContext.getFileType()
    );
    var result = new ArrayList<TypedMember>();
    for (var owner : leftTypes.refs()) {
      collectCanonicalMembers(owner, ctx, result);
      if (ctx.expectedKind() == MemberKind.PROPERTY) {
        collectLocalFieldMembers(owner, leftTypes, ctx, result);
      }
    }
    return result;
  }

  /** Контекст матчинга члена в позиции: общие параметры для per-owner проходов. */
  private record MatchContext(MemberKind expectedKind, String memberName, Range range,
                              int argCount, List<TypeSet> argTypes, FileType fileType) {
  }

  /** Канонические члены типа из {@link TypeRegistry}. */
  private void collectCanonicalMembers(TypeRef owner, MatchContext ctx, List<TypedMember> sink) {
    for (var member : typeRegistry.getMembers(owner, ctx.fileType())) {
      if (member.kind() == ctx.expectedKind() && member.matches(ctx.memberName())) {
        sink.add(new TypedMember(owner, member, ctx.range(), ctx.argCount(), ctx.argTypes()));
      }
    }
  }

  /**
   * Динамические поля, прикреплённые инференсом к ресиверу: ключи литеральной
   * {@code Новый Структура("К1,К2")}, колонки ТЗ из JsDoc и т.п. Тот же источник,
   * что у dot-completion ({@code CompletionProvider#dotCompletion}).
   */
  private static void collectLocalFieldMembers(TypeRef owner, TypeSet leftTypes,
                                               MatchContext ctx, List<TypedMember> sink) {
    for (var entry : leftTypes.getLocalFields(owner).entrySet()) {
      if (!entry.getKey().equalsIgnoreCase(ctx.memberName())) {
        continue;
      }
      var field = entry.getValue();
      // Полный тип поля (с вложенными полями/элементами структуры), а не только
      // головной ref — иначе при чейнинге `a.b.` ресивер теряет содержимое b
      // (поля структуры, типизированной через см.-ссылку).
      var fieldTypes = !field.types().isEmpty() ? field.types() : TypeSet.of(TypeRef.UNKNOWN);
      sink.add(new TypedMember(owner,
        MemberDescriptor.property(entry.getKey(), fieldTypes, field.description()),
        ctx.range(), ctx.argCount(), ctx.argTypes()));
    }
  }

  private static int countMeaningfulArgs(MethodCallNode call) {
    var args = call.arguments();
    int n = args.size();
    // Trim trailing skipped argument (e.g. `Foo(a, )` — 2 ноды, но «значимый» только 1).
    while (n > 0 && args.get(n - 1) instanceof SkippedCallArgumentNode) {
      n--;
    }
    return n;
  }

  /**
   * Извлекает типы фактических аргументов вызова через
   * {@link ExpressionTypeInferencer#infer}. Используется для type-aware
   * подбора перегруженной сигнатуры. Trailing skipped argument'ы
   * пропускаются (как и в {@link #countMeaningfulArgs}), чтобы число
   * типов соответствовало callArgCount.
   */
  private List<TypeSet> inferArgTypes(MethodCallNode call, DocumentContext documentContext) {
    var args = call.arguments();
    int n = args.size();
    while (n > 0 && args.get(n - 1) instanceof SkippedCallArgumentNode) {
      n--;
    }
    if (n == 0) {
      return List.of();
    }
    var result = new ArrayList<TypeSet>(n);
    for (var i = 0; i < n; i++) {
      var arg = args.get(i);
      if (arg instanceof SkippedCallArgumentNode) {
        result.add(TypeSet.EMPTY);
        continue;
      }
      result.add(inferencer.infer(arg, documentContext));
    }
    return result;
  }

  private static @Nullable BinaryOperationNode findDereferenceForTerminal(BslExpression root, TerminalNode terminal) {
    if (root instanceof BinaryOperationNode binary
      && binary.getOperator() == BslOperator.DEREFERENCE
      && rightMatchesTerminal(binary.getRight(), terminal)) {
      return binary;
    }
    if (root instanceof BinaryOperationNode binary) {
      var leftHit = findDereferenceForTerminal(binary.getLeft(), terminal);
      if (leftHit != null) {
        return leftHit;
      }
      return findDereferenceForTerminal(binary.getRight(), terminal);
    }
    if (root instanceof MethodCallNode call) {
      for (var arg : call.arguments()) {
        var hit = findDereferenceForTerminal(arg, terminal);
        if (hit != null) {
          return hit;
        }
      }
    }
    return null;
  }

  private static boolean rightMatchesTerminal(BslExpression right, TerminalNode terminal) {
    if (right instanceof TerminalSymbolNode terminalNode
      && terminalNode.getNodeType() == ExpressionNodeType.IDENTIFIER) {
      var ast = terminalNode.getRepresentingAst();
      return ast == terminal;
    }
    if (right instanceof MethodCallNode call) {
      return call.getName() == terminal;
    }
    return false;
  }
}
