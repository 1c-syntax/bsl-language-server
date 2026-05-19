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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

/**
 * Поиск ссылки по «голому» идентификатору в позиции курсора.
 *
 * <p>В отличие от {@link ReferenceIndexReferenceFinder}, который опирается на
 * заранее построенный индекс, этот finder опрашивает symbol-tree документа
 * и глобальный скоуп «налету». Это нужно, поскольку для использования локальных
 * переменных, параметров метода, модульных переменных и глобальных synthetic-
 * символов (например, {@code КодировкаТекста}, {@code ФС}, {@code СтрЗаменить})
 * записи в {@code ReferenceIndex} не пишутся.
 *
 * <p>Используется, в частности, для типового резолва выражения вида
 * {@code A.} (visible identifier перед точкой), где парсер может не построить
 * complex-identifier-узел вокруг {@code A}.
 */
@Component
@Order(50)
@RequiredArgsConstructor
public class BareIdentifierReferenceFinder implements ReferenceFinder {

  private final ServerContextProvider serverContextProvider;
  private final GlobalScopeProvider globalScopeProvider;

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {
    return serverContextProvider.getDocumentUnsafe(uri)
      .flatMap(document -> findReference(document, uri, position));
  }

  private Optional<Reference> findReference(DocumentContext document, URI uri, Position position) {
    var token = findIdentifierTokenAt(document, position);
    if (token == null) {
      return Optional.empty();
    }
    var name = token.getText();
    var selectionRange = Ranges.create(token, token);

    var symbolTree = document.getSymbolTree();
    var module = symbolTree.getModule();
    var scope = symbolTree.getSymbolAtPosition(position);

    // Идентификатор в позиции имени конструктора (`Новый Имя(...)`) принадлежит
    // NewExpressionReferenceFinder: он отдаст ConstructorCallSymbol с сигнатурой
    // конструктора и описанием класса. Даже если в скоупе/глобальной области нашёлся
    // одноимённый символ (локальная переменная или синтетический объект конфигурации),
    // здесь мы его игнорируем, чтобы не подменять ховер/go-to-definition конструктора.
    if (isNewExpressionTypeName(document, position)) {
      return Optional.empty();
    }

    // Идентификатор в позиции accessor'а ({@code obj.IDENT}, {@code obj.METHOD(…)}) —
    // имя члена типа, а не bare identifier. Пытаться зарезолвить его как локальную
    // переменную/глобальный символ нельзя: это даст фантомное попадание, если в
    // скоупе случайно есть переменная/функция с таким же именем. Резолв члена —
    // дело PlatformMemberReferenceFinder через TypeService.findMemberAt.
    if (isAccessorIdentifier(document, position)) {
      return Optional.empty();
    }
    var resolved = resolveInScope(symbolTree, scope, module, name);
    if (resolved.isEmpty()) {
      var entry = globalScopeProvider.findGlobalEntry(name, document.getFileType());
      // Bare class identifier (Role.TYPE_NAME, e.g. `Структура`) — это не value-выражение,
      // его нельзя резолвить как «значение типа Структура» (привело бы к instance-member
      // автокомплиту на голом имени класса). Имя класса в позиции `Новый X(...)` уже
      // отфильтровано выше через isNewExpressionTypeName.
      resolved = entry
        .filter(e -> e.role() != com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope.Role.TYPE_NAME)
        .map(com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope.Entry::symbol);
    }

    return resolved.map(symbol -> enrichForHover(symbol, document.getFileType()))
      .map(symbol -> new Reference(
        module,
        symbol,
        uri,
        selectionRange,
        OccurrenceType.REFERENCE
      ));
  }

