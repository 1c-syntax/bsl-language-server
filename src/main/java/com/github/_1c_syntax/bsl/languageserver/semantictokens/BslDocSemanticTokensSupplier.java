/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLMethodDescriptionLexer;
import com.github._1c_syntax.bsl.parser.BSLMethodDescriptionParser;
import com.github._1c_syntax.bsl.parser.BSLMethodDescriptionTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.jspecify.annotations.Nullable;
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

  @Setter
  private boolean multilineTokenSupport;

  @EventListener
  public void onClientCapabilitiesChanged(LanguageServerInitializeRequestReceivedEvent event) {
    multilineTokenSupport = Optional.of(event)
      .map(LanguageServerInitializeRequestReceivedEvent::getParams)
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

    // Process method descriptions
    for (var method : symbolTree.getMethods()) {
      method.getDescription().ifPresent(description ->
        addBslDocDescriptionTokens(entries, description, multilineTokenSupport)
      );
    }

    // Process variable descriptions
    for (var variable : symbolTree.getVariables()) {
      variable.getDescription().ifPresent(description -> {
        addBslDocDescriptionTokens(entries, description, multilineTokenSupport);
        description.getTrailingDescription().ifPresent(trailing ->
          addBslDocDescriptionTokens(entries, trailing, multilineTokenSupport)
        );
      });
    }

    return entries;
  }

  private void addBslDocDescriptionTokens(
    List<SemanticTokenEntry> entries,
    SourceDefinedSymbolDescription description,
    boolean multilineTokenSupport
  ) {
    var range = description.getRange();
    if (Ranges.isEmpty(range)) {
      return;
    }

    var descriptionText = description.getDescription();
    if (descriptionText.isEmpty()) {
      return;
    }

    // Re-parse description to get AST for parameter names and types
    var tokenizer = new BSLMethodDescriptionTokenizer(descriptionText);
    var ast = tokenizer.getAst();
    var tokens = tokenizer.getTokens();

    // The description range start gives us the file position
    int fileStartLine = range.getStart().getLine();
    int fileStartChar = range.getStart().getCharacter();

    // Collect semantic elements from AST (parameter names, types, and keywords in structural positions)
    var semanticElements = new ArrayList<SemanticTokenEntry>();
    if (ast != null) {
      collectBslDocSemanticElements(ast, semanticElements);
    }

    // Collect operator tokens from lexer (DASH, STAR) - these are safe to highlight everywhere
    for (Token token : tokens) {
      if (isOperatorToken(token.getType())) {
        helper.addTokenRange(semanticElements, token, SemanticTokenTypes.Operator, SemanticTokenModifiers.Documentation);
      }
    }

    // Sort elements by position
    semanticElements.sort(Comparator.comparingInt(SemanticTokenEntry::line)
      .thenComparingInt(SemanticTokenEntry::start));

    // Group elements by line
    var elementsByLine = new HashMap<Integer, List<SemanticTokenEntry>>();
    for (var element : semanticElements) {
      elementsByLine.computeIfAbsent(element.line(), k -> new ArrayList<>()).add(element);
    }

    // Split the description text into lines to get line lengths
    var lines = descriptionText.split("\n", -1);

    if (multilineTokenSupport) {
      addBslDocTokensWithMultilineSupport(entries, lines, elementsByLine, fileStartLine, fileStartChar);
    } else {
      addBslDocTokensPerLine(entries, lines, elementsByLine, fileStartLine, fileStartChar);
    }
  }

  private void collectBslDocSemanticElements(
    BSLMethodDescriptionParser.MethodDescriptionContext ast,
    List<SemanticTokenEntry> elements
  ) {
    // "Параметры:" keyword
    var parameters = ast.parameters();
    if (parameters != null) {
      helper.addTerminalNodeRange(elements, parameters.PARAMETERS_KEYWORD(), SemanticTokenTypes.Macro, SemanticTokenModifiers.Documentation);
      if (parameters.parameterString() != null) {
        for (var paramString : parameters.parameterString()) {
          if (paramString.parameter() != null) {
            collectParameterElements(paramString.parameter(), elements);
          }
          if (paramString.typesBlock() != null) {
            collectTypesBlockElements(paramString.typesBlock(), elements);
          }
          if (paramString.subParameter() != null) {
            collectSubParameterElements(paramString.subParameter(), elements);
          }
        }
      }
    }

    // "Возвращаемое значение:" keyword
    var returnsValues = ast.returnsValues();
    if (returnsValues != null) {
      helper.addTerminalNodeRange(elements, returnsValues.RETURNS_KEYWORD(), SemanticTokenTypes.Macro, SemanticTokenModifiers.Documentation);
      if (returnsValues.returnsValuesString() != null) {
        for (var returnString : returnsValues.returnsValuesString()) {
          if (returnString.returnsValue() != null) {
            var type = returnString.returnsValue().type();
            if (type != null) {
              helper.addContextRange(elements, type, SemanticTokenTypes.Type, SemanticTokenModifiers.Documentation);
            }
          }
          if (returnString.typesBlock() != null) {
            collectTypesBlockElements(returnString.typesBlock(), elements);
          }
          if (returnString.subParameter() != null) {
            collectSubParameterElements(returnString.subParameter(), elements);
          }
        }
      }
    }

    // "Пример:" keyword
    var examples = ast.examples();
    if (examples != null) {
      helper.addTerminalNodeRange(elements, examples.EXAMPLE_KEYWORD(), SemanticTokenTypes.Macro, SemanticTokenModifiers.Documentation);
    }

    // "Варианты вызова:" keyword
    var callOptions = ast.callOptions();
    if (callOptions != null) {
      helper.addTerminalNodeRange(elements, callOptions.CALL_OPTIONS_KEYWORD(), SemanticTokenTypes.Macro, SemanticTokenModifiers.Documentation);
    }

    // "Устарела." keyword
    var deprecate = ast.deprecate();
    if (deprecate != null) {
      helper.addTerminalNodeRange(elements, deprecate.DEPRECATE_KEYWORD(), SemanticTokenTypes.Macro, SemanticTokenModifiers.Documentation);
    }
  }

  private void collectParameterElements(
    BSLMethodDescriptionParser.ParameterContext parameter,
    List<SemanticTokenEntry> elements
  ) {
    var paramName = parameter.parameterName();
    if (paramName != null) {
      helper.addContextRange(elements, paramName, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Documentation);
    }

    collectTypesBlockElements(parameter.typesBlock(), elements);
  }

  private void collectSubParameterElements(
    BSLMethodDescriptionParser.SubParameterContext subParameter,
    List<SemanticTokenEntry> elements
  ) {
    var paramName = subParameter.parameterName();
    if (paramName != null) {
      helper.addContextRange(elements, paramName, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Documentation);
    }

    collectTypesBlockElements(subParameter.typesBlock(), elements);
  }

  private void collectTypesBlockElements(
    BSLMethodDescriptionParser.@Nullable TypesBlockContext typesBlock,
    List<SemanticTokenEntry> elements
  ) {
    if (typesBlock != null && typesBlock.type() != null) {
      helper.addContextRange(elements, typesBlock.type(), SemanticTokenTypes.Type, SemanticTokenModifiers.Documentation);
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

      var lineElements = elementsByLine.getOrDefault(lineIdx, List.of());

      if (lineElements.isEmpty()) {
        int startLineIdx = lineIdx;

        while (lineIdx < lines.length) {
          var nextLineElements = elementsByLine.getOrDefault(lineIdx, List.of());
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

      var lineElements = elementsByLine.getOrDefault(lineIdx, List.of());

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
    int lineEnd = lineText.length();
    int currentPos = 0;

    for (var element : elements) {
      int elementStart = element.start();
      int elementLength = element.length();
      int elementEnd = elementStart + elementLength;

      if (currentPos < elementStart) {
        addDocCommentRange(entries, fileLine, charOffset + currentPos, elementStart - currentPos);
      }

      // Add the element with adjusted position
      entries.add(new SemanticTokenEntry(
        fileLine,
        charOffset + elementStart,
        elementLength,
        element.type(),
        element.modifiers()
      ));

      currentPos = elementEnd;
    }

    if (currentPos < lineEnd) {
      addDocCommentRange(entries, fileLine, charOffset + currentPos, lineEnd - currentPos);
    }
  }

  private void addDocCommentRange(List<SemanticTokenEntry> entries, int line, int start, int length) {
    helper.addEntry(entries, line, start, length, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation);
  }

  private static boolean isOperatorToken(int tokenType) {
    return tokenType == BSLMethodDescriptionLexer.DASH || tokenType == BSLMethodDescriptionLexer.STAR;
  }
}


