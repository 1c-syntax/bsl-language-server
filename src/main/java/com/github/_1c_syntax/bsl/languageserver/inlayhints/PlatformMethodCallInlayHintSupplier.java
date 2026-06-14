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
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.Strings;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Inlay-hint'ы с именами параметров для вызовов ПЛАТФОРМЕННЫХ методов и
 * глобальных функций (по аналогии с {@link SourceDefinedMethodCallInlayHintSupplier},
 * но для не-source-defined символов: {@code СтрНайти("a","b","",1)},
 * {@code Сообщение.Сообщить()}, {@code Новый Массив(5)} и т.п.).
 * <p>
 * Резолв члена выполняется через {@link TypeService#memberAt}, что
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
 * фильтруются — {@link TypeService#memberAt} для них возвращает
 * MemberDescriptor с непустым sourceSymbol.
 */
@Component
public class PlatformMethodCallInlayHintSupplier
  extends AbstractMethodCallInlayHintSupplier<DefaultInlayHintData> {

  private final TypeService typeService;
  private final Resources resources;

  public PlatformMethodCallInlayHintSupplier(
    LanguageServerConfiguration configuration,
    TypeService typeService,
    Resources resources
  ) {
    super(configuration);
    this.typeService = typeService;
    this.resources = resources;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Платформенные методы не имеют исходного расположения, поэтому хинты не несут
   * ссылок и ничего не откладывают на резолв — используется дефолтный дата-класс
   * {@link DefaultInlayHintData}.
   *
   * @return Класс {@link DefaultInlayHintData}.
   */
  @Override
  public Class<DefaultInlayHintData> getInlayHintDataClass() {
    return DefaultInlayHintData.class;
  }

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
      // Конструкторы (Новый Тип(...)) резолвятся отдельно — memberAt
      // не вернёт MemberDescriptor для имени типа.
      if (doCall.getParent() instanceof BSLParser.NewExpressionContext nex) {
        appendConstructorHints(result, documentContext, nex, args);
        continue;
      }
      var methodNamePosition = methodNamePosition(doCall);
      if (methodNamePosition == null) {
        continue;
      }
      var member = typeService.memberAt(documentContext, methodNamePosition)
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

  private void appendHints(
    List<InlayHint> sink,
    MemberDescriptor member,
    SignatureDescriptor signature,
    List<? extends BSLParser.CallParamContext> args
  ) {
    var parameters = signature.parameters();
    if (parameters.isEmpty()) {
      return;
    }
    var lang = currentLanguage();
    var variadicIndex = variadicIndex(parameters);
    // Единственный пустой callParam — это запись вида `Метод()` / `Новый Тип()`,
    // т.е. ноль фактических аргументов. Пропуск аргумента (`Метод(а,,б)`) даёт
    // несколько callParam'ов, в которых пустой обозначает именно опущенный довод.
    var hasSkippedArguments = args.size() > 1;
    for (var i = 0; i < args.size(); i++) {
      var unit = paramAt(parameters, variadicIndex, i, lang);
      if (unit == null) {
        break;
      }
      appendHint(sink, member, unit, args.get(i), hasSkippedArguments);
    }
  }

  /** Имя параметра + дескриптор для конкретной позиции аргумента. */
  private record NamedParam(String name, ParameterDescriptor descriptor) {
  }

  /**
   * Параметр для позиции аргумента {@code i}. Вариадик-хвост разворачивается в
   * нумерованные имена по фактическим аргументам (Значение → Значение1, …).
   * {@code null}, если аргументов больше, чем параметров, и хвост не вариадик.
   */
  @Nullable
  private static NamedParam paramAt(List<ParameterDescriptor> parameters, int variadicIndex,
                                    int i, Language lang) {
    if (variadicIndex >= 0 && i >= variadicIndex) {
      var p = parameters.get(variadicIndex);
      return new NamedParam(p.displayName(lang) + (i - variadicIndex + 1), p);
    }
    if (i < parameters.size()) {
      var p = parameters.get(i);
      return new NamedParam(p.displayName(lang), p);
    }
    return null;
  }

  private void appendHint(List<InlayHint> sink, MemberDescriptor member, NamedParam unit,
                          BSLParser.CallParamContext callParam, boolean argumentSkipAllowed) {
    var passedValue = callParam.getText();
    // Пустой довод — либо `Метод()`/`Новый Тип()` (ноль аргументов, хинт не нужен),
    // либо пропущенный аргумент `Метод(а,,б)`. Для пропущенного при включённом
    // showDefaultValues показываем значение по умолчанию из сигнатуры.
    if (passedValue.isBlank()) {
      if (!argumentSkipAllowed || !showDefaultValues() || unit.descriptor().defaultValue().isBlank()) {
        return;
      }
    } else if (!showParametersWithTheSameName()
      && shadowsName(passedValue, unit.name(), unit.descriptor())) {
      return;
    } else {
      // непустой довод, не дублирующий имя параметра — показываем обычный хинт ниже
    }
    var hint = new InlayHint();
    hint.setKind(InlayHintKind.Parameter);
    hint.setLabel(buildLabel(unit.name(), unit.descriptor(), passedValue));
    hint.setPosition(positionOf(callParam.getStart()));
    if (!unit.name().isBlank() || !unit.descriptor().description().isBlank()) {
      hint.setTooltip(buildTooltip(unit.name(), unit.descriptor(), member));
    }
    hint.setPaddingRight(Boolean.TRUE);
    sink.add(hint);
  }

  /** Индекс вариадик-параметра (он же последний), либо {@code -1}. */
  private static int variadicIndex(List<ParameterDescriptor> parameters) {
    for (var i = 0; i < parameters.size(); i++) {
      if (parameters.get(i).variadic()) {
        return i;
      }
    }
    return -1;
  }

  /** Аргумент уже содержит имя параметра — хинт был бы избыточен. */
  private static boolean shadowsName(String passedValue, String paramName, ParameterDescriptor parameter) {
    if (paramName.isBlank()) {
      return false;
    }
    var bn = parameter.bilingualName();
    return Strings.CI.contains(passedValue, paramName)
      || (!bn.ru().isEmpty() && Strings.CI.contains(passedValue, bn.ru()))
      || (!bn.en().isEmpty() && Strings.CI.contains(passedValue, bn.en()));
  }

  private String buildLabel(String paramName, ParameterDescriptor parameter, String passedValue) {
    var sb = new StringBuilder();
    sb.append(paramName);
    if (showDefaultValues() && passedValue.isBlank() && !parameter.defaultValue().isBlank()) {
      sb.append(" (").append(parameter.defaultValue()).append(')');
    } else {
      sb.append(':');
    }
    return sb.toString();
  }

  private MarkupContent buildTooltip(String paramName, ParameterDescriptor parameter, MemberDescriptor member) {
    var sb = new StringBuilder();
    var lang = currentLanguage();
    sb.append("**").append(paramName).append("**");
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

}
