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
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.hover.MarkupContentBuilder;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Провайдер для отображения всплывающих подсказок при наведении курсора.
 *
 * <p>Тонкий слой поверх {@link ReferenceResolver}: резолвит ссылку под курсором
 * и выбирает {@link MarkupContentBuilder} по классу разрешённого символа.
 * Никакой собственной логики поиска символов или типов: всё, что относится к
 * подбору ссылки, живёт в реализациях {@link com.github._1c_syntax.bsl.languageserver.references.ReferenceFinder};
 * всё, что относится к формированию текста подсказки — в соответствующем
 * {@code MarkupContentBuilder}.
 *
 * <p>Перед резолвом ссылки обрабатывается особый случай: позиция курсора
 * на BSL-keyword'е ({@code Истина}, {@code Цикл}, {@code Попытка} …). Для
 * keyword'ов hover собирается напрямую из {@link GlobalScopeProvider} — они
 * не являются символами, поэтому в общий symbol-based flow не попадают.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_hover">Hover Request specification</a>
 */
@Component
@RequiredArgsConstructor
public final class HoverProvider {

  private final ReferenceResolver referenceResolver;
  private final GlobalScopeProvider globalScopeProvider;
  private final Map<Class<? extends Symbol>, MarkupContentBuilder<Symbol>> markupContentBuilders;

  public Optional<Hover> getHover(DocumentContext documentContext, HoverParams params) {
    var keywordHover = tryKeywordHover(documentContext, params);
    if (keywordHover.isPresent()) {
      return keywordHover;
    }
    return referenceResolver.findReference(documentContext.getUri(), params.getPosition())
      .flatMap(reference -> findBuilder(reference.symbol())
        .map(builder -> builder.getContent(reference.symbol()))
        .map(content -> new Hover(content, reference.selectionRange())));
  }

  /**
   * Если позиция указывает на keyword-токен ({@code Истина}, {@code Цикл}, …)
   * и для этого keyword'а в {@link GlobalScopeProvider} есть описание из
   * синтакс-помощника, возвращаем hover напрямую. В остальных случаях —
   * пусто, продолжаем обычным reference-резолвом.
   */
  private Optional<Hover> tryKeywordHover(DocumentContext documentContext, HoverParams params) {
    BSLParser.FileContext ast;
    try {
      ast = documentContext.getAst();
    } catch (NullPointerException e) {
      return Optional.empty();
    }
    var terminalOpt = Trees.findTerminalNodeContainsPosition(ast, params.getPosition());
    if (terminalOpt.isEmpty()) {
      return Optional.empty();
    }
    var terminal = terminalOpt.get();
    if (!isKeywordToken(terminal)) {
      return Optional.empty();
    }
    return globalScopeProvider.findKeywordDescription(terminal.getText())
      .map(description -> {
        var content = new MarkupContent(MarkupKind.MARKDOWN,
          "```bsl\n" + terminal.getText() + "\n```\n\n_ключевое слово_\n\n" + description);
        return new Hover(content, Ranges.create(terminal));
      });
  }

  /**
   * Эвристика: keyword-токены имеют тип, отличный от {@code IDENTIFIER},
   * {@code STRING}, {@code NUMBER}, и не относятся к пунктуации. Для
   * lookup'а описания достаточно проверить, что текст узла — известное
   * ключевое слово ({@link GlobalScopeProvider#findKeywordDescription}
   * сам отфильтрует неподходящие).
   * <p>
   * Мы намеренно не выкручиваем эвристику слишком жёстко: лишний промах
   * по identifier-токену с тем же написанием, что у keyword'а, безопасен —
   * keyword'ы и identifier'ы лексически непересекаются в BSL.
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

  private Optional<MarkupContentBuilder<Symbol>> findBuilder(Symbol symbol) {
    var direct = markupContentBuilders.get(symbol.getClass());
    if (direct != null) {
      return Optional.of(direct);
    }
    return markupContentBuilders.entrySet().stream()
      .filter(entry -> entry.getKey().isInstance(symbol))
      .map(Map.Entry::getValue)
      .findFirst();
  }
}
