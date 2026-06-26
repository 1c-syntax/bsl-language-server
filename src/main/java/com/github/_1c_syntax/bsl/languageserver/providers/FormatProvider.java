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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.formatting.BslFormatter;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.bsl.BlockKeywordMatcher;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentRangesFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Провайдер для форматирования исходного кода.
 * <p>
 * Обрабатывает запросы {@code textDocument/formatting}, {@code textDocument/rangeFormatting} и
 * {@code textDocument/rangesFormatting}. Сам алгоритм форматирования вынесен в {@link BslFormatter};
 * провайдер только адаптирует протокольные запросы LSP к движку.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_formatting">Document Formatting Request specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rangeFormatting">Document Range Formatting Request specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rangesFormatting">Document Ranges Formatting Request specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_onTypeFormatting">Document On Type Formatting Request specification</a>
 */
@Component
@RequiredArgsConstructor
public final class FormatProvider {

  private final LanguageServerConfiguration configuration;
  private final BslFormatter formatter;

  public List<TextEdit> getFormatting(DocumentFormattingParams params, DocumentContext documentContext) {
    List<Token> tokens = documentContext.getTokens();
    if (tokens.isEmpty()) {
      return Collections.emptyList();
    }
    var firstToken = tokens.getFirst();
    var lastToken = tokens.getLast();

    var locale = documentContext.getScriptVariantLocale();
    var options = params.getOptions();
    var edits = getTextEdits(
      tokens,
      locale,
      Ranges.create(firstToken, lastToken), firstToken.getCharPositionInLine(), options
    );

    if (edits.isEmpty() || !(options.isInsertFinalNewline() || options.isTrimFinalNewlines())) {
      return edits;
    }

    var edit = edits.getFirst();
    edit.setNewText(normalizeFinalNewlines(edit.getNewText(), options));
    return edits;
  }

  /**
   * Приводит хвостовые переводы строк отформатированного текста к виду, требуемому
   * протокольными опциями {@link FormattingOptions}.
   * <p>
   * Диапазон правки полного форматирования всегда покрывает документ до конца, поэтому достаточно
   * нормализовать сам текст: {@code insertFinalNewline} гарантирует ровно один завершающий перевод
   * строки, а {@code trimFinalNewlines} (когда первый не взведён) удаляет все переводы строк после
   * последней содержательной строки.
   *
   * @param text    отформатированный текст без нормализации хвоста
   * @param options протокольные опции форматирования с взведённым флагом хвоста
   * @return текст с нормализованным хвостом переводов строк
   */
  private static String normalizeFinalNewlines(String text, FormattingOptions options) {
    var body = StringUtils.stripEnd(text, "\r\n");
    if (options.isInsertFinalNewline()) {
      return body + "\n";
    }
    return body;
  }

  /**
   * Возвращает правки форматирования при наборе указанного символа.
   * <p>
   * Поддерживаются триггеры:
   * <ul>
   *   <li>{@code "\n"} (Enter) — переформатирование предыдущей строки;</li>
   *   <li>{@code ";"} — переформатирование завершённого оператора текущей строки до позиции курсора.</li>
   * </ul>
   * Диапазон правки никогда не пересекает позицию ввода, чтобы не «дёргать» курсор пользователя.
   *
   * @param params          параметры запроса onTypeFormatting
   * @param documentContext контекст текущего документа
   * @return список правок (одна-единственная замена соответствующего диапазона) или пустой список,
   * если форматировать нечего
   */
  public List<TextEdit> getOnTypeFormatting(
    DocumentOnTypeFormattingParams params,
    DocumentContext documentContext
  ) {
    if (!configuration.getFormattingOptions().isUseOnTypeFormatting()) {
      return Collections.emptyList();
    }

    var window = resolveEditWindow(params.getCh(), params.getPosition(), documentContext);
    if (window == null) {
      return Collections.emptyList();
    }

    var allTokens = documentContext.getTokens();
    var lineTokens = collectLineTokens(allTokens, window.antlrLine, window.cutoffCharacter);
    if (lineTokens.firstSignificant == null) {
      return Collections.emptyList();
    }

    var adjustedRange = Ranges.create(
      window.editRange.getStart().getLine(),
      0,
      window.editRange.getEnd().getLine(),
      window.editRange.getEnd().getCharacter()
    );

    // startCharacter = колонка первого значимого токена, чтобы getNewText считал
    // currentIndentLevel от него. Decrement у первого токена даст 0, а ведущий
    // отступ мы подставим сами ниже.
    var baseEdits = getTextEdits(
      lineTokens.tokens,
      documentContext.getScriptVariantLocale(),
      adjustedRange,
      lineTokens.firstSignificant.getCharPositionInLine(),
      params.getOptions()
    );
    if (baseEdits.isEmpty()) {
      return baseEdits;
    }

    var targetIndent = resolveTargetIndent(
      documentContext, window.targetLineLsp, allTokens, lineTokens.firstSignificantIndex);
    var base = baseEdits.getFirst();
    return List.of(new TextEdit(base.getRange(), targetIndent + base.getNewText()));
  }

