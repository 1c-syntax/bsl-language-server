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
import java.util.regex.Pattern;

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
 * не задан, считается «самая свежая платформа», поэтому любое устаревание
 * срабатывает, а недоступность — никогда.
 */
public final class PlatformMemberCalls {

  /**
   * Строка версии вида {@code 8.3.10} — минимум три числовых компонента через
   * {@code .} или {@code _} (этого достаточно для {@link CompatibilityMode}).
   * Двухкомпонентные ({@code 8.3}) и прочие — невалидны.
   */
  private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+[._]\\d+[._]\\d+");

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

  /** Члены типов (метод/свойство) — с pre-filter'ом по версионному имени. */
  private static void collectVersionedMembers(BSLParser.FileContext ast, DocumentContext documentContext,
                                              TypeService typeService, TypeRegistry typeRegistry,
                                              List<TypedMember> sink) {
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_methodCall)) {
      var methodName = ((BSLParser.MethodCallContext) node).methodName();
      if (methodName != null) {
        resolveVersioned(methodName.getStart(), documentContext, typeService, typeRegistry, sink);
      }
    }
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_accessProperty)) {
      var identifier = ((BSLParser.AccessPropertyContext) node).IDENTIFIER();
      if (identifier != null) {
        resolveVersioned(identifier.getSymbol(), documentContext, typeService, typeRegistry, sink);
      }
    }
  }

  private static void resolveVersioned(@Nullable Token token, DocumentContext documentContext,
                                       TypeService typeService, TypeRegistry typeRegistry,
                                       List<TypedMember> sink) {
    if (token != null && typeRegistry.isVersionedMemberName(token.getText())) {
      resolveInto(sink, documentContext, typeService, token);
    }
  }

  private static void resolveInto(List<TypedMember> sink, DocumentContext documentContext,
                                  TypeService typeService, @Nullable Token token) {
    if (token == null) {
      return;
    }
    // Позиция начала идентификатора входит в его токен (start-inclusive),
    // этого достаточно для findMembersAt.
    var position = new Position(token.getLine() - 1, token.getCharPositionInLine());
    sink.addAll(typeService.findMembersAt(documentContext, position));
  }

  /**
   * Целевая версия платформы для сравнения. Приоритет: явная настройка
   * {@code v8platform.targetVersion} в конфиге LS → режим совместимости
   * конфигурации. Если режим совместимости не задан ({@code DontUse}),
   * {@link CompatibilityMode} трактует его как самую свежую платформу
   * (доминирует в {@code compareTo}), поэтому отдельной обработки не требуется.
   */
  public static CompatibilityMode targetCompatibilityMode(DocumentContext documentContext,
                                                          LanguageServerConfiguration configuration) {
    var explicit = parse(configuration.getV8PlatformOptions().getTargetVersion());
    if (explicit != null) {
      return explicit;
    }
    return documentContext.getServerContext().getConfiguration().getCompatibilityMode();
  }

  /**
   * Член устарел для целевой платформы: {@code target >= deprecatedSinceVersion}.
   * Пустая или неразборчивая строка версии → {@code false}.
   */
  public static boolean firesDeprecated(String deprecatedSinceVersion, CompatibilityMode target) {
    var version = parse(deprecatedSinceVersion);
    return version != null && CompatibilityMode.compareTo(version, target) >= 0;
  }

  /**
   * Член недоступен в целевой платформе: {@code target < sinceVersion}.
   * Пустая или неразборчивая строка версии → {@code false}.
   */
  public static boolean firesUnavailable(String sinceVersion, CompatibilityMode target) {
    var version = parse(sinceVersion);
    return version != null && CompatibilityMode.compareTo(version, target) < 0;
  }

  /**
   * Парсит строку версии вида {@code 8.3.10} в {@link CompatibilityMode}.
   * Пустые, двухкомпонентные ({@code 8.3}) и иные неразборчивые строки →
   * {@code null} (проверку версии для такого члена пропускаем).
   */
  @Nullable
  private static CompatibilityMode parse(@Nullable String version) {
    if (version == null || !VERSION_PATTERN.matcher(version).find()) {
      return null;
    }
    return new CompatibilityMode(version);
  }
}
