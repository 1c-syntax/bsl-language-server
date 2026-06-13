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
package com.github._1c_syntax.bsl.languageserver.diagnostics.platform;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Резолв платформенных членов в сайтах вызовов модуля — общая база для
 * диагностик устаревания ({@code DeprecatedMethodCall}) и
 * недоступности-по-версии ({@code UnavailableMemberCall}). Версионная
 * применимость члена (устаревание/недоступность) вынесена в
 * {@link com.github._1c_syntax.bsl.languageserver.types.PlatformMemberVersions}.
 * <p>
 * Глобальные функции резолвятся напрямую (без инференса), поэтому собираются
 * без pre-filter'а. Члены типов (метод/свойство) предварительно отсеиваются по
 * имени через {@link TypeRegistry#isVersionedMemberName(String)} — это лишь
 * дешёвый фильтр, после которого {@link TypeService#membersAt} выполняет
 * точный резолв члена на конкретном типе-владельце (иначе сработал бы
 * однофамилец с другого типа).
 */
public final class PlatformMemberCalls {

  /**
   * 1С-конвенция пометки устаревших реквизитов / значений перечислений /
   * объектов конфигурации — префиксы {@code "Удалить"} (ru-mode) и
   * {@code "Delete"} (en-mode).
   */
  private static final String[] DELETED_PREFIXES = {"Удалить", "Delete"};

  private PlatformMemberCalls() {
  }

  /**
   * Собирает резолвленные платформенные члены во всех сайтах вызовов/обращений
   * модуля. Для union-типа ресивера возвращаются все кандидаты-владельцы (по
   * одному {@link TypedMember} на тип) с одинаковым диапазоном.
   */
  public static List<TypedMember> collect(DocumentContext documentContext,
                                          TypeService typeService,
                                          TypeRegistry typeRegistry) {
    var ast = documentContext.getAst();
    var result = new ArrayList<TypedMember>();
    collectGlobalCalls(ast, documentContext, typeService, result);
    collectVersionedMembers(ast, documentContext, typeService, typeRegistry, result);
    return result;
  }

  /** Глобальные вызовы — резолв дёшев (без инференса), без pre-filter'а по имени. */
  private static void collectGlobalCalls(BSLParser.FileContext ast, DocumentContext documentContext,
                                         TypeService typeService, List<TypedMember> sink) {
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_globalMethodCall)) {
      var methodName = ((BSLParser.GlobalMethodCallContext) node).methodName();
      if (methodName != null) {
        resolveInto(sink, documentContext, typeService, methodName.getStart());
      }
    }
  }

  /**
   * Имя следует 1С-конвенции «устарело» — начинается с одного из префиксов
   * {@link #DELETED_PREFIXES} (без учёта регистра, с хотя бы одним символом
   * после префикса). Используется в паре с {@code MemberKind.PROPERTY},
   * чтобы не захватывать одноимённые action-методы вроде {@code УдалитьФайл}.
   */
  public static boolean hasDeletedPrefix(@Nullable String name) {
    if (name == null) {
      return false;
    }
    for (var prefix : DELETED_PREFIXES) {
      if (name.length() > prefix.length()
        && name.regionMatches(true, 0, prefix, 0, prefix.length())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Члены типов (метод/свойство) — с pre-filter'ом по имени.
   * Включает версионные ({@link TypeRegistry#isVersionedMemberName}) и
   * следующие 1С-конвенции «устарело» (префикс «Удалить»). Остальные
   * имена не резолвятся, чтобы не тратить инференс на каждый узел.
   */
  private static void collectVersionedMembers(BSLParser.FileContext ast, DocumentContext documentContext,
                                              TypeService typeService, TypeRegistry typeRegistry,
                                              List<TypedMember> sink) {
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_methodCall)) {
      var methodName = ((BSLParser.MethodCallContext) node).methodName();
      if (methodName != null) {
        resolveCandidate(methodName.getStart(), documentContext, typeService, typeRegistry, sink);
      }
    }
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_accessProperty)) {
      var identifier = ((BSLParser.AccessPropertyContext) node).IDENTIFIER();
      if (identifier != null) {
        resolveCandidate(identifier.getSymbol(), documentContext, typeService, typeRegistry, sink);
      }
    }
  }

  private static void resolveCandidate(@Nullable Token token, DocumentContext documentContext,
                                       TypeService typeService, TypeRegistry typeRegistry,
                                       List<TypedMember> sink) {
    if (token == null) {
      return;
    }
    var text = token.getText();
    if (typeRegistry.isVersionedMemberName(text) || hasDeletedPrefix(text)) {
      resolveInto(sink, documentContext, typeService, token);
    }
  }

  private static void resolveInto(List<TypedMember> sink, DocumentContext documentContext,
                                  TypeService typeService, @Nullable Token token) {
    if (token == null) {
      return;
    }
    // Позиция начала идентификатора входит в его токен (start-inclusive),
    // этого достаточно для membersAt.
    var position = new Position(token.getLine() - 1, token.getCharPositionInLine());
    sink.addAll(typeService.membersAt(documentContext, position));
  }
}
