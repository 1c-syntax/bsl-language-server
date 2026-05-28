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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Position;

import java.util.stream.Collectors;

/**
 * Подсвечивает обращение к методу или свойству, которое не является известным
 * членом своего типа (вероятная опечатка или несуществующий API):
 * <ul>
 *   <li>{@code Ресивер.Член} — тип ресивера выведен и конкретен, но члена с
 *       таким именем у него нет;</li>
 *   <li>голый вызов {@code Имя(...)} — имя не резолвится ни в глобальную
 *       функцию/свойство платформы или конфигурации, ни в метод/переменную
 *       текущего модуля.</li>
 * </ul>
 * <p>
 * Эвристика: при невыводимом или произвольном типе ресивера диагностика молчит,
 * чтобы не давать ложных срабатываний. Выключена по умолчанию — требует обкатки
 * на реальных конфигурациях.
 *
 * @see TypeService#unknownMemberReceiverAt
 * @see TypeService#isUnknownGlobalAt
 */
@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 2,
  scope = DiagnosticScope.ALL,
  activatedByDefault = false,
  tags = {
    DiagnosticTag.SUSPICIOUS
  }
)
@RequiredArgsConstructor
public class UnknownMemberDiagnostic extends AbstractVisitorDiagnostic {

  private final TypeService typeService;

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext ctx) {
    var methodName = ctx.methodName();
    if (methodName != null) {
      var token = methodName.getStart();
      if (typeService.isUnknownGlobalAt(documentContext, positionOf(token))) {
        diagnosticStorage.addDiagnostic(methodName, info.getMessage(token.getText()));
      }
    }
    return super.visitGlobalMethodCall(ctx);
  }

  @Override
  public ParseTree visitMethodCall(BSLParser.MethodCallContext ctx) {
    var methodName = ctx.methodName();
    if (methodName != null) {
      var token = methodName.getStart();
      typeService.unknownMemberReceiverAt(documentContext, positionOf(token))
        .ifPresent(types -> diagnosticStorage.addDiagnostic(methodName, memberMessage(token.getText(), types)));
    }
    return super.visitMethodCall(ctx);
  }

  @Override
  public ParseTree visitAccessProperty(BSLParser.AccessPropertyContext ctx) {
    var identifier = ctx.IDENTIFIER();
    if (identifier != null) {
      var token = identifier.getSymbol();
      typeService.unknownMemberReceiverAt(documentContext, positionOf(token))
        .ifPresent(types -> diagnosticStorage.addDiagnostic(identifier, memberMessage(token.getText(), types)));
    }
    return super.visitAccessProperty(ctx);
  }

  /**
   * Сообщение с именем(-ами) типа ресивера для подсказки, на каком типе члена
   * не нашлось. Несколько кандидатов union'а склеиваются через запятую.
   */
  private String memberMessage(String memberName, TypeSet ownerTypes) {
    var typeNames = ownerTypes.refs().stream()
      .map(TypeRef::qualifiedName)
      .distinct()
      .collect(Collectors.joining(", "));
    return info.getResourceString("memberMessage", memberName, typeNames);
  }

  private static Position positionOf(Token token) {
    return new Position(token.getLine() - 1, token.getCharPositionInLine());
  }
}
