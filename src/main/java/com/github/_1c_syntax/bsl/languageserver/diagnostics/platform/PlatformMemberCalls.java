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
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Резолв обращений к платформенному API в модуле — общая база для диагностик
 * устаревания ({@code DeprecatedMethodCall}) и недоступности-по-версии
 * ({@code UnavailableMemberCall}). Собирает два вида мест в коде: обращения к
 * членам (вызов метода, обращение к свойству, вызов глобальной функции) и
 * конструирование типов ({@code Новый Тип(…)}). Версионная применимость
 * (устаревание/недоступность) вынесена в
 * {@link com.github._1c_syntax.bsl.languageserver.types.PlatformMemberVersions}.
 * <p>
 * Глобальные функции резолвятся напрямую (без инференса), поэтому собираются
 * без pre-filter'а. Члены типов (метод/свойство) предварительно отсеиваются по
 * имени через {@link TypeService#isVersionedMemberName(String)} — это лишь
 * дешёвый фильтр, после которого {@link TypeService#membersAt} выполняет
 * точный резолв члена на конкретном типе-владельце (иначе сработал бы
 * однофамилец с другого типа).
 * <p>
 * Форма {@code Новый("ИмяТипа", …)} не разбирается: имя типа там — значение
 * выражения, вычисляемое в рантайме.
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
   * Места обращения к платформенному API в одном модуле.
   *
   * @param members          резолвленные члены (метод, свойство, глобальная функция).
   *                         Для union-типа ресивера возвращаются все кандидаты-владельцы
   *                         (по одному {@link TypedMember} на тип) с одинаковым диапазоном.
   * @param constructedTypes типы, конструируемые через {@code Новый Тип(…)}.
   */
  public record CallSites(List<TypedMember> members, List<ConstructedType> constructedTypes) {
  }

  /**
   * Конструируемый тип в одном выражении {@code Новый Тип(…)}.
   *
   * @param typeRef  резолвленный тип.
   * @param typeName имя типа, как написано в коде (для сообщения диагностики).
   * @param metadata «страничные» метаданные типа: версии появления/устаревания,
   *                 рекомендуемые замены и т.д.
   * @param node     узел имени типа — диапазон подсветки.
   */
  public record ConstructedType(
    TypeRef typeRef,
    String typeName,
    PlatformMetadata metadata,
    ParserRuleContext node
  ) {
  }

  /**
   * Собирает все места обращения к платформенному API модуля за один обход AST
   * (раньше на каждый вид обращения был свой {@code findAllRuleNodes} — то есть
   * отдельный полный обход дерева). Нерезолвленные имена пропускаются.
   */
  public static CallSites collect(DocumentContext documentContext, TypeService typeService) {
    var ast = documentContext.getAst();
    var members = new ArrayList<TypedMember>();
    var constructedTypes = new ArrayList<ConstructedType>();
    for (var node : Trees.findAllRuleNodes(ast,
      BSLParser.RULE_globalMethodCall, BSLParser.RULE_methodCall, BSLParser.RULE_accessProperty,
      BSLParser.RULE_newExpression)) {
      collectSite(node, documentContext, typeService, members, constructedTypes);
    }
    return new CallSites(members, constructedTypes);
  }

  /** Резолв одного места вызова/обращения в зависимости от вида продукции. */
  private static void collectSite(ParserRuleContext node, DocumentContext documentContext,
                                  TypeService typeService, List<TypedMember> memberSink,
                                  List<ConstructedType> constructedSink) {
    switch (node) {
      case BSLParser.GlobalMethodCallContext globalCall ->
        resolveGlobalCall(globalCall, documentContext, typeService, memberSink);
      case BSLParser.MethodCallContext methodCall ->
        resolveMethodCall(methodCall, documentContext, typeService, memberSink);
      case BSLParser.AccessPropertyContext accessProperty ->
        resolveCandidate(accessProperty.IDENTIFIER(), documentContext, typeService, memberSink);
      case BSLParser.NewExpressionContext newExpression ->
        resolveConstructedType(newExpression, documentContext, typeService, constructedSink);
      default -> {
        // другие продукции в обход не запрашивались
      }
    }
  }

  /** Глобальный вызов: резолв дёшев (без инференса), поэтому без pre-filter'а по имени. */
  private static void resolveGlobalCall(BSLParser.GlobalMethodCallContext globalCall,
                                        DocumentContext documentContext, TypeService typeService,
                                        List<TypedMember> sink) {
    var methodName = globalCall.methodName();
    if (methodName != null) {
      resolveInto(sink, documentContext, typeService, methodName.IDENTIFIER());
    }
  }

  /** Вызов метода на ресивере: имя проходит pre-filter, затем точный резолв на типе-владельце. */
  private static void resolveMethodCall(BSLParser.MethodCallContext methodCall,
                                        DocumentContext documentContext, TypeService typeService,
                                        List<TypedMember> sink) {
    var methodName = methodCall.methodName();
    if (methodName != null) {
      resolveCandidate(methodName.IDENTIFIER(), documentContext, typeService, sink);
    }
  }

  /**
   * Резолв конструируемого типа: имя типа из {@code Новый Тип(…)} + его
   * «страничные» метаданные. Форма {@code Новый(«ИмяТипа»)} и неизвестные
   * реестру имена пропускаются.
   */
  private static void resolveConstructedType(BSLParser.NewExpressionContext newExpression,
                                             DocumentContext documentContext,
                                             TypeService typeService,
                                             List<ConstructedType> sink) {
    var typeNameContext = newExpression.typeName();
    if (typeNameContext == null) {
      return;
    }
    var fileType = documentContext.getFileType();
    var typeName = typeNameContext.getText();
    typeService.resolve(typeName, fileType).ifPresent(typeRef -> sink.add(new ConstructedType(
      typeRef, typeName, typeService.getTypeMetadata(typeRef, fileType), typeNameContext)));
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
   * Члены типов (метод/свойство) — с pre-filter'ом по имени. Резолвятся только
   * версионные ({@link TypeService#isVersionedMemberName}) и следующие
   * 1С-конвенции «устарело» (префикс «Удалить»); остальные имена пропускаются,
   * чтобы не тратить инференс на каждый узел.
   */
  private static void resolveCandidate(@Nullable TerminalNode terminal, DocumentContext documentContext,
                                       TypeService typeService,
                                       List<TypedMember> sink) {
    if (terminal == null) {
      return;
    }
    var text = terminal.getText();
    if (typeService.isVersionedMemberName(text) || hasDeletedPrefix(text)) {
      resolveInto(sink, documentContext, typeService, terminal);
    }
  }

  private static void resolveInto(List<TypedMember> sink, DocumentContext documentContext,
                                  TypeService typeService, @Nullable TerminalNode terminal) {
    if (terminal == null) {
      return;
    }
    // Терминал уже на руках из обхода — передаём его напрямую, минуя повторный
    // спуск по AST для поиска терминала по позиции (доминирует на больших модулях).
    sink.addAll(typeService.membersAt(documentContext, terminal));
  }
}