  private record EditWindow(int targetLineLsp, int antlrLine, Range editRange, int cutoffCharacter) {
  }

  private static @Nullable EditWindow resolveEditWindow(
    String ch, Position position, DocumentContext documentContext
  ) {
    if ("\n".equals(ch)) {
      return resolveEnterWindow(position, documentContext);
    }
    if (";".equals(ch)) {
      return resolveSemicolonWindow(position, documentContext);
    }
    return null;
  }

  private static @Nullable EditWindow resolveEnterWindow(Position position, DocumentContext documentContext) {
    if (position.getLine() == 0) {
      return null;
    }
    var targetLineLsp = position.getLine() - 1;
    var contentList = documentContext.getContentList();
    if (targetLineLsp >= contentList.length) {
      return null;
    }
    // Ограничиваем диапазон концом строки без переноса: только что набранный пользователем
    // перевод строки не должен попасть внутрь replace-range, иначе editor может проглотить
    // новую строку при отсутствии хвостового переноса в newText.
    var lineLength = contentList[targetLineLsp].length();
    var range = Ranges.create(targetLineLsp, 0, targetLineLsp, lineLength);
    return new EditWindow(targetLineLsp, targetLineLsp + 1, range, lineLength);
  }

  private static @Nullable EditWindow resolveSemicolonWindow(Position position, DocumentContext documentContext) {
    var targetLineLsp = position.getLine();
    var contentList = documentContext.getContentList();
    if (targetLineLsp >= contentList.length) {
      return null;
    }
    // Курсор может оказаться правее фактического конца синхронизированной строки (только что
    // набранный ';' ещё не доехал до серверной копии документа — рассинхрон у LSP4IJ). Без клампа
    // диапазон правки уехал бы за конец строки и форматтер дописал бы хвостовой перенос строки,
    // затирая набранный символ. Зеркалим клампинг ветки "\n".
    var cutoff = Math.min(position.getCharacter(), contentList[targetLineLsp].length());
    var range = Ranges.create(targetLineLsp, 0, targetLineLsp, cutoff);
    return new EditWindow(targetLineLsp, targetLineLsp + 1, range, cutoff);
  }

  private record LineTokens(List<Token> tokens, @Nullable Token firstSignificant, int firstSignificantIndex) {
  }

  private static LineTokens collectLineTokens(List<Token> allTokens, int antlrLine, int cutoffCharacter) {
    // Токены отсортированы по line — находим начало нужной строки бинарным поиском
    // и идём пока line совпадает. Все условия выхода — в заголовке while, поэтому
    // в теле цикла нет break/continue.
    var tokens = new ArrayList<Token>();
    @Nullable Token firstSignificant = null;
    var firstSignificantIndex = -1;
    var i = firstTokenIndexOnLine(allTokens, antlrLine);
    while (i < allTokens.size() && allTokens.get(i).getLine() == antlrLine) {
      var token = allTokens.get(i);
      // Конец токена должен укладываться в диапазон до позиции курсора — иначе токены,
      // выходящие за курсор (например многострочные литералы), дублируют свой хвост в replace.
      long endColumn = (long) token.getCharPositionInLine() + token.getText().length();
      if (endColumn <= cutoffCharacter) {
        tokens.add(token);
        if (firstSignificant == null
          && (token.getChannel() == Token.DEFAULT_CHANNEL || token.getType() == BSLLexer.LINE_COMMENT)) {
          firstSignificant = token;
          firstSignificantIndex = i;
        }
      }
      i++;
    }
    return new LineTokens(tokens, firstSignificant, firstSignificantIndex);
  }

