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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.util.SignatureSelection;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Inlay-hint'ы с именами параметров для вызовов ПЛАТФОРМЕННЫХ методов и
 * глобальных функций (по аналогии с {@link SourceDefinedMethodCallInlayHintCollector},
 * но для не-source-defined символов: {@code СтрНайти("a","b","",1)},
 * {@code Сообщение.Сообщить()}, {@code Новый Массив(5)} и т.п.).
 * <p>
 * Коллектор используется единым {@link MethodCallInlayHintSupplier} и отвечает за
 * резолв вызова; построение меток и tooltip делегируется
 * {@link PlatformMethodCallHintRenderer}. Резолв члена выполняется через
 * {@link TypeService#memberAt}, что покрывает три кейса:
 * <ul>
 *   <li>{@link BSLParser.GlobalMethodCallContext} — глобальная функция
 *       (СтрНайти, Сообщить, ПолучитьСообщенияПользователю …);</li>
 *   <li>{@link BSLParser.MethodCallContext} — доступ через точку
 *       (Объект.Метод());</li>
 *   <li>{@link BSLParser.NewExpressionContext} — конструктор
 *       (Новый Тип(...)) — обрабатывается отдельно через
 *       {@link TypeService#getConstructors}.</li>
 * </ul>
 * <p>
 * Source-defined вызовы покрывает {@link SourceDefinedMethodCallInlayHintCollector}
 * и здесь фильтруются — {@link TypeService#memberAt} для них возвращает
 * MemberDescriptor с непустым sourceSymbol.
 */
@Component
public class PlatformMethodCallInlayHintCollector {

  /** Делитель длины идентификатора для позиционирования курсора в его середине. */
  private static final int IDENTIFIER_CENTER_DIVISOR = 2;

  private final TypeService typeService;
  private final PlatformMethodCallHintRenderer renderer;

  public PlatformMethodCallInlayHintCollector(
    TypeService typeService,
    PlatformMethodCallHintRenderer renderer
  ) {
    this.typeService = typeService;
    this.renderer = renderer;
  }

  public List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params) {
    var ast = documentContext.getAst();
    if (ast == null) {
      return List.of();
    }
    var range = params.getRange();
    var result = new ArrayList<InlayHint>();
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_doCall)) {
      if (node instanceof BSLParser.DoCallContext doCall) {
        collectForDoCall(result, documentContext, range, doCall);
      }
    }
    return result;
  }

  private void collectForDoCall(
    List<InlayHint> sink,
    DocumentContext documentContext,
    Range range,
    BSLParser.DoCallContext doCall
  ) {
    var args = hintableArguments(doCall, range);
    if (args == null) {
      return;
    }
    // Конструкторы (Новый Тип(...)) резолвятся отдельно — memberAt
    // не вернёт MemberDescriptor для имени типа.
    if (doCall.getParent() instanceof BSLParser.NewExpressionContext nex) {
      appendConstructorHints(sink, documentContext, nex, args);
      return;
    }
    appendResolvedMethodHints(sink, documentContext, doCall, args);
  }

  /**
   * Фактические аргументы вызова, если он вообще подлежит подсказкам: есть непустой
   * список аргументов и открывающая скобка попадает в запрошенный диапазон.
   * {@code null}, если подсказки для вызова не нужны.
   */
  @Nullable
  private static List<? extends BSLParser.CallParamContext> hintableArguments(
    BSLParser.DoCallContext doCall,
    Range range
  ) {
    var paramList = doCall.callParamList();
    if (paramList == null || paramList.callParam().isEmpty()) {
      return null;
    }
    // Range-filter по позиции открывающей скобки вызова — дешёво.
    if (!Ranges.containsPosition(range, positionOf(doCall.getStart()))) {
      return null;
    }
    return paramList.callParam();
  }

  private void appendResolvedMethodHints(
    List<InlayHint> sink,
    DocumentContext documentContext,
    BSLParser.DoCallContext doCall,
    List<? extends BSLParser.CallParamContext> args
  ) {
    var methodNamePosition = methodNamePosition(doCall);
    if (methodNamePosition == null) {
      return;
    }
    var member = typeService.memberAt(documentContext, methodNamePosition)
      .map(TypeService.TypedMember::descriptor)
      .filter(m -> m.kind() == MemberKind.METHOD)
      // Source-defined методы покрывает SourceDefinedMethodCallInlayHintCollector.
      .filter(m -> m.sourceSymbol() == null)
      .orElse(null);
    if (member == null || member.signatures().isEmpty()) {
      return;
    }
    var argTypes = inferArgTypes(documentContext, args);
    var signature = pickSignatureByTypes(member.signatures(), args.size(), argTypes);
    if (signature == null) {
      return;
    }
    renderer.appendHints(sink, member, signature, args);
  }

  /**
   * Тип каждого фактического аргумента вызова. Для аргумента, чьё значение
   * не удалось проинферить — {@link TypeSet#EMPTY}; pick'ер сигнатуры
   * относится к таким нейтрально.
   */
  private List<TypeSet> inferArgTypes(
    DocumentContext documentContext,
    List<? extends BSLParser.CallParamContext> args
  ) {
    var result = new ArrayList<TypeSet>(args.size());
    for (var arg : args) {
      if (arg.getText().isBlank()) {
        result.add(TypeSet.EMPTY);
        continue;
      }
      var start = arg.getStart();
      var position = new Position(start.getLine() - 1, start.getCharPositionInLine());
      result.add(typeService.expressionTypesAt(documentContext, position));
    }
    return result;
  }

  /**
   * Inlay-хинты для конструктора {@code Новый Тип(...)}. Имя типа резолвится
   * через {@link TypeService#resolve}, сигнатуры — через
   * {@link TypeService#getConstructors}.
   * <p>
   * Конструкторы платформенных типов часто перегружены (например, {@code Массив}
   * имеет два варианта — variadic «По количеству элементов» и фиксированный
   * «На основании фиксированного массива»). Без type-inference аргумента
   * различить, какой именно вариант имел в виду пользователь, невозможно —
   * в таких случаях inlay скрывается, чтобы не показывать неверное имя параметра.
   */
  private void appendConstructorHints(
    List<InlayHint> sink,
    DocumentContext documentContext,
    BSLParser.NewExpressionContext nex,
    List<? extends BSLParser.CallParamContext> args
  ) {
    var typeName = nex.typeName() == null ? null : nex.typeName().getText();
    if (typeName == null || typeName.isBlank()) {
      return;
    }
    var fileType = documentContext.getFileType();
    var ref = typeService.resolve(typeName, fileType).orElse(null);
    // Конструкторы source-defined типов (OneScript-классы, ПриСозданииОбъекта)
    // покрывает SourceDefinedMethodCallInlayHintCollector — иначе подсказки
    // имён параметров задвоились бы. Здесь обрабатываем только платформенные.
    if (ref == null || ref.kind() == TypeKind.USER) {
      return;
    }
    var constructors = typeService.getConstructors(ref, fileType);
    if (constructors.isEmpty()) {
      return;
    }
    var argTypes = inferArgTypes(documentContext, args);
    var signature = pickSignatureByTypes(constructors, args.size(), argTypes);
    if (signature == null) {
      return;
    }
    var synthetic = MemberDescriptor.method(typeName, "", constructors);
    renderer.appendHints(sink, synthetic, signature, args);
  }

  /**
   * Возвращает позицию идентификатора имени метода в исходнике для
   * подходящих parent'ов doCall'а:
   * <ul>
   *   <li>{@link BSLParser.GlobalMethodCallContext} — methodName;</li>
   *   <li>{@link BSLParser.MethodCallContext} — methodName;</li>
   *   <li>{@link BSLParser.NewExpressionContext} — здесь special: позиция
   *       typeName (резолв конструктора через тип).</li>
   * </ul>
   * Возвращает {@code null} для не подходящих контекстов.
   */
  @Nullable
  private static Position methodNamePosition(BSLParser.DoCallContext doCall) {
    var parent = doCall.getParent();
    if (parent instanceof BSLParser.GlobalMethodCallContext gmc) {
      return idPosition(gmc.methodName() == null ? null : gmc.methodName().IDENTIFIER());
    }
    if (parent instanceof BSLParser.MethodCallContext mc) {
      return idPosition(mc.methodName() == null ? null : mc.methodName().IDENTIFIER());
    }
    // NewExpression обрабатывается отдельно — конструкторы у платформенных
    // классов резолвятся через TypeService.getConstructors, а memberAt
    // не вернёт MemberDescriptor для имени типа.
    return null;
  }

  @Nullable
  private static Position idPosition(@Nullable TerminalNode terminal) {
    if (terminal == null) {
      return null;
    }
    var token = terminal.getSymbol();
    // Внутрь токена, чтобы findTerminalNodeContainsPosition попал в IDENTIFIER.
    var col = token.getCharPositionInLine();
    var len = token.getText().length();
    return new Position(token.getLine() - 1, col + Math.max(0, len / IDENTIFIER_CENTER_DIVISOR));
  }

  private static Position positionOf(Token token) {
    return new Position(token.getLine() - 1, token.getCharPositionInLine());
  }

  /**
   * Выбирает signature по типам фактических аргументов. Делегирует в
   * {@link SignatureSelection#pickIndexByTypes(List, List)} для дезамбигации
   * перегруженных методов с одинаковой arity (например,
   * {@code ТЗ.Скопировать(Массив, …)} vs {@code ТЗ.Скопировать(Структура, …)}).
   */
  @Nullable
  private static SignatureDescriptor pickSignatureByTypes(
    List<SignatureDescriptor> signatures, int callArgCount, List<TypeSet> argTypes
  ) {
    if (signatures.isEmpty()) {
      return null;
    }
    if (signatures.size() == 1) {
      return signatures.get(0);
    }
    var idx = SignatureSelection.pickIndexByTypes(signatures, argTypes);
    if (idx < 0) {
      idx = SignatureSelection.pickIndexByArity(signatures, callArgCount);
    }
    return signatures.get(idx < 0 ? 0 : idx);
  }

}
