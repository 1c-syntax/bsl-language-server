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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
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

    var signatures = new ArrayList<SignatureInformation>(descriptor.signatures().size());
    for (var sig : descriptor.signatures()) {
      signatures.add(toSignatureInformation(descriptor.name(), sig));
    }

    var help = new SignatureHelp();
    help.setSignatures(signatures);
    help.setActiveSignature(0);
    help.setActiveParameter(activeParameter);
    return help;
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
    if (ast == null) {
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
      .<MemberDescriptor>map(SignatureHelpProvider::methodToDescriptor);
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
    // Walk up: ищем complexIdentifier ИЛИ callStatement (получатель)
    var receiver = findReceiverExpression(mc);
    if (receiver == null) {
      return Optional.empty();
    }
    // Позиция конца ресивера = непосредственно перед DOT текущего accessCall.
    // Родитель methodCall — это accessCall, чей первый токен — DOT.
    // receiver.getStop() даёт конец всего внешнего узла (вплоть до закрывающей ')'),
    // что неверно для цепочек вида `a.b.Method(` или `Var.Method(`.
    var accessCall = (mc.getParent() instanceof BSLParser.AccessCallContext ac) ? ac : null;
    Token receiverEnd;
    if (accessCall != null && accessCall.getStart() != null) {
      // Берём токен непосредственно перед DOT accessCall'a.
      var dotToken = accessCall.getStart();
      receiverEnd = findTokenBefore(receiver, dotToken);
      if (receiverEnd == null) {
        receiverEnd = receiver.getStop();
      }
    } else {
      receiverEnd = receiver.getStop();
    }
    if (receiverEnd == null) {
      return Optional.empty();
    }
    var pos = new Position(receiverEnd.getLine() - 1,
      receiverEnd.getCharPositionInLine() + receiverEnd.getText().length() - 1);
    var typeSet = typeService.findTypes(documentContext.getUri(), pos);
    if (typeSet.isEmpty()) {
      typeSet = typeService.inferAtPosition(documentContext, pos);
    }
    if (typeSet.isEmpty()) {
      // Fallback: голое имя OneScript library-модуля как ресивер (нет Symbol/инференса).
      // В BSL-файлах library-модули недоступны.
      if (documentContext.getFileType() != com.github._1c_syntax.bsl.languageserver.context.FileType.BSL) {
        var libRef = findLibraryModuleReceiver(mc, receiver);
        if (libRef != null) {
          typeSet = com.github._1c_syntax.bsl.languageserver.types.model.TypeSet.of(libRef);
        }
      }
    }
    for (TypeRef ref : typeSet.refs()) {
      for (var member : typeService.getMembers(ref, documentContext.getFileType())) {
        if (member.kind() == MemberKind.METHOD && member.name().equalsIgnoreCase(methodName)) {
          return Optional.of(member);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Находит последний токен внутри {@code receiver}, чей tokenIndex меньше tokenIndex {@code before}.
   * Используется чтобы получить позицию конца «приёмника» перед DOT текущего accessCall.
   */
  private static Token findTokenBefore(ParserRuleContext receiver, Token before) {
    if (receiver.getStart() == null || before == null) {
      return null;
    }
    return findTokenBeforeRec(receiver, before.getTokenIndex());
  }

  private static Token findTokenBeforeRec(ParseTree node, int limit) {
    if (node instanceof TerminalNode tn) {
      var token = tn.getSymbol();
      return (token.getTokenIndex() < limit) ? token : null;
    }
    Token best = null;
    for (int i = 0; i < node.getChildCount(); i++) {
      var child = findTokenBeforeRec(node.getChild(i), limit);
      if (child != null && (best == null || child.getTokenIndex() > best.getTokenIndex())) {
        best = child;
      }
    }
    return best;
  }

  private static ParserRuleContext findReceiverExpression(BSLParser.MethodCallContext mc) {
    // accessCall — родитель methodCall. Родитель accessCall — modifier (внутри complexIdentifier)
    // ИЛИ напрямую callStatement (для statements типа MyMod.method(...);).
    var node = (ParseTree) mc;
    while (node != null
      && !(node instanceof BSLParser.ComplexIdentifierContext)
      && !(node instanceof BSLParser.CallStatementContext)) {
      node = node.getParent();
    }
    return (ParserRuleContext) node;
  }

  /**
   * Если ресивер аксес-колла — голый идентификатор (нет промежуточных модификаторов
   * между {@code IDENTIFIER} ресивера и текущим methodCall), то пробуем
   * зарезолвить его как имя OneScript library-модуля.
   */
  private TypeRef findLibraryModuleReceiver(BSLParser.MethodCallContext mc, ParserRuleContext receiver) {
    TerminalNode idNode;
    List<? extends BSLParser.ModifierContext> modifiers;
    BSLParser.AccessCallContext directAccessCall = null;
    if (receiver instanceof BSLParser.ComplexIdentifierContext complex) {
      idNode = complex.IDENTIFIER();
      modifiers = complex.modifier();
    } else if (receiver instanceof BSLParser.CallStatementContext callStatement) {
      idNode = callStatement.IDENTIFIER();
      modifiers = callStatement.modifier();
      directAccessCall = callStatement.accessCall();
    } else {
      return null;
    }
    if (idNode == null) {
      return null;
    }
    // methodCall сидит либо в первом модификаторе complexIdentifier, либо
    // напрямую в callStatement.accessCall (без модификаторов между ними).
    boolean firstAccess;
    if (directAccessCall != null && (modifiers == null || modifiers.isEmpty())) {
      firstAccess = isInside(mc, directAccessCall);
    } else if (modifiers != null && !modifiers.isEmpty()) {
      firstAccess = isInside(mc, modifiers.get(0));
    } else {
      return null;
    }
    if (!firstAccess) {
      return null;
    }
    return globalScopeProvider.findLibraryModule(idNode.getText()).orElse(null);
  }

  private static boolean isInside(ParseTree descendant, ParseTree ancestor) {
    ParseTree walker = descendant;
    while (walker != null) {
      if (walker == ancestor) {
        return true;
      }
      walker = walker.getParent();
    }
    return false;
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
          && (member.name().equalsIgnoreCase("Constructor")
          || member.name().equalsIgnoreCase("ПриСозданииОбъекта"))) {
          return Optional.of(member);
        }
      }
      var platformCtors = typeService.getConstructors(ref);
      if (!platformCtors.isEmpty()) {
        return Optional.of(MemberDescriptor.method(typeName, typeService.getDescription(ref), platformCtors));
      }
    }
    // Fallback: OneScript library-класс — конструктор хранится отдельно в GlobalScopeProvider.
    // В BSL-файлах library-классы недоступны.
    if (fileType == com.github._1c_syntax.bsl.languageserver.context.FileType.BSL) {
      return Optional.empty();
    }
    var libCtor = globalScopeProvider.findLibraryClassConstructor(typeName);
    if (!libCtor.isEmpty()) {
      return Optional.of(MemberDescriptor.method(typeName, "", libCtor));
    }
    return Optional.empty();
  }

  private static MemberDescriptor methodToDescriptor(
    com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol method
  ) {
    var params = method.getParameters().stream()
      .map(p -> new ParameterDescriptor(
        p.getName(),
        com.github._1c_syntax.bsl.languageserver.types.model.TypeSet.EMPTY,
        p.isOptional(),
        ""
      ))
      .toList();
    var description = method.getDescription()
      .map(d -> d.getDescription() == null ? "" : d.getDescription().trim())
      .orElse("");
    var sig = new SignatureDescriptor(params, TypeRef.UNKNOWN, description);
    return MemberDescriptor.method(method.getName(), description, List.of(sig));
  }

  private static SignatureInformation toSignatureInformation(String methodName, SignatureDescriptor sig) {
    var label = new StringBuilder(methodName).append('(');
    var paramInfos = new ArrayList<ParameterInformation>(sig.parameters().size());
    for (int i = 0; i < sig.parameters().size(); i++) {
      var p = sig.parameters().get(i);
      if (i > 0) {
        label.append(", ");
      }
      int start = label.length();
      label.append(p.name());
      int end = label.length();
      var info = new ParameterInformation();
      info.setLabel(Either.forRight(new org.eclipse.lsp4j.jsonrpc.messages.Tuple.Two<>(start, end)));
      if (!p.description().isBlank()) {
        info.setDocumentation(p.description());
      }
      paramInfos.add(info);
    }
    label.append(')');
    var info = new SignatureInformation();
    info.setLabel(label.toString());
    info.setParameters(paramInfos);
    if (!sig.description().isBlank()) {
      info.setDocumentation(sig.description());
    }
    return info;
  }
}
