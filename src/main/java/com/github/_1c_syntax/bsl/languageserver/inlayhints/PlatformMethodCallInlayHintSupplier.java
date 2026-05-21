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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.util.SignatureSelection;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.Strings;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Inlay-hint'ы с именами параметров для вызовов ПЛАТФОРМЕННЫХ методов и
 * глобальных функций (по аналогии с {@link SourceDefinedMethodCallInlayHintSupplier},
 * но для не-source-defined символов: {@code СтрНайти("a","b","",1)},
 * {@code Сообщение.Сообщить()}, {@code Новый Массив(5)} и т.п.).
 * <p>
 * Резолв члена выполняется через {@link TypeService#findMemberAt}, что
 * покрывает три кейса:
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
 * Source-defined вызовы покрываются другим supplier'ом и здесь
 * фильтруются — {@link TypeService#findMemberAt} для них возвращает
 * MemberDescriptor с непустым sourceSymbol.
 */
@Component
@RequiredArgsConstructor
public class PlatformMethodCallInlayHintSupplier implements InlayHintSupplier {

  private static final boolean DEFAULT_SHOW_PARAMETERS_WITH_THE_SAME_NAME = false;
  private static final boolean DEFAULT_SHOW_DEFAULT_VALUES = true;

  private final TypeService typeService;
  private final LanguageServerConfiguration configuration;
  private final Resources resources;

  private Language currentLanguage() {
    return configuration.getLanguage();
  }

  @Override
  public List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params) {
    var ast = documentContext.getAst();
    if (ast == null) {
      return List.of();
    }
    var range = params.getRange();
    var result = new ArrayList<InlayHint>();
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_doCall)) {
      if (!(node instanceof BSLParser.DoCallContext doCall)) {
        continue;
      }
      var paramList = doCall.callParamList();
      if (paramList == null || paramList.callParam().isEmpty()) {
        continue;
      }
      // Range-filter по позиции открывающей скобки вызова — дешёво.
      if (!Ranges.containsPosition(range, positionOf(doCall.getStart()))) {
        continue;
      }
      var args = paramList.callParam();
      // Конструкторы (Новый Тип(...)) резолвятся отдельно — findMemberAt
      // не вернёт MemberDescriptor для имени типа.
      if (doCall.getParent() instanceof BSLParser.NewExpressionContext nex) {
        appendConstructorHints(result, documentContext, nex, args);
        continue;
      }
      var methodNamePosition = methodNamePosition(doCall);
      if (methodNamePosition == null) {
        continue;
      }
      var member = typeService.findMemberAt(documentContext, methodNamePosition)
        .map(TypeService.TypedMember::descriptor)
        .filter(m -> m.kind() == MemberKind.METHOD)
        // Source-defined методы покрывает SourceDefinedMethodCallInlayHintSupplier.
        .filter(m -> m.sourceSymbol() == null)
        .orElse(null);
      if (member == null || member.signatures().isEmpty()) {
        continue;
      }
      var argTypes = inferArgTypes(documentContext, args);
      var signature = pickSignatureByTypes(member.signatures(), args.size(), argTypes);
      if (signature == null) {
        continue;
      }
      appendHints(result, member, signature, args);
    }
    return result;
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
      var text = arg.getText();
      if (text == null || text.isBlank()) {
        result.add(TypeSet.EMPTY);
        continue;
      }
      var start = arg.getStart();
      var position = new Position(start.getLine() - 1, start.getCharPositionInLine());
      result.add(typeService.inferAtPosition(documentContext, position));
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
    if (nex.typeName() == null) {
      return;
    }
    var typeName = nex.typeName().getText();
    if (typeName == null || typeName.isBlank()) {
      return;
    }
    var fileType = documentContext.getFileType();
    var ref = typeService.resolve(typeName, fileType).orElse(null);
    if (ref == null) {
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
    appendHints(sink, synthetic, signature, args);
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
  private static Position methodNamePosition(BSLParser.DoCallContext doCall) {
    var parent = doCall.getParent();
    if (parent instanceof BSLParser.GlobalMethodCallContext gmc) {
      return idPosition(gmc.methodName() == null ? null : gmc.methodName().IDENTIFIER());
    }
    if (parent instanceof BSLParser.MethodCallContext mc) {
      return idPosition(mc.methodName() == null ? null : mc.methodName().IDENTIFIER());
    }
    // NewExpression обрабатывается отдельно — конструкторы у платформенных
    // классов резолвятся через TypeService.getConstructors, а findMemberAt
    // не вернёт MemberDescriptor для имени типа.
    return null;
  }

  private static Position idPosition(TerminalNode terminal) {
    if (terminal == null) {
      return null;
    }
    var token = terminal.getSymbol();
    // Внутрь токена, чтобы findTerminalNodeContainsPosition попал в IDENTIFIER.
    var col = token.getCharPositionInLine();
    var len = token.getText() == null ? 0 : token.getText().length();
    return new Position(token.getLine() - 1, col + Math.max(0, len / 2));
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

  private void appendHints(
    List<InlayHint> sink,
    MemberDescriptor member,
    SignatureDescriptor signature,
    List<? extends BSLParser.CallParamContext> args
  ) {
    var parameters = signature.parameters();
    var max = Math.min(parameters.size(), args.size());
    for (int i = 0; i < max; i++) {
      var parameter = parameters.get(i);
      var callParam = args.get(i);
      var passedValue = callParam.getText();
      if (passedValue == null || passedValue.isBlank()) {
        // `Новый Тип();` парсится как один пустой callParam — не показываем
        // хинт для пустого аргумента, иначе получим бессмысленное
        // `Новый Массив(Массив:);`.
        continue;
      }
      var label = parameter.displayName(currentLanguage());
      var bn = parameter.bilingualName();
      if (!showParametersWithTheSameName()
        && label != null
        && !label.isBlank()
        && (Strings.CI.contains(passedValue, label)
          || (!bn.ru().isEmpty() && Strings.CI.contains(passedValue, bn.ru()))
          || (!bn.en().isEmpty() && Strings.CI.contains(passedValue, bn.en())))) {
        continue;
      }
      var hint = new InlayHint();
      hint.setKind(InlayHintKind.Parameter);
      hint.setLabel(buildLabel(parameter, passedValue));
      hint.setPosition(positionOf(callParam.getStart()));
      if (!parameter.name().isBlank() || !parameter.description().isBlank()) {
        hint.setTooltip(buildTooltip(parameter, member));
      }
      hint.setPaddingRight(Boolean.TRUE);
      sink.add(hint);
    }
  }

  private String buildLabel(ParameterDescriptor parameter, String passedValue) {
    var sb = new StringBuilder();
    sb.append(parameter.displayName(currentLanguage()));
    if (showDefaultValues() && passedValue.isBlank() && !parameter.defaultValue().isBlank()) {
      sb.append(" (").append(parameter.defaultValue()).append(')');
    } else {
      sb.append(':');
    }
    return sb.toString();
  }

  private MarkupContent buildTooltip(ParameterDescriptor parameter, MemberDescriptor member) {
    var sb = new StringBuilder();
    var lang = currentLanguage();
    sb.append("**").append(parameter.displayName(lang)).append("**");
    if (parameter.optional()) {
      sb.append(" _(").append(tr("optionalParameter")).append(")_");
    }
    if (!parameter.defaultValue().isBlank()) {
      sb.append(" _= ").append(parameter.defaultValue()).append('_');
    }
    if (!parameter.types().isEmpty()) {
      sb.append(": ");
      var first = true;
      for (var ref : parameter.types().refs()) {
        if (!first) {
          sb.append(" | ");
        }
        sb.append(ref.qualifiedName());
        first = false;
      }
    }
    var pDesc = parameter.displayDescription(lang);
    if (!pDesc.isBlank()) {
      sb.append("\n\n").append(pDesc);
    }
    sb.append("\n\n_").append(tr("methodLabel")).append("_ `").append(member.displayName(lang)).append('`');
    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }

  private String tr(String key) {
    return resources.getResourceString(getClass(), key);
  }

  private boolean showParametersWithTheSameName() {
    var option = configuration.getInlayHintOptions().getParameters()
      .getOrDefault(getId(), Either.forLeft(true));
    if (option.isLeft()) {
      return DEFAULT_SHOW_PARAMETERS_WITH_THE_SAME_NAME;
    }
    return (boolean) option.getRight().getOrDefault(
      "showParametersWithTheSameName", DEFAULT_SHOW_PARAMETERS_WITH_THE_SAME_NAME);
  }

  private boolean showDefaultValues() {
    var option = configuration.getInlayHintOptions().getParameters()
      .getOrDefault(getId(), Either.forLeft(true));
    if (option.isLeft()) {
      return DEFAULT_SHOW_DEFAULT_VALUES;
    }
    return (boolean) option.getRight().getOrDefault(
      "showDefaultValues", DEFAULT_SHOW_DEFAULT_VALUES);
  }
}