  private static String resolveTargetIndent(
    DocumentContext documentContext,
    int targetLineLsp,
    List<Token> allTokens,
    int firstSignificantIndex
  ) {
    // Если первый значимый токен — закрывающее ключевое слово, выравниваем строку
    // по парному открывающему. Иначе сохраняем фактический leading whitespace строки:
    // это не даёт форматтеру срезать табуляцию у строк с decrement-keyword'ом и не
    // трогает руками проставленный отступ прочих строк.
    var contentList = documentContext.getContentList();
    var opener = BlockKeywordMatcher.findMatchingOpener(allTokens, firstSignificantIndex);
    if (opener != null) {
      return leadingWhitespace(contentList[opener.getLine() - 1]);
    }
    return leadingWhitespace(contentList[targetLineLsp]);
  }

  private static String leadingWhitespace(String line) {
    var i = 0;
    while (i < line.length()) {
      var c = line.charAt(i);
      if (c != ' ' && c != '\t') {
        break;
      }
      i++;
    }
    return line.substring(0, i);
  }


  private static int firstTokenIndexOnLine(List<Token> tokens, int line) {
    int lo = 0;
    int hi = tokens.size();
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (tokens.get(mid).getLine() < line) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }

  public List<TextEdit> getRangeFormatting(
    DocumentRangeFormattingParams params,
    DocumentContext documentContext
  ) {
    return getRangeFormatting(params.getRange(), params.getOptions(), documentContext);
  }

  /**
   * Возвращает правки форматирования сразу для нескольких диапазонов документа.
   * <p>
   * Обрабатывает запрос {@code textDocument/rangesFormatting} (LSP 3.18): каждый диапазон из
   * {@link DocumentRangesFormattingParams#getRanges()} форматируется независимо тем же алгоритмом,
   * что и одиночный {@code textDocument/rangeFormatting}, с одними и теми же протокольными
   * {@link FormattingOptions}. Правки от всех диапазонов объединяются в общий список.
   *
   * @param params          параметры запроса rangesFormatting с набором диапазонов и опциями
   * @param documentContext контекст текущего документа
   * @return объединённый список правок по всем переданным диапазонам; пустой, если форматировать
   * нечего
   */
  public List<TextEdit> getRangesFormatting(
    DocumentRangesFormattingParams params,
    DocumentContext documentContext
  ) {
    var options = params.getOptions();
    return params.getRanges().stream()
      .map(range -> getRangeFormatting(range, options, documentContext))
      .flatMap(List::stream)
      .collect(Collectors.toList());
  }

  private List<TextEdit> getRangeFormatting(
    Range range,
    FormattingOptions options,
    DocumentContext documentContext
  ) {
    Position start = range.getStart();
    Position end = range.getEnd();
    int startLine = start.getLine() + 1;
    int startCharacter = start.getCharacter();
    int endLine = end.getLine() + 1;
    int endCharacter = end.getCharacter();

    List<Token> tokens = documentContext.getTokens().stream()
      .filter((Token token) -> {
        int tokenLine = token.getLine();
        int tokenCharacter = token.getCharPositionInLine();
        return inLineRange(startLine, endLine, tokenLine)
          || (tokenLine == endLine && betweenStartAndStopCharacters(startCharacter, endCharacter, tokenCharacter));
      })
      .collect(Collectors.toList());

    return getTextEdits(
      tokens, documentContext.getScriptVariantLocale(), range, startCharacter, options);
  }

  @EventListener
  public void handleEvent(LanguageServerConfigurationChangedEvent event) {
    // No-op: per-workspace архитектура — конфигурация получается при каждом вызове
  }

  private static boolean betweenStartAndStopCharacters(int startCharacter, int endCharacter, int tokenCharacter) {
    return tokenCharacter >= startCharacter
      && tokenCharacter < endCharacter;
  }

  private static boolean inLineRange(int startLine, int endLine, int tokenLine) {
    return tokenLine >= startLine
      && tokenLine < endLine;
  }

  private List<TextEdit> getTextEdits(
    List<Token> tokens,
    Locale languageLocale,
    Range range,
    int startCharacter,
    FormattingOptions options
  ) {

    String newText = formatter.getNewText(tokens, languageLocale, range, startCharacter, options);

    if (newText.isEmpty()) {
      return Collections.emptyList();
    }

    var edit = new TextEdit(range, newText);

    return List.of(edit);

  }

}
