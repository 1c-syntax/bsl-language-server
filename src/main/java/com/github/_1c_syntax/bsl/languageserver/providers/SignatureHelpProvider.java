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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.util.SignatureSelection;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Провайдер для запросов {@code textDocument/signatureHelp}.
 * <p>
 * Логика работы:
 * <ol>
 *   <li>В позиции курсора ищется охватывающий узел {@code doCall} (аргументы вызова).</li>
 *   <li>По синтаксическому окружению определяется имя и тип вызываемого:
 *       глобальный метод, метод-аксессор через точку, конструктор (Новый).</li>
 *   <li>По {@link TypeService#getMembers} достаётся {@link MemberDescriptor} с сигнатурами.</li>
 *   <li>Сигнатуры маппятся в {@link SignatureInformation}; активный параметр — по количеству
 *       запятых между открывающей скобкой вызова и курсором.</li>
 * </ol>
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_signatureHelp">SignatureHelp specification</a>
 */
@Component
@RequiredArgsConstructor
public final class SignatureHelpProvider {

  private final TypeService typeService;
  private final GlobalScopeProvider globalScopeProvider;
  private final LanguageServerConfiguration configuration;

  /**
   * @return signature help для указанной позиции, либо пустой {@link SignatureHelp} если контекст
   *         не определяется или у вызываемого не зарегистрированы сигнатуры
   */
  public SignatureHelp getSignatureHelp(DocumentContext documentContext, SignatureHelpParams params) {
    var position = params.getPosition();
    var doCall = findEnclosingDoCall(documentContext, position).orElse(null);
    if (doCall == null) {
      return emptyHelp();
    }

    var activeParameter = computeActiveParameter(doCall, position);

    var callee = resolveCallee(documentContext, doCall);
    if (callee.isEmpty()) {
      return emptyHelp();
    }
    var descriptor = callee.get();
    if (descriptor.kind() != MemberKind.METHOD || descriptor.signatures().isEmpty()) {
      return emptyHelp();
    }

    // Signature help — элемент интерфейса: язык из настроек LS, а не ScriptVariant.
    var lang = configuration.getLanguage();
    var argCount = argCount(doCall);
    var signatures = new ArrayList<SignatureInformation>(descriptor.signatures().size());
    for (var sig : descriptor.signatures()) {
      signatures.add(toSignatureInformation(descriptor.displayName(lang), sig, lang, argCount));
    }

    var help = new SignatureHelp();
    help.setSignatures(signatures);
    help.setActiveSignature(pickActiveSignature(descriptor.signatures(), doCall, activeParameter, documentContext));
    help.setActiveParameter(activeParameter);
    return help;
  }

  /**
   * Выбирает {@code activeSignature} по типам уже введённых аргументов
   * (для перегруженных вариантов одинаковой arity). При неудаче — fallback
   * к {@link SignatureSelection#pickIndexByActiveParameter}, который
   * выбирает по позиции курсора.
   */
  private int pickActiveSignature(
    List<SignatureDescriptor> signatures,
    BSLParser.DoCallContext doCall,
    int activeParameter,
    DocumentContext documentContext
  ) {
    var argTypes = inferArgTypes(doCall, documentContext);
    int byTypes = SignatureSelection.pickIndexByTypes(signatures, argTypes);
    if (byTypes >= 0) {
      return byTypes;
    }
    return SignatureSelection.pickIndexByActiveParameter(signatures, activeParameter);
  }

  /**
   * Извлекает типы фактических аргументов из {@code doCall} через
   * {@link TypeService#expressionTypesAt(DocumentContext, org.eclipse.lsp4j.Position)}. Незаполненный
   * аргумент или аргумент с неизвестным типом → {@link TypeSet#EMPTY}.
   */
  private List<TypeSet> inferArgTypes(BSLParser.DoCallContext doCall, DocumentContext documentContext) {
    var paramList = doCall.callParamList();
    if (paramList == null) {
      return List.of();
    }
    var args = paramList.callParam();
    if (args.isEmpty()) {
      return List.of();
    }
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

  private static SignatureHelp emptyHelp() {
    var help = new SignatureHelp();
    help.setSignatures(Collections.emptyList());
    help.setActiveSignature(0);
    help.setActiveParameter(0);
    return help;
  }

  private static Optional<BSLParser.DoCallContext> findEnclosingDoCall(
    DocumentContext documentContext, Position position
  ) {
    BSLParser.FileContext ast;
    try {
      ast = documentContext.getAst();
    } catch (NullPointerException e) {
      return Optional.empty();
    }

    // Идём по дереву: ищем самый глубокий doCall, у которого LPAREN < pos <= RPAREN.
    return findInnermostDoCallEnclosing(ast, position);
  }

  private static Optional<BSLParser.DoCallContext> findInnermostDoCallEnclosing(
    ParseTree node, Position position
  ) {
    BSLParser.DoCallContext best = null;
    if (node instanceof BSLParser.DoCallContext doCall && encloses(doCall, position)) {
      best = doCall;
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      var child = node.getChild(i);
      if (child instanceof ParserRuleContext prc && !encloses(prc, position)) {
        continue;
      }
      var inner = findInnermostDoCallEnclosing(child, position);
      if (inner.isPresent()) {
        best = inner.get();
      }
    }
    return Optional.ofNullable(best);
  }

  private static boolean encloses(ParserRuleContext ctx, Position position) {
    var start = ctx.getStart();
    var stop = ctx.getStop();
    if (start == null || stop == null) {
      return false;
    }
    var range = Ranges.create(start.getLine() - 1, start.getCharPositionInLine(),
      stop.getLine() - 1, stop.getCharPositionInLine() + stop.getText().length());
    return Ranges.containsPosition(range, position);
  }

  /**
   * Активный параметр = число запятых между LPAREN и cursor (на верхнем уровне аргументов).
   */
  private static int computeActiveParameter(BSLParser.DoCallContext doCall, Position position) {
    int commas = 0;
    var paramList = doCall.callParamList();
    if (paramList == null) {
      return 0;
    }
    for (int i = 0; i < paramList.getChildCount(); i++) {
      var child = paramList.getChild(i);
      if (child instanceof TerminalNode tn) {
        var token = tn.getSymbol();
        if (token.getType() == BSLParser.COMMA && tokenBefore(token, position)) {
          commas++;
        }
      }
    }
    return commas;
  }

  private static boolean tokenBefore(Token token, Position position) {
    int tokLine = token.getLine() - 1;
    int tokCol = token.getCharPositionInLine();
    if (tokLine < position.getLine()) {
      return true;
    }
    if (tokLine > position.getLine()) {
      return false;
    }
    return tokCol < position.getCharacter();
  }

  /**
   * Резолвит вызываемого члена по контексту doCall.
   * Поддерживает: globalMethodCall, accessCall (через точку), newExpression (конструктор).
   */
  private Optional<MemberDescriptor> resolveCallee(DocumentContext documentContext, BSLParser.DoCallContext doCall) {
    var parent = doCall.getParent();
    if (parent instanceof BSLParser.GlobalMethodCallContext gmc) {
      return resolveGlobalMethodCall(documentContext, gmc);
    }
    if (parent instanceof BSLParser.MethodCallContext mc) {
      return resolveAccessCall(documentContext, mc);
    }
    if (parent instanceof BSLParser.NewExpressionContext nex) {
      return resolveConstructor(documentContext, nex);
    }
    return Optional.empty();
  }

  private Optional<MemberDescriptor> resolveGlobalMethodCall(
    DocumentContext documentContext, BSLParser.GlobalMethodCallContext gmc
  ) {
    var methodName = Optional.ofNullable(gmc.methodName())
      .map(BSLParser.MethodNameContext::IDENTIFIER)
      .map(TerminalNode::getText)
      .orElse(null);
    if (methodName == null) {
      return Optional.empty();
    }
    // Try local module first: find any export/non-export method by name in current document
    var local = documentContext.getSymbolTree().getMethods().stream()
      .filter(m -> m.getName().equalsIgnoreCase(methodName))
      .findFirst()
      .<MemberDescriptor>map(this::methodToDescriptor);
    if (local.isPresent()) {
      return local;
    }
    // Fallback: global builtin function — фильтруем по типу файла.
    return globalScopeProvider.findFunction(methodName, documentContext.getFileType());
  }

  private Optional<MemberDescriptor> resolveAccessCall(
    DocumentContext documentContext, BSLParser.MethodCallContext mc
  ) {
    var methodName = Optional.ofNullable(mc.methodName())
      .map(BSLParser.MethodNameContext::IDENTIFIER)
      .map(TerminalNode::getText)
      .orElse(null);
    if (methodName == null) {
      return Optional.empty();
    }
    // тип ресивера — по позиции имени метода
    var memberToken = mc.methodName().IDENTIFIER().getSymbol();
    var memberPos = new Position(memberToken.getLine() - 1, memberToken.getCharPositionInLine());
    var typeSet = typeService.receiverTypesAt(documentContext, memberPos);
    for (TypeRef ref : typeSet.refs()) {
      for (var member : typeService.getMembers(ref, documentContext.getFileType())) {
        if (member.kind() == MemberKind.METHOD && member.matches(methodName)) {
          return Optional.of(member);
        }
      }
    }
    return Optional.empty();
  }

  private Optional<MemberDescriptor> resolveConstructor(DocumentContext documentContext, BSLParser.NewExpressionContext nex) {
    var typeName = Optional.ofNullable(nex.typeName())
      .map(ParseTree::getText)
      .orElse(null);
    if (typeName == null || typeName.isBlank()) {
      return Optional.empty();
    }
    var fileType = documentContext.getFileType();
    var ref = typeService.resolve(typeName, fileType).orElse(null);
    if (ref != null) {
      for (var member : typeService.getMembers(ref, fileType)) {
        if (member.kind() == MemberKind.METHOD
          && (member.matches("Constructor")
          || member.matches("ПриСозданииОбъекта")
          || member.matches("OnObjectCreation"))) {
          return Optional.of(member);
        }
      }
      var platformCtors = typeService.getConstructors(ref, fileType);
      if (!platformCtors.isEmpty()) {
        return Optional.of(MemberDescriptor.method(typeName, typeService.getDescription(ref, fileType), platformCtors));
      }
    }
    // Library-классы регистрируются как user-types через OScriptModuleMembersProvider —
    // их конструкторы доступны через typeService.getConstructors выше.
    return Optional.empty();
  }

  private MemberDescriptor methodToDescriptor(
    com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol method
  ) {
    var declaredParamTypes = typeService.getParameterTypes(method);
    var parameters = method.getParameters();
    var params = new ArrayList<ParameterDescriptor>(parameters.size());
    for (int i = 0; i < parameters.size(); i++) {
      var p = parameters.get(i);
      var types = i < declaredParamTypes.size()
        ? declaredParamTypes.get(i)
        : TypeSet.EMPTY;
      var paramDescription = p.getDescription()
        .map(SignatureHelpProvider::buildParameterDescription)
        .orElse("");
      params.add(new ParameterDescriptor(p.getName(), types, p.isOptional(), paramDescription));
    }
    var description = method.getDescription()
      .map(d -> d.getDescription() == null ? "" : d.getDescription().trim())
      .orElse("");
    var declaredReturn = typeService.getDeclaredReturnTypes(method);
    var returnRef = declaredReturn.refs().stream().findFirst().orElse(TypeRef.UNKNOWN);
    var sig = new SignatureDescriptor(params, returnRef, description);
    return MemberDescriptor.method(method.getName(), description, List.of(sig));
  }

  private SignatureInformation toSignatureInformation(
    String methodName, SignatureDescriptor sig, Language lang, int argCount
  ) {
    var label = new StringBuilder(methodName).append('(');
    var units = expandParameters(sig.parameters(), lang, argCount);
    var paramInfos = new ArrayList<ParameterInformation>(units.size());
    for (var i = 0; i < units.size(); i++) {
      if (i > 0) {
        label.append(", ");
      }
      paramInfos.add(appendParameter(label, units.get(i), lang));
    }
    label.append(')');
    if (sig.returnType() != null && sig.returnType() != TypeRef.UNKNOWN) {
      label.append(": ").append(typeService.displayName(sig.returnType(), lang));
    }
    var info = new SignatureInformation();
    info.setLabel(label.toString());
    info.setParameters(paramInfos);
    var sigDesc = sig.displayDescription(lang);
    if (!sigDesc.isBlank()) {
      info.setDocumentation(sigDesc);
    }
    return info;
  }

  /**
   * Дописать в {@code label} один параметр ({@code Имя: Тип? = Значение}) и вернуть
   * соответствующий {@link ParameterInformation} с диапазоном подсветки.
   * Необязательный параметр помечается «?»: знак приклеивается к типу
   * ({@code Имя: Тип?}), а при отсутствии типа — к имени ({@code Имя?}).
   */
  private ParameterInformation appendParameter(StringBuilder label, ParamUnit unit, Language lang) {
    var p = unit.descriptor();
    var start = label.length();
    label.append(unit.name());
    var typesLabel = renderTypes(p.types(), lang);
    if (!typesLabel.isEmpty()) {
      label.append(": ").append(typesLabel);
    }
    if (p.optional()) {
      label.append('?');
    }
    if (p.optional() && !p.defaultValue().isBlank()) {
      // Платформенный синтаксис: «ИмяПараметра = ЗначениеПоУмолчанию».
      label.append(" = ").append(p.defaultValue());
    }
    var end = label.length();
    var info = new ParameterInformation();
    info.setLabel(Either.forRight(new Tuple.Two<>(start, end)));
    var pDesc = p.displayDescription(lang);
    if (!pDesc.isBlank()) {
      info.setDocumentation(pDesc);
    }
    return info;
  }

  /** Имя параметра + его дескриптор для отрисовки в signature help. */
  private record ParamUnit(String name, ParameterDescriptor descriptor) {
  }

  /**
   * Разворачивает параметры сигнатуры в плоский список единиц отрисовки.
   * Вариадик-параметр (база {@code Значение}) превращается в нумерованные
   * {@code Значение1}, {@code Значение2}, … по фактическому числу аргументов
   * в хвосте плюс один следующий (минимум один). Остальные параметры — 1:1.
   */
  private static List<ParamUnit> expandParameters(
    List<ParameterDescriptor> params, Language lang, int argCount
  ) {
    var units = new ArrayList<ParamUnit>(params.size());
    for (var i = 0; i < params.size(); i++) {
      var p = params.get(i);
      if (p.variadic()) {
        var count = Math.max(1, Math.max(0, argCount - i) + 1);
        for (var k = 1; k <= count; k++) {
          units.add(new ParamUnit(p.displayName(lang) + k, p));
        }
      } else {
        units.add(new ParamUnit(p.displayName(lang), p));
      }
    }
    return units;
  }

  private static int argCount(BSLParser.DoCallContext doCall) {
    var paramList = doCall.callParamList();
    if (paramList == null) {
      return 0;
    }
    return (int) paramList.callParam().stream()
      .map(ParseTree::getText)
      .filter(text -> text != null && !text.isBlank())
      .count();
  }

  private String renderTypes(TypeSet types, Language lang) {
    if (types == null || types.isEmpty()) {
      return "";
    }
    var sb = new StringBuilder();
    boolean first = true;
    for (var ref : types.refs()) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(typeService.displayName(ref, lang));
      first = false;
    }
    return sb.toString();
  }

  /**
   * Извлекает текстовое описание параметра из его {@code ParameterDescription},
   * склеивая описания каждой типизированной строки.
   */
  private static String buildParameterDescription(
    com.github._1c_syntax.bsl.parser.description.ParameterDescription pd
  ) {
    var typeDescriptions = pd.types();
    if (typeDescriptions == null || typeDescriptions.isEmpty()) {
      return "";
    }
    var sb = new StringBuilder();
    for (var td : typeDescriptions) {
      var text = td.description();
      if (text == null || text.isBlank()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(text.trim());
    }
    return sb.toString();
  }
}
