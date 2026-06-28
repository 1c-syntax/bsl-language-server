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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializedEvent;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.utils.DescriptionTypes;
import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сапплаер семантических токенов для BSL документации (описаний методов и переменных).
 * <p>
 * Обеспечивает подсветку синтаксиса внутри комментариев-описаний (аналогично JavaDoc).
 */
@Component
@RequiredArgsConstructor
public class BslDocSemanticTokensSupplier implements SemanticTokensSupplier {

  private final SemanticTokensHelper helper;
  private final TypeService typeService;

  @Setter
  private boolean multilineTokenSupport;

  @EventListener
  public void onClientCapabilitiesChanged(LanguageServerInitializedEvent event) {
    multilineTokenSupport = Optional.of(event)
      .map(LanguageServerInitializedEvent::getParams)
      .map(InitializeParams::getCapabilities)
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getSemanticTokens)
      .map(SemanticTokensCapabilities::getMultilineTokenSupport)
      .orElse(false);
  }

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();
    var symbolTree = documentContext.getSymbolTree();

    // Process method descriptions. Типы параметров/возврата в описаниях методов задаются
    // структурно (после «Параметры:»/«Возвращаемое значение:»), поэтому подсвечиваются без
    // проверки резолва — чтобы не терять подсветку конфигурационных типов без загруженных метаданных.
    for (var method : symbolTree.getMethods()) {
      method.getDescription().ifPresent(description ->
        addBslDocDescriptionTokens(entries, description, multilineTokenSupport, documentContext, false)
      );
    }

    // Process variable descriptions. Тип переменной берётся из первого токена описания
    // (нотация «тип в начале»), что для свободного текста даёт ложные срабатывания. Поэтому
    // подсвечиваем как тип только то, что резолвится в реальный тип.
    for (var variable : symbolTree.getVariables()) {
      variable.getDescription().ifPresent(description -> {
        addBslDocDescriptionTokens(entries, description, multilineTokenSupport, documentContext, true);
        description.getTrailingDescription().ifPresent(trailing ->
          addBslDocDescriptionTokens(entries, trailing, multilineTokenSupport, documentContext, true)
        );
      });
    }

    return entries;
  }

  private void addBslDocDescriptionTokens(
    List<SemanticTokenEntry> entries,
    SourceDefinedSymbolDescription description,
    boolean multilineTokenSupport,
    DocumentContext documentContext,
    boolean validateTypeResolution
  ) {
    var range = description.getRange();
    if (range.isEmpty()) {
      return;
    }

    var descriptionText = description.getDescription();
    if (descriptionText.isEmpty()) {
      return;
    }

    // The description range start gives us the file position
    int fileStartLine = range.startLine();
    int fileStartChar = range.startCharacter();

    // Split the description text into lines to get line lengths
    var lines = descriptionText.split("\n", -1);

    // Collect semantic elements from AST (parameter names and keywords in structural positions).
    // Типы здесь НЕ подсвечиваются — они идут отдельным проходом из структурных аксессоров (addTypeTokens),
    // т.к. текстовая разметка TYPE_NAME не отдаёт вложенные типы коллекций/полей структур.
    var semanticElements = new ArrayList<SemanticTokenEntry>();

    for (var element : description.getElements()) {
      var semanticType = switch (element.type()) {
        case PARAMETER_NAME -> SemanticTokenTypes.Parameter;
        case RETURNS_KEYWORD, EXAMPLE_KEYWORD, PARAMETERS_KEYWORD, DEPRECATE_KEYWORD,
             CALL_OPTIONS_KEYWORD -> SemanticTokenTypes.Macro;
        default -> "";
      };
      helper.addDescriptionElement(semanticElements, element, semanticType, SemanticTokenModifiers.Documentation);
    }

    // Все типы описания (включая вложенные типы-значения коллекций «Массив из Число» → «Число»
    // и типы полей структур) подсвечиваются из структурных аксессоров парсера по element().range().
    // На корпусе описаний typesOf — надмножество TYPE_NAME-элементов getElements(), поэтому единый
    // источник покрывает и тип-головы, и вложенные типы без отдельного прохода по TYPE_NAME.
    addTypeTokens(semanticElements, description, validateTypeResolution, documentContext);

    // Sort elements by position
    semanticElements.sort(Comparator.comparingInt(SemanticTokenEntry::line)
      .thenComparingInt(SemanticTokenEntry::start));

    // Group elements by line
    var elementsByLine = new HashMap<Integer, List<SemanticTokenEntry>>();
    for (var element : semanticElements) {
      elementsByLine.computeIfAbsent(element.line(), k -> new ArrayList<>()).add(element);
    }

    if (multilineTokenSupport) {
      addBslDocTokensWithMultilineSupport(entries, lines, elementsByLine, fileStartLine, fileStartChar);
    } else {
      addBslDocTokensPerLine(entries, lines, elementsByLine, fileStartLine, fileStartChar);
    }
  }

  private void addBslDocTokensWithMultilineSupport(
    List<SemanticTokenEntry> entries,
    String[] lines,
    Map<Integer, List<SemanticTokenEntry>> elementsByLine,
    int fileStartLine,
    int fileStartChar
  ) {
    int lineIdx = 0;
    while (lineIdx < lines.length) {
      int fileLine = fileStartLine + lineIdx;
      String lineText = lines[lineIdx];
      int charOffset = (lineIdx == 0) ? fileStartChar : 0;

      var lineElements = elementsByLine.getOrDefault(fileLine, List.of());

      if (lineElements.isEmpty()) {
        int startLineIdx = lineIdx;

        while (lineIdx < lines.length) {
          var nextLineElements = elementsByLine.getOrDefault(fileLine, List.of());
          if (!nextLineElements.isEmpty()) {
            break;
          }
          lineIdx++;
        }

        int endLineIdx = lineIdx - 1;
        int startLine = fileStartLine + startLineIdx;
        int startChar = (startLineIdx == 0) ? fileStartChar : 0;

        if (startLineIdx == endLineIdx) {
          int lineLength = lines[startLineIdx].length();
          if (lineLength > 0) {
            addDocCommentRange(entries, startLine, startChar, lineLength);
          }
        } else {
          int totalLength = 0;
          for (int i = startLineIdx; i <= endLineIdx; i++) {
            totalLength += lines[i].length();
            if (i < endLineIdx) {
              totalLength += 1;
            }
          }
          if (totalLength > 0) {
            addDocCommentRange(entries, startLine, startChar, totalLength);
          }
        }
      } else {
        addBslDocTokensForLine(entries, fileLine, lineText, lineElements, charOffset);
        lineIdx++;
      }
    }
  }

  private void addBslDocTokensPerLine(
    List<SemanticTokenEntry> entries,
    String[] lines,
    Map<Integer, List<SemanticTokenEntry>> elementsByLine,
    int fileStartLine,
    int fileStartChar
  ) {
    for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
      int fileLine = fileStartLine + lineIdx;
      String lineText = lines[lineIdx];
      int lineLength = lineText.length();
      int charOffset = (lineIdx == 0) ? fileStartChar : 0;

      var lineElements = elementsByLine.getOrDefault(fileLine, List.of());

      if (lineElements.isEmpty()) {
        if (lineLength > 0) {
          addDocCommentRange(entries, fileLine, charOffset, lineLength);
        }
      } else {
        addBslDocTokensForLine(entries, fileLine, lineText, lineElements, charOffset);
      }
    }
  }

  private void addBslDocTokensForLine(
    List<SemanticTokenEntry> entries,
    int fileLine,
    String lineText,
    List<SemanticTokenEntry> elements,
    int charOffset
  ) {
    // Позиции элементов приходят от парсера в абсолютных координатах файла, а charOffset —
    // это абсолютный столбец начала строки описания (ненулевой для висячих/trailing комментариев,
    // которые начинаются не с начала строки). Поэтому работаем в абсолютных координатах:
    // прибавлять charOffset к позиции элемента нельзя — это приводило к двойному смещению
    // и «съезжавшей» подсветке типов в висячих (trailing) комментариях.
    int lineStart = charOffset;
    int lineEnd = charOffset + lineText.length();
    int currentPos = lineStart;

    for (var element : elements) {
      int elementStart = element.start();
      int elementLength = element.length();
      int elementEnd = elementStart + elementLength;

      if (currentPos < elementStart) {
        addDocCommentRange(entries, fileLine, currentPos, elementStart - currentPos);
      }

      entries.add(new SemanticTokenEntry(
        fileLine,
        elementStart,
        elementLength,
        element.type(),
        element.modifiers()
      ));

      currentPos = elementEnd;
    }

    if (currentPos < lineEnd) {
      addDocCommentRange(entries, fileLine, currentPos, lineEnd - currentPos);
    }
  }

  /**
   * Подсветить все типы описания из структурных аксессоров парсера ({@link DescriptionTypes#typesOf}).
   * <p>
   * Источник именно структурный, а не {@code TYPE_NAME}-элементы {@link SourceDefinedSymbolDescription#getElements()}:
   * текстовая разметка не отдаёт вложенные типы (типы-значения коллекций «Массив из Число» → «Число»,
   * типы полей структур). На корпусе описаний {@code typesOf} — надмножество {@code TYPE_NAME}, поэтому
   * отдельный проход по {@code TYPE_NAME} не нужен.
   * <p>
   * Для описаний переменных ({@code validateTypeResolution}) подсвечиваются только типы, резолвящиеся
   * в реальный тип через {@link TypeService}, — иначе нотация «тип в начале» висячего комментария
   * подсветила бы как тип любой первый токен свободного текста. Для описаний методов проверка не нужна
   * ({@code validateTypeResolution = false}): типы заданы структурно.
   */
  private void addTypeTokens(
    List<SemanticTokenEntry> semanticElements,
    SourceDefinedSymbolDescription description,
    boolean validateTypeResolution,
    DocumentContext documentContext
  ) {
    var fileType = documentContext.getFileType();
    DescriptionTypes.typesOf(description)
      .filter(type -> !validateTypeResolution || isResolvable(type, fileType))
      .map(type -> type.element().range())
      .distinct()
      .forEach(range -> helper.addEntry(semanticElements,
        range.startLine(), range.startCharacter(), range.length(),
        SemanticTokenTypes.Type, SemanticTokenModifiers.Documentation));
  }

  /**
   * Резолвится ли тип описания в реальный тип через {@link TypeService}. Имя типа для резолва берётся
   * из семантических аксессоров парсера ({@link DescriptionTypes#resolveName}); гиперссылки {@code См.}
   * (пустое имя) типами не считаются.
   */
  private boolean isResolvable(TypeDescription type, FileType fileType) {
    var name = DescriptionTypes.resolveName(type);
    return !name.isBlank() && typeService.resolve(name, fileType).isPresent();
  }

  private void addDocCommentRange(List<SemanticTokenEntry> entries, int line, int start, int length) {
    helper.addEntry(entries, line, start, length, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation);
  }
}
