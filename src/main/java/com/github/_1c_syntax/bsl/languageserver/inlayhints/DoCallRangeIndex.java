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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Range;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Индекс {@code doCall}-узлов документа по диапазону имени вызываемого метода.
 * <p>
 * Строится одним обходом AST документа и позволяет резолвить вызов по ссылке
 * на метод за {@code O(1)} вместо повторного обхода AST на каждую ссылку.
 * Ключом служит {@link Reference#selectionRange()} ссылки, совпадающий с
 * диапазоном имени вызываемого метода (для конструктора — с диапазоном имени типа).
 */
final class DoCallRangeIndex {

  private final Map<String, BSLParser.DoCallContext> doCallsByMethodNameRange;

  private DoCallRangeIndex(Map<String, BSLParser.DoCallContext> doCallsByMethodNameRange) {
    this.doCallsByMethodNameRange = doCallsByMethodNameRange;
  }

  /**
   * Строит индекс по AST документа.
   * <p>
   * Все {@code doCall}-узлы документа собираются в карту по диапазону имени
   * вызываемого метода (для конструктора — по диапазону имени типа). Этот диапазон
   * совпадает с {@link Reference#selectionRange()} соответствующей ссылки.
   *
   * @param documentContext контекст документа, AST которого обходится
   * @return индекс вызовов документа; пустой, если вызовов нет
   */
  static DoCallRangeIndex of(DocumentContext documentContext) {
    var ast = documentContext.getAst();
    var doCalls = Trees.findAllRuleNodes(ast, BSLParser.RULE_doCall);
    Map<String, BSLParser.DoCallContext> result = HashMap.newHashMap(doCalls.size());
    for (var node : doCalls) {
      var doCall = (BSLParser.DoCallContext) node;
      var doCallParent = doCall.getParent();
      if (doCallParent == null) {
        continue;
      }
      methodNameRange(doCallParent)
        .ifPresent(methodNameRange -> result.putIfAbsent(rangeKey(methodNameRange), doCall));
    }
    return new DoCallRangeIndex(result);
  }

  /**
   * Возвращает {@code doCall}-узел, соответствующий ссылке на метод.
   *
   * @param reference ссылка на вызываемый метод; используется её {@link Reference#selectionRange()}
   * @return узел вызова либо {@link Optional#empty()}, если в документе нет вызова с таким диапазоном
   */
  Optional<BSLParser.DoCallContext> doCallFor(Reference reference) {
    return Optional.ofNullable(doCallsByMethodNameRange.get(rangeKey(reference.selectionRange())));
  }

  /**
   * Строковый ключ карты вызовов по диапазону имени метода.
   * <p>
   * Используется вместо {@link Range} из lsp4j, который не реализует
   * {@link Comparable}: {@link String} реализует {@link Comparable} и не зависит
   * от деталей {@link Range#hashCode()}, что устраняет риск деградации хэш-карты
   * при коллизиях ключей.
   *
   * @param range диапазон имени метода/типа
   * @return ключ вида {@code "startLine:startChar:endLine:endChar"}
   */
  private static String rangeKey(Range range) {
    var start = range.getStart();
    var end = range.getEnd();
    return start.getLine() + ":" + start.getCharacter() + ":" + end.getLine() + ":" + end.getCharacter();
  }

  /**
   * Диапазон имени вызываемого метода для родителя {@code doCall}-узла —
   * именно его {@link com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex}
   * хранит в {@link Reference#selectionRange()}.
   *
   * @param doCallParent родительский узел вызова (methodCall, globalMethodCall или newExpression)
   * @return диапазон имени метода/типа либо {@link Optional#empty()},
   *   если узел не является вызовом метода
   */
  private static Optional<Range> methodNameRange(ParserRuleContext doCallParent) {
    if (doCallParent instanceof BSLParser.MethodCallContext methodCallContext) {
      var methodName = methodCallContext.methodName();
      return methodName == null ? Optional.empty() : Optional.of(Ranges.create(methodName));
    } else if (doCallParent instanceof BSLParser.GlobalMethodCallContext globalMethodCallContext) {
      var methodName = globalMethodCallContext.methodName();
      return methodName == null ? Optional.empty() : Optional.of(Ranges.create(methodName));
    } else if (doCallParent instanceof BSLParser.NewExpressionContext newExpressionContext) {
      var typeName = newExpressionContext.typeName();
      if (typeName != null && typeName.IDENTIFIER() != null) {
        return Optional.of(Ranges.create(typeName.IDENTIFIER()));
      }
      return Optional.empty();
    } else {
      return Optional.empty();
    }
  }
}
