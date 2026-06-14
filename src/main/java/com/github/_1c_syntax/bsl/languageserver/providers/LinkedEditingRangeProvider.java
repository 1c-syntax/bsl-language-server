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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.LinkedEditingRangeParams;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Провайдер связанного редактирования вхождений символа.
 * <p>
 * Обрабатывает запросы {@code textDocument/linkedEditingRange}: по позиции курсора
 * возвращает диапазоны всех вхождений локального символа (локальной/модульной
 * переменной или параметра) в пределах текущего документа, чтобы клиент мог
 * синхронно редактировать их (по аналогии с парными HTML-тегами).
 * <p>
 * Для методов, общих модулей и любых межфайловых символов возвращается
 * {@code null}: связанное редактирование однодокументное и синхронное, поэтому
 * для них оно небезопасно.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_linkedEditingRange">Linked Editing Range specification</a>
 */
@Component
@RequiredArgsConstructor
public class LinkedEditingRangeProvider {

  /**
   * Регулярное выражение идентификатора 1С для клиента связанного редактирования.
   * <p>
   * Соответствует правилу лексера BSL {@code IDENTIFIER : LETTER (LETTER | DIGIT)*},
   * где {@code LETTER} — любая буква Unicode (в том числе кириллица) или подчёркивание,
   * а {@code DIGIT} — десятичная цифра. Передаётся клиенту, чтобы тот сохранял группу
   * связанного редактирования между перезапросами при наборе символов.
   */
  private static final String IDENTIFIER_WORD_PATTERN = "[\\p{L}_][\\p{L}0-9_]*";

  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;

  /**
   * Получить диапазоны связанного редактирования для символа под курсором.
   *
   * @param documentContext Контекст документа, в котором находится курсор
   * @param params          Параметры запроса {@code textDocument/linkedEditingRange}
   * @return Диапазоны всех вхождений локального символа в текущем документе либо
   *   {@code null}, если под курсором нет подходящего для связанного редактирования
   *   символа.
   */
  @Nullable
  public LinkedEditingRanges getLinkedEditingRanges(
    DocumentContext documentContext,
    LinkedEditingRangeParams params
  ) {
    var uri = documentContext.getUri();
    var position = params.getPosition();

    var maybeSymbol = resolveVariableSymbol(documentContext, uri, position);

    if (maybeSymbol.isEmpty()) {
      return null;
    }

    var symbol = maybeSymbol.get();
    if (!symbol.getOwner().getUri().equals(uri)) {
      return null;
    }

    var ranges = collectRanges(symbol, uri);
    if (ranges.isEmpty()) {
      return null;
    }

    return new LinkedEditingRanges(ranges, IDENTIFIER_WORD_PATTERN);
  }

  /**
   * Найти символ-переменную под курсором, толерантно обрабатывая позицию на границе идентификатора.
   * <p>
   * Сначала выполняется обычное разрешение ссылки по позиции курсора. Если в этой точке ничего не
   * найдено (типичный случай — каретка стоит вплотную ПОСЛЕ только что набранного идентификатора, а
   * поиск по позиции исключает конец токена), выполняется повторная попытка разрешения по позиции
   * внутри идентификатора, заканчивающегося ровно в позиции курсора. Это снимает гонку при наборе
   * символов во время переименования, когда клиент перезапрашивает диапазоны после каждой клавиши.
   *
   * @param documentContext Контекст документа, в котором находится курсор
   * @param uri             URI документа
   * @param position        Позиция курсора
   * @return Символ-переменная под курсором либо пустое значение, если подходящего символа нет
   */
  private Optional<VariableSymbol> resolveVariableSymbol(
    DocumentContext documentContext,
    URI uri,
    Position position
  ) {
    var maybeSymbol = findVariableSymbolAt(uri, position);
    if (maybeSymbol.isPresent()) {
      return maybeSymbol;
    }

    return identifierEndingAtPosition(documentContext, position)
      .flatMap(identifierPosition -> findVariableSymbolAt(uri, identifierPosition));
  }

  /**
   * Разрешить символ-переменную точно по указанной позиции.
   *
   * @param uri      URI документа
   * @param position Позиция, по которой разрешается ссылка
   * @return Символ-переменная по позиции либо пустое значение
   */
  private Optional<VariableSymbol> findVariableSymbolAt(URI uri, Position position) {
    return referenceResolver.findReference(uri, position)
      .flatMap(Reference::getSourceDefinedSymbol)
      .filter(VariableSymbol.class::isInstance)
      .map(VariableSymbol.class::cast);
  }

  /**
   * Найти позицию внутри идентификатора, заканчивающегося ровно в позиции курсора.
   * <p>
   * Используется тот же приём, что и в {@code SelectionRangeProvider}: поскольку поиск токена по
   * позиции содержит начало токена и исключает конец, для каретки на границе берётся позиция на один
   * символ левее и проверяется, что найденный токен заканчивается ровно в исходной позиции.
   *
   * @param documentContext Контекст документа
   * @param position        Позиция курсора (предполагается конец идентификатора)
   * @return Позиция начала идентификатора, заканчивающегося в позиции курсора, либо пустое значение
   */
  private Optional<Position> identifierEndingAtPosition(DocumentContext documentContext, Position position) {
    if (position.getCharacter() == 0) {
      return Optional.empty();
    }

    ParserRuleContext ast = documentContext.getAst();
    var positionInsideToken = new Position(position.getLine(), position.getCharacter() - 1);
    return Trees.findTerminalNodeContainsPosition(ast, positionInsideToken)
      .filter(terminalNode -> Ranges.create(terminalNode).getEnd().equals(position))
      .map(terminalNode -> Ranges.create(terminalNode).getStart());
  }

  /**
   * Собрать диапазоны имени символа: объявление и все вхождения в указанном документе.
   *
   * @param symbol Символ, объявленный в исходном коде
   * @param uri    URI документа, по которому фильтруются вхождения
   * @return Список диапазонов имени символа в пределах документа
   */
  private List<Range> collectRanges(SourceDefinedSymbol symbol, URI uri) {
    List<Range> ranges = new ArrayList<>();
    ranges.add(symbol.getSelectionRange());

    referenceIndex.getReferencesTo(symbol).stream()
      .filter(reference -> reference.uri().equals(uri))
      .map(Reference::selectionRange)
      .filter(range -> !ranges.contains(range))
      .forEach(ranges::add);

    return ranges;
  }
}
