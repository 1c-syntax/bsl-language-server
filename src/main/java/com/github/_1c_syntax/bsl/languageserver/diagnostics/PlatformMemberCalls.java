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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.support.CompatibilityMode;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Резолв платформенных членов в сайтах вызовов и сравнение версий — общая
 * база для диагностик устаревания ({@code DeprecatedMethodCall}) и
 * недоступности-по-версии ({@code UnavailableMemberCall}).
 * <p>
 * Глобальные функции резолвятся напрямую (без инференса), поэтому собираются
 * без pre-filter'а. Члены типов (метод/свойство) предварительно отсеиваются по
 * имени через {@link TypeRegistry#isVersionedMemberName(String)} — это лишь
 * дешёвый фильтр, после которого {@link TypeService#findMembersAt} выполняет
 * точный резолв члена на конкретном типе-владельце (иначе сработал бы
 * однофамилец с другого типа).
 * <p>
 * Сравнение версий ведётся относительно режима совместимости проекта. Если он
 * не задан, {@link CompatibilityMode} = «самая свежая платформа», поэтому любое
 * устаревание срабатывает, а недоступность — никогда.
 */
final class PlatformMemberCalls {

  private PlatformMemberCalls() {
  }

  /**
   * Собирает резолвленные платформенные члены во всех сайтах вызовов/обращений
   * модуля. Для union-типа ресивера возвращаются все кандидаты-владельцы (по
   * одному {@link TypedMember} на тип) с одинаковым диапазоном.
   */
  static List<TypedMember> collect(DocumentContext documentContext,
                                   TypeService typeService,
                                   TypeRegistry typeRegistry) {
    var ast = documentContext.getAst();
    if (ast == null) {
      return List.of();
    }
    var result = new ArrayList<TypedMember>();

    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_globalMethodCall)) {
      var ctx = (BSLParser.GlobalMethodCallContext) node;
      if (ctx.methodName() != null) {
        resolveInto(result, documentContext, typeService, ctx.methodName().getStart());
      }
    }

    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_methodCall)) {
      var ctx = (BSLParser.MethodCallContext) node;
      if (ctx.methodName() == null) {
        continue;
      }
      var token = ctx.methodName().getStart();
      if (token != null && typeRegistry.isVersionedMemberName(token.getText())) {
        resolveInto(result, documentContext, typeService, token);
      }
    }

    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_accessProperty)) {
      var ctx = (BSLParser.AccessPropertyContext) node;
      if (ctx.IDENTIFIER() == null) {
        continue;
      }
      var token = ctx.IDENTIFIER().getSymbol();
      if (typeRegistry.isVersionedMemberName(token.getText())) {
        resolveInto(result, documentContext, typeService, token);
      }
    }

    return result;
  }

  private static void resolveInto(List<TypedMember> sink, DocumentContext documentContext,
                                  TypeService typeService, @Nullable Token token) {
    if (token == null) {
      return;
    }
    var position = new Position(
      token.getLine() - 1,
      token.getCharPositionInLine() + Math.max(0, token.getText().length() / 2));
    sink.addAll(typeService.findMembersAt(documentContext, position));
  }

  /**
   * Режим совместимости конфигурации «не задан» ({@code DontUse}) —
   * {@link CompatibilityMode} по умолчанию = {@code (3, 99)}.
   */
  private static final CompatibilityMode UNSET = new CompatibilityMode();

  /**
   * «Самая свежая платформа» — сентинел, доминирующий над любым реальным
   * режимом совместимости. Берём заведомо больший {@code minor}, чтобы
   * перекрывать и семейство 8.5+, а не только 8.3.x (значение по умолчанию
   * {@code DontUse} = {@code (3, 99)} семейство 8.5 НЕ перекрывает).
   */
  private static final CompatibilityMode NEWEST = new CompatibilityMode(99, 99);

  /**
   * Целевая версия платформы для сравнения. Приоритет: явная настройка
   * {@code platform.targetVersion} в конфиге LS → режим совместимости
   * конфигурации → «самая свежая платформа» (если режим совместимости не задан
   * / {@code DontUse}).
   */
  static CompatibilityMode targetCompatibilityMode(DocumentContext documentContext,
                                                   LanguageServerConfiguration configuration) {
    var explicit = parse(configuration.getPlatformOptions().getTargetVersion());
    if (explicit != null) {
      return explicit;
    }
    var configMode = documentContext.getServerContext().getConfiguration().getCompatibilityMode();
    if (configMode == null || configMode.equals(UNSET)) {
      return NEWEST;
    }
    return configMode;
  }

  /**
   * Член устарел для целевой платформы: {@code target >= deprecatedSinceVersion}.
   * Пустая или неразборчивая строка версии → {@code false}.
   */
  static boolean firesDeprecated(String deprecatedSinceVersion, CompatibilityMode target) {
    var version = parse(deprecatedSinceVersion);
    return version != null && CompatibilityMode.compareTo(version, target) >= 0;
  }

  /**
   * Член недоступен в целевой платформе: {@code target < sinceVersion}.
   * Пустая или неразборчивая строка версии → {@code false}.
   */
  static boolean firesUnavailable(String sinceVersion, CompatibilityMode target) {
    var version = parse(sinceVersion);
    return version != null && CompatibilityMode.compareTo(version, target) < 0;
  }

  /**
   * Парсит строку версии вида {@code 8.3.10} в {@link CompatibilityMode}.
   * Двухкомпонентные ({@code 8.3}) и иные неразборчивые строки → {@code null}
   * (проверку версии для такого члена пропускаем).
   */
  @Nullable
  private static CompatibilityMode parse(String version) {
    if (version == null || version.isBlank()) {
      return null;
    }
    try {
      return new CompatibilityMode(version);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
