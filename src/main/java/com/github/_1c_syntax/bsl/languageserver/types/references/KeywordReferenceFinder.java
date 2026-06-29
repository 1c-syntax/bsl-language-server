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
package com.github._1c_syntax.bsl.languageserver.types.references;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.KeywordSymbol;
import com.github._1c_syntax.bsl.languageserver.references.AnnotationReferenceFinder;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceFinder;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

/**
 * Поиск ссылок на BSL-keyword'ы ({@code Если}, {@code Истина}, {@code Цикл}…).
 * <p>
 * Keyword'ы не являются source-defined-символами и не попадают в общий
 * symbol-tree модуля, поэтому для их hover-обработки строится синтетический
 * {@link KeywordSymbol} на лету — по позиции курсора находится keyword-токен,
 * по его тексту и AST-контексту использования (Функция/Процедура/Перем для
 * body-keyword'ов вида {@code Знач}/{@code Возврат}/{@code Экспорт}) выбирается
 * описание из {@link GlobalScopeProvider#findKeywordDescription}. Полученный
 * {@code KeywordSymbol} проходит дальше через обычный
 * {@link com.github._1c_syntax.bsl.languageserver.providers.HoverProvider}-flow
 * и попадает в {@link com.github._1c_syntax.bsl.languageserver.hover.KeywordSymbolMarkupContentBuilder}.
 * <p>
 * Аналогично паттерну {@link AnnotationReferenceFinder}: оба создают
 * synthetic-символы on-the-fly без регистрации в symbol-tree.
 */
@Component
@Order(190)
@RequiredArgsConstructor
public class KeywordReferenceFinder implements ReferenceFinder {

  private final ServerContextProvider serverContextProvider;
  private final GlobalScopeProvider globalScopeProvider;
  private final LanguageServerConfiguration configuration;

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {
    var documentContext = serverContextProvider.getDocumentNoLock(uri).orElse(null);
    if (documentContext == null) {
      return Optional.empty();
    }
    BSLParser.FileContext ast;
    try {
      ast = documentContext.getAst();
    } catch (NullPointerException e) {
      return Optional.empty();
    }
    var terminalOpt = Trees.findTerminalNodeContainsPosition(ast, position);
    if (terminalOpt.isEmpty()) {
      return Optional.empty();
    }
    var terminal = terminalOpt.get();
    if (!isKeywordToken(terminal)) {
      return Optional.empty();
    }

    var lang = configuration.getLanguage();
    var parentContext = findKeywordParentContext(terminal);
    var descriptionOpt = globalScopeProvider.findKeywordDescription(
      terminal.getText(), lang, parentContext, documentContext.getFileType());
    if (descriptionOpt.isEmpty()) {
      return Optional.empty();
    }

    var selectionRange = Ranges.create(terminal);
    var keywordSymbol = new KeywordSymbol(terminal.getText(), descriptionOpt.get(), selectionRange);

    return Optional.of(Reference.of(
      documentContext.getSymbolTree().getModule(),
      keywordSymbol,
      new Location(uri.toString(), selectionRange)
    ));
  }

  /**
   * Эвристика: keyword-токены имеют тип, отличный от {@code IDENTIFIER},
   * {@code STRING}, {@code NUMBER}, и не относятся к пунктуации. Достаточно
   * проверить тип терминала: реальный фильтр по известному словарю keyword'ов —
   * на стороне {@link GlobalScopeProvider#findKeywordDescription}, который
   * вернёт {@link Optional#empty()} для неизвестных.
   */
  private static boolean isKeywordToken(TerminalNode terminal) {
    var token = terminal.getSymbol();
    int type = token.getType();
    return type != BSLParser.IDENTIFIER
      && type != BSLParser.STRING
      && type != BSLParser.DECIMAL
      && type != BSLParser.FLOAT
      && type != BSLParser.DATETIME
      && type != BSLParser.EOF;
  }

  /**
   * Поднимается по AST от позиции keyword-токена к ближайшей объемлющей
   * декларации и возвращает ru-имя соответствующей родительской конструкции
   * из СП: {@code "Функция"}, {@code "Процедура"} или {@code "Перем"}.
   * <p>
   * Используется для контекстно-зависимых описаний body-keyword'ов
   * ({@code Async}/{@code Знач}/{@code Возврат}/{@code Экспорт}/{@code КонецФункции}/
   * {@code КонецПроцедуры}) — у каждого из них своё описание в Функция vs Процедура;
   * у {@code Экспорт} есть ещё контекст модульной переменной ({@code Перем X Экспорт}).
   * Если контекст не определяется — {@code null}, и
   * {@link GlobalScopeProvider#findKeywordDescription} вернёт generic-описание.
   */
  @Nullable
  private static String findKeywordParentContext(TerminalNode terminal) {
    ParseTree node = terminal;
    while (node != null) {
      if (node instanceof BSLParser.FuncDeclarationContext
        || node instanceof BSLParser.FunctionContext) {
        return "Функция";
      }
      if (node instanceof BSLParser.ProcDeclarationContext
        || node instanceof BSLParser.ProcedureContext) {
        return "Процедура";
      }
      if (node instanceof BSLParser.ModuleVarDeclarationContext
        || node instanceof BSLParser.ModuleVarContext
        || node instanceof BSLParser.SubVarDeclarationContext) {
        return "Перем";
      }
      node = node instanceof ParserRuleContext prc ? prc.getParent() : node.getParent();
    }
    return null;
  }
}