  /**
   * Для synthetic-методов (глобальные функции и методы платформенных типов)
   * подменяем символ на {@link PlatformMemberSymbol} — он несёт полный
   * {@link MemberDescriptor} (сигнатуры, параметры, описания), необходимый для
   * рендеринга hover'а. Для прочих synthetic-видов символ остаётся как есть —
   * у них собственный markup-builder.
   */
  private Symbol enrichForHover(Symbol symbol, com.github._1c_syntax.bsl.languageserver.context.FileType fileType) {
    if (!(symbol instanceof SyntheticSymbol synth)) {
      return symbol;
    }
    if (synth.getSyntheticKind() == SyntheticKind.PLATFORM_GLOBAL_METHOD) {
      var descriptor = globalScopeProvider.findFunction(synth.getName(), fileType);
      if (descriptor.isPresent()) {
        return new PlatformMemberSymbol(synth.getName(), null, descriptor.get(), -1);
      }
    }
    return symbol;
  }

  private static Optional<Symbol> resolveInScope(
    SymbolTree symbolTree,
    SourceDefinedSymbol scope,
    SourceDefinedSymbol module,
    String name
  ) {
    SourceDefinedSymbol current = scope;
    while (current != null && current != module) {
      if (current instanceof MethodSymbol) {
        var local = symbolTree.getVariableSymbol(name, current);
        if (local.isPresent()) {
          return local.map(Symbol.class::cast);
        }
      }
      current = current.getParent().orElse(null);
    }
    var moduleVar = symbolTree.getVariableSymbol(name, module);
    if (moduleVar.isPresent()) {
      return moduleVar.map(Symbol.class::cast);
    }
    var method = symbolTree.getMethodSymbol(name);
    if (method.isPresent()) {
      return method.map(Symbol.class::cast);
    }
    return Optional.empty();
  }

  /**
   * Проверяет, попадает ли позиция в имя типа конструкторного выражения
   * {@code Новый Имя(...)}. В этом случае идентификатор — не ссылка на
   * локальную переменную, даже если такая есть (классическое затенение в
   * самоприсваивании: {@code A = Новый A();}).
   */
  private static boolean isNewExpressionTypeName(DocumentContext document, Position position) {
    BSLParser.FileContext ast;
    try {
      ast = document.getAst();
    } catch (NullPointerException e) {
      return false;
    }
    if (ast == null) {
      return false;
    }
    return Trees.findTerminalNodeContainsPosition(ast, position)
      .map(terminal -> terminal.getParent() instanceof BSLParser.TypeNameContext tn
        ? tn.getParent() instanceof BSLParser.NewExpressionContext
        : false)
      .orElse(false);
  }

  /**
   * Проверяет, является ли идентификатор под курсором accessor'ом — то есть именем
   * свойства ({@code obj.IDENT}) или именем метода в цепочке вызова ({@code obj.METHOD(…)}).
   * Это зеркало логики из {@code TypeService.isAccessorIdentifier}: грамматика BSL
   * выделяет такие IDENTIFIER-ы в специальные продукции {@code accessProperty} и
   * {@code accessCall/methodCall/methodName}, и резолв их по локальному скоупу неверен.
   */
  private static boolean isAccessorIdentifier(DocumentContext document, Position position) {
    BSLParser.FileContext ast;
    try {
      ast = document.getAst();
    } catch (NullPointerException e) {
      return false;
    }
    if (ast == null) {
      return false;
    }
    return Trees.findTerminalNodeContainsPosition(ast, position)
      .map(terminal -> {
        var parent = terminal.getParent();
        if (parent instanceof BSLParser.AccessPropertyContext) {
          return true;
        }
        return parent instanceof BSLParser.MethodNameContext
          && parent.getParent() instanceof BSLParser.MethodCallContext mc
          && mc.getParent() instanceof BSLParser.AccessCallContext;
      })
      .orElse(false);
  }

  private static Token findIdentifierTokenAt(DocumentContext document, Position position) {
    int line = position.getLine() + 1;
    int character = position.getCharacter();
    java.util.List<Token> tokens;
    try {
      tokens = document.getTokensFromDefaultChannel();
    } catch (NullPointerException e) {
      return null;
    }
    for (Token token : tokens) {
      if (token.getType() != BSLLexer.IDENTIFIER) {
        continue;
      }
      if (token.getLine() != line) {
        continue;
      }
      int start = token.getCharPositionInLine();
      int end = start + token.getText().length();
      if (character >= start && character <= end) {
        return token;
      }
    }
    return null;
  }
}
