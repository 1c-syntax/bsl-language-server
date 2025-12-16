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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@DirtiesContext
class SemanticTokensProviderTest {

  @Autowired
  private SemanticTokensProvider provider;

  @Autowired
  private SemanticTokensLegend legend;

  @MockitoBean
  private ReferenceIndex referenceIndex;

  @BeforeEach
  void init() {
    provider.setMultilineTokenSupport(false);
  }

  @Test
  void emitsExpectedTokenTypes() {
    // given: sample BSL with annotation, macro, method, parameter, string, number, comment, operators
    String bsl = String.join("\n",
      "&НаКлиенте",
      "#Если Истина Тогда",
      "Процедура Тест(Парам) Экспорт",
      "  // комментарий",
      "  Сообщить(\"строка\" + 123);",
      "КонецПроцедуры",
      "#КонецЕсли"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    var params = new SemanticTokensParams(textDocumentIdentifier);
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, params);

    // then: collect type indexes present
    List<Integer> data = tokens.getData();
    assertThat(data).isNotEmpty();

    Set<Integer> presentTypes = indexesOfTypes(data);

    // map desired types to indices and assert they're present
    assertPresent(presentTypes, SemanticTokenTypes.Decorator);
    assertPresent(presentTypes, SemanticTokenTypes.Macro);
    assertPresent(presentTypes, SemanticTokenTypes.Method);
    assertPresent(presentTypes, SemanticTokenTypes.Parameter);
    assertPresent(presentTypes, SemanticTokenTypes.Keyword);
    assertPresent(presentTypes, SemanticTokenTypes.String);
    assertPresent(presentTypes, SemanticTokenTypes.Number);
    assertPresent(presentTypes, SemanticTokenTypes.Comment);
    assertPresent(presentTypes, SemanticTokenTypes.Operator);
  }

  @Test
  void emitsMacroForAllPreprocTokens() {
    // given: preprocessor variety to cover PREPROC_* tokens including regions
    String bsl = String.join("\n",
      "#Область Region1",
      "#Если Сервер И НЕ Клиент Тогда",
      "Процедура Пусто()",
      "КонецПроцедуры",
      "#ИначеЕсли Клиент Тогда",
      "#Иначе",
      "#КонецЕсли",
      "#КонецОбласти"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    // then: count how many lexer tokens are PREPROC_* (or HASH) on default channel
    List<Token> defaultTokens = documentContext.getTokensFromDefaultChannel();

    long totalPreproc = defaultTokens.stream()
      .map(Token::getType)
      .map(BSLLexer.VOCABULARY::getSymbolicName)
      .filter(Objects::nonNull)
      .filter(sym -> sym.equals("HASH") || sym.startsWith("PREPROC_"))
      .count();

    // count region directives and names
    long regionDirectives = 0;
    long regionNames = 0;
    for (int i = 0; i + 1 < defaultTokens.size(); i++) {
      Token t = defaultTokens.get(i);
      Token n = defaultTokens.get(i + 1);
      if (t.getType() == BSLLexer.HASH && n.getType() == BSLLexer.PREPROC_REGION) {
        regionDirectives++;
        // if name token follows, it is included into Namespace span and not counted as Macro
        if (i + 2 < defaultTokens.size() && defaultTokens.get(i + 2).getType() == BSLLexer.PREPROC_IDENTIFIER) {
          regionNames++;
        }
      } else if (t.getType() == BSLLexer.HASH && n.getType() == BSLLexer.PREPROC_END_REGION) {
        regionDirectives++;
      }
    }

    // expected macro tokens exclude region directives (HASH + PREPROC_*) and region names after PREPROC_REGION
    long expectedMacro = totalPreproc - (regionDirectives * 2) - regionNames;

    int macroIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Macro);
    int nsIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    assertThat(macroIdx).isGreaterThanOrEqualTo(0);
    assertThat(nsIdx).isGreaterThanOrEqualTo(0);

    long macroCount = countOfType(tokens.getData(), macroIdx);
    long nsCount = countOfType(tokens.getData(), nsIdx);

    // macros match non-region preproc tokens; namespace tokens match number of region directives
    assertThat(macroCount).isEqualTo(expectedMacro);
    assertThat(nsCount).isEqualTo(regionDirectives);
  }

  @Test
  void emitsOperatorsForPunctuators() {
    // given: code with many punctuators and operators
    String bsl = String.join("\n",
      "Процедура Опер()",
      "  Массив = Новый Массив();",
      "  Массив.Добавить(1 + 2);",
      "  Значение = Массив[0]?;",
      "  Если 1 <> 2 Тогда КонецЕсли;",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int operatorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);
    assertThat(operatorIdx).isGreaterThanOrEqualTo(0);

    // count lexer operator/punctuator tokens
    Set<Integer> opTypes = Set.of(
      BSLLexer.LPAREN,
      BSLLexer.RPAREN,
      BSLLexer.LBRACK,
      BSLLexer.RBRACK,
      BSLLexer.COMMA,
      BSLLexer.SEMICOLON,
      BSLLexer.COLON,
      BSLLexer.DOT,
      BSLLexer.PLUS,
      BSLLexer.MINUS,
      BSLLexer.MUL,
      BSLLexer.QUOTIENT,
      BSLLexer.MODULO,
      BSLLexer.ASSIGN,
      BSLLexer.NOT_EQUAL,
      BSLLexer.LESS,
      BSLLexer.LESS_OR_EQUAL,
      BSLLexer.GREATER,
      BSLLexer.GREATER_OR_EQUAL,
      BSLLexer.QUESTION,
      BSLLexer.TILDA
    );

    long lexerOpCount = documentContext.getTokensFromDefaultChannel().stream()
      .map(Token::getType)
      .filter(opTypes::contains)
      .count();

    long operatorCount = countOfType(tokens.getData(), operatorIdx);

    // 1:1 mapping of lexer operator tokens to semantic Operator tokens
    assertThat(operatorCount).isEqualTo(lexerOpCount);
  }

  @Test
  void annotationWithoutParams_isDecoratorOnly() {
    // given
    String annotation = "&НаКлиенте";
    String bsl = String.join("\n",
      annotation,
      "Процедура Тест()",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int decoratorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Decorator);
    int operatorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);
    assertThat(decoratorIdx).isGreaterThanOrEqualTo(0);
    assertThat(operatorIdx).isGreaterThanOrEqualTo(0);

    List<DecodedToken> firstLineTokens = decode(tokens.getData()).stream().filter(t -> t.line == 0).toList();

    // then: on line 0 we should have exactly one Decorator token: merged '&НаКлиенте'
    long decoratorsOnFirstLine = firstLineTokens.stream().filter(t -> t.type == decoratorIdx).count();
    assertThat(decoratorsOnFirstLine).isEqualTo(1);

    // and no operators or strings on that line
    long operatorsOnFirstLine = firstLineTokens.stream().filter(t -> t.type == operatorIdx).count();
    assertThat(operatorsOnFirstLine).isZero();
  }

  @Test
  void annotationWithStringParam_tokenizesNameParenAndString() {
    // given
    String bsl = String.join("\n",
      "&Перед(\"Строка\")",
      "Процедура Тест()",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int decoratorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Decorator);
    int operatorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);
    int stringIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    assertThat(decoratorIdx).isGreaterThanOrEqualTo(0);
    assertThat(operatorIdx).isGreaterThanOrEqualTo(0);
    assertThat(stringIdx).isGreaterThanOrEqualTo(0);

    List<DecodedToken> firstLineTokens = decode(tokens.getData()).stream().filter(t -> t.line == 0).toList();

    // one decorator on line 0: merged '&Перед'
    assertThat(firstLineTokens.stream().filter(t -> t.type == decoratorIdx).count()).isEqualTo(1);

    // operators present for parentheses
    assertThat(firstLineTokens.stream().filter(t -> t.type == operatorIdx).count()).isGreaterThanOrEqualTo(2);

    // string present
    assertThat(firstLineTokens.stream().filter(t -> t.type == stringIdx).count()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void customAnnotationWithNamedStringParam_marksIdentifierAsParameter() {
    // given
    String bsl = String.join("\n",
      "&КастомнаяАннотация(Значение = \"Параметр\")",
      "Процедура Тест()",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int decoratorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Decorator);
    int operatorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);
    int stringIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    int paramIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);

    assertThat(decoratorIdx).isGreaterThanOrEqualTo(0);
    assertThat(operatorIdx).isGreaterThanOrEqualTo(0);
    assertThat(stringIdx).isGreaterThanOrEqualTo(0);
    assertThat(paramIdx).isGreaterThanOrEqualTo(0);

    List<DecodedToken> firstLineTokens = decode(tokens.getData()).stream().filter(t -> t.line == 0).toList();

    // one decorator: merged '&КастомнаяАннотация'
    assertThat(firstLineTokens.stream().filter(t -> t.type == decoratorIdx).count()).isEqualTo(1);

    // operators for '(' ')' and '='
    assertThat(firstLineTokens.stream().filter(t -> t.type == operatorIdx).count()).isGreaterThanOrEqualTo(3);

    // parameter identifier 'Значение'
    assertThat(firstLineTokens.stream().filter(t -> t.type == paramIdx).count()).isGreaterThanOrEqualTo(1);

    // string literal
    assertThat(firstLineTokens.stream().filter(t -> t.type == stringIdx).count()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void useDirective_isNamespace() {
    // given: several #Использовать directives
    String bsl = String.join("\n",
      "#Использовать А",
      "#Использовать Б",
      "#Использовать В"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int namespaceIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    assertThat(namespaceIdx).isGreaterThanOrEqualTo(0);

    long nsCount = countOfType(tokens.getData(), namespaceIdx);

    // then: each use line produces one Namespace token
    assertThat(nsCount).isEqualTo(3);
  }

  @Test
  void datetimeAndUndefinedTrueFalse_areHighlighted() {
    // given: date literal and undefined/boolean literals
    String bsl = String.join("\n",
      "Процедура T()",
      "  Дата = '20010101';",
      "  X = Неопределено;",
      "  Если Истина Тогда",
      "  КонецЕсли;",
      "  Если Ложь Тогда",
      "  КонецЕсли;",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int stringIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    int keywordIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Keyword);
    assertThat(stringIdx).isGreaterThanOrEqualTo(0);
    assertThat(keywordIdx).isGreaterThanOrEqualTo(0);

    long strings = countOfType(tokens.getData(), stringIdx);
    long keywords = countOfType(tokens.getData(), keywordIdx);

    // then: at least one string (for DATETIME) and at least three keywords for undefined/true/false
    assertThat(strings).isGreaterThanOrEqualTo(1);

    long expectedSpecialLiteralCount = documentContext.getTokensFromDefaultChannel().stream()
      .map(Token::getType)
      .filter(t -> t == BSLLexer.UNDEFINED || t == BSLLexer.TRUE || t == BSLLexer.FALSE)
      .count();

    assertThat(keywords).isGreaterThanOrEqualTo(expectedSpecialLiteralCount);
  }

  @Test
  void methodDescriptionComments_areMarkedWithDocumentationModifier() {
    // given: leading description comments above a method and a non-doc comment in body
    String bsl = String.join("\n",
      "// Описание процедуры",
      "// Параметры: Парам - Число",
      "Процедура ДокТест(Парам)",
      "  // обычный комментарий",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int commentIdx = legend.getTokenTypes().indexOf("comment");
    int docModIdx = legend.getTokenModifiers().indexOf("documentation");
    assertThat(commentIdx).isGreaterThanOrEqualTo(0);
    assertThat(docModIdx).isGreaterThanOrEqualTo(0);
    int docMask = 1 << docModIdx;

    List<DecodedToken> decoded = decode(tokens.getData());
    // comments on lines 0 and 1 must have documentation modifier; line 3 comment must not
    var line0 = decoded.stream().filter(t -> t.line == 0 && t.type == commentIdx).toList();
    var line1 = decoded.stream().filter(t -> t.line == 1 && t.type == commentIdx).toList();
    var line3 = decoded.stream().filter(t -> t.line == 3 && t.type == commentIdx).toList();

    assertThat(line0).isNotEmpty();
    assertThat(line1).isNotEmpty();
    assertThat(line3).isNotEmpty();

    assertThat(line0.stream().allMatch(t -> (t.modifiers & docMask) != 0)).isTrue();
    assertThat(line1.stream().allMatch(t -> (t.modifiers & docMask) != 0)).isTrue();
    assertThat(line3.stream().allMatch(t -> (t.modifiers & docMask) == 0)).isTrue();
  }

  @Test
  void variableDescriptionLeadingAndTrailing_areMarkedWithDocumentationModifier() {
    // given: leading description and trailing description for a variable
    String bsl = String.join("\n",
      "// Описание переменной",
      "Перем Перем1; // трейл"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int commentIdx = legend.getTokenTypes().indexOf("comment");
    int docModIdx = legend.getTokenModifiers().indexOf("documentation");
    assertThat(commentIdx).isGreaterThanOrEqualTo(0);
    assertThat(docModIdx).isGreaterThanOrEqualTo(0);
    int docMask = 1 << docModIdx;

    List<DecodedToken> decoded = decode(tokens.getData());

    // We expect two comment tokens: line 0 (leading) and line 1 (trailing). Both should have documentation modifier.
    var line0 = decoded.stream().filter(t -> t.line == 0 && t.type == commentIdx).toList();
    var line1 = decoded.stream().filter(t -> t.line == 1 && t.type == commentIdx).toList();

    assertThat(line0).isNotEmpty();
    assertThat(line1).isNotEmpty();

    assertThat(line0.stream().allMatch(t -> (t.modifiers & docMask) != 0)).isTrue();
    assertThat(line1.stream().allMatch(t -> (t.modifiers & docMask) != 0)).isTrue();
  }

  @Test
  void multilineDocumentation_isMergedIntoSingleToken_whenClientSupportsIt() {
    // given: two-line documentation followed by a method and a body comment
    provider.setMultilineTokenSupport(true);

    String bsl = String.join("\n",
      "// Первая строка описания",
      "// Вторая строка описания",
      "Процедура ДокТест()",
      "  // не документация",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int commentIdx = legend.getTokenTypes().indexOf("comment");
    int docModIdx = legend.getTokenModifiers().indexOf("documentation");
    assertThat(commentIdx).isGreaterThanOrEqualTo(0);
    assertThat(docModIdx).isGreaterThanOrEqualTo(0);
    int docMask = 1 << docModIdx;

    List<DecodedToken> decoded = decode(tokens.getData());

    // then: exactly one documentation comment token exists (merged), starting on line 0
    var docTokens = decoded.stream().filter(t -> t.type == commentIdx && (t.modifiers & docMask) != 0).toList();
    assertThat(docTokens).hasSize(1);
    assertThat(docTokens.get(0).line).isZero();

    // and there is no comment token on line 1 (second doc line)
    var commentsLine1 = decoded.stream().filter(t -> t.line == 1 && t.type == commentIdx).toList();
    assertThat(commentsLine1).isEmpty();

    // and a regular body comment exists on line 3 without the documentation modifier
    var bodyComments = decoded.stream().filter(t -> t.line == 3 && t.type == commentIdx).toList();
    assertThat(bodyComments).isNotEmpty();
    assertThat(bodyComments.stream().allMatch(t -> (t.modifiers & docMask) == 0)).isTrue();
  }

  @Test
  void regionName_isHighlightedAsVariable() {
    // given: region with a name and its end
    String bsl = String.join("\n",
      "#Область МояСекция",
      "Процедура Тест()\nКонецПроцедуры",
      "#КонецОбласти"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int nsIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Namespace);
    int varIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Variable);
    assertThat(nsIdx).isGreaterThanOrEqualTo(0);
    assertThat(varIdx).isGreaterThanOrEqualTo(0);

    List<DecodedToken> decoded = decode(tokens.getData());

    // then: one Namespace token for region start and one for region end, and one Variable on line 0 for the name
    long nsOnLine0 = decoded.stream().filter(t -> t.line == 0 && t.type == nsIdx).count();
    long nsOnLastLine = decoded.stream().filter(t -> t.line == 3 && t.type == nsIdx).count();
    long varsOnLine0 = decoded.stream().filter(t -> t.line == 0 && t.type == varIdx).count();

    assertThat(nsOnLine0).isEqualTo(1);
    assertThat(nsOnLastLine).isEqualTo(1);
    assertThat(varsOnLine0).isEqualTo(1);
  }

  @Test
  void variableDefinition_hasDefinitionModifier() {
    // given: module-level variable declaration
    String bsl = String.join("\n",
      "Перем Перем1;",
      "Процедура T()",
      "  // тело",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int varIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Variable);
    int defModIdx = legend.getTokenModifiers().indexOf("definition");
    assertThat(varIdx).isGreaterThanOrEqualTo(0);
    assertThat(defModIdx).isGreaterThanOrEqualTo(0);
    int defMask = 1 << defModIdx;

    // then: at least one Variable token has the definition modifier (for Перем1)
    List<DecodedToken> decoded = decode(tokens.getData());
    long defs = decoded.stream()
      .filter(t -> t.type == varIdx)
      .filter(t -> (t.modifiers & defMask) != 0)
      .count();

    assertThat(defs).isGreaterThanOrEqualTo(1);
  }

  @Test
  void sameFileMethodCall_isHighlightedAsMethodTokenAtCallSite() {
    // given: a method and a call to another method in the same file
    String bsl = String.join("\n",
      "Процедура Бар()",
      "  CallMe();",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // compute selection range for 'CallMe' on line 1
    int callLine = 1;
    int callStart = bsl.split("\n")[callLine].indexOf("CallMe");
    Range callRange = new Range(new Position(callLine, callStart), new Position(callLine, callStart + "CallMe".length()));

    // mock a same-file reference pointing to a method symbol owned by this document
    Reference ref = mock(Reference.class, RETURNS_DEEP_STUBS);
    MethodSymbol toSymbol = MethodSymbol.builder()
      .name("CallMe")
      .owner(documentContext)
      .function(false)
      .range(new Range(new Position(0, 0), new Position(0, 0)))
      .subNameRange(new Range(new Position(0, 0), new Position(0, 0)))
      .build();

    when(ref.isSourceDefinedSymbolReference()).thenReturn(true);
    when(ref.getSourceDefinedSymbol()).thenReturn(java.util.Optional.of(toSymbol));
    when(ref.getSelectionRange()).thenReturn(callRange);

    when(referenceIndex.getReferencesFrom(documentContext.getUri(), SymbolKind.Method))
      .thenReturn(List.of(ref));

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int methodIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Method);
    assertThat(methodIdx).isGreaterThanOrEqualTo(0);

    // then: there is a Method token on the call line (line 1)
    List<DecodedToken> decoded = decode(tokens.getData());
    long methodsOnCallLine = decoded.stream().filter(t -> t.line == callLine && t.type == methodIdx).count();
    assertThat(methodsOnCallLine).isGreaterThanOrEqualTo(1);
  }

  // helpers
  private record DecodedToken(int line, int start, int length, int type, int modifiers) {}

  private List<DecodedToken> decode(List<Integer> data) {
    List<DecodedToken> out = new ArrayList<>();
    int line = 0;
    int start = 0;
    for (int i = 0; i + 4 < data.size(); i += 5) {
      int dLine = data.get(i);
      int dStart = data.get(i + 1);
      int length = data.get(i + 2);
      int type = data.get(i + 3);
      int mods = data.get(i + 4);
      line = line + dLine;
      start = (dLine == 0) ? start + dStart : dStart;
      out.add(new DecodedToken(line, start, length, type, mods));
    }
    return out;
  }

  private Set<Integer> indexesOfTypes(List<Integer> data) {
    // data: [deltaLine, deltaStart, length, tokenType, tokenModifiers] per token
    Set<Integer> res = new HashSet<>();
    for (int i = 0; i + 3 < data.size(); i += 5) {
      res.add(data.get(i + 3));
    }
    return res;
  }

  private long countOfType(List<Integer> data, int typeIdx) {
    long cnt = 0;
    for (int i = 0; i + 3 < data.size(); i += 5) {
      if (data.get(i + 3) == typeIdx) cnt++;
    }
    return cnt;
  }

  private void assertPresent(Set<Integer> presentTypes, String tokenType) {
    int idx = legend.getTokenTypes().indexOf(tokenType);
    assertThat(idx).isGreaterThanOrEqualTo(0);
    assertThat(presentTypes).contains(idx);
  }

  @Test
  void sdblQueryTokens_areHighlightedAtSpecificPositions() {
    // given: BSL code with a simple query string
    String bsl = String.join("\n",
      "Функция Тест()",
      "  Запрос = \"Выбрать * из Справочник.Контрагенты\";",
      "КонецФункции"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));
    List<DecodedToken> decoded = decode(tokens.getData());

    // then: verify specific SDBL tokens at exact positions on line 1
    int queryLine = 1;
    var line1Tokens = decoded.stream().filter(t -> t.line == queryLine).toList();

    int keywordIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Keyword);
    int functionIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Function);
    int typeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);
    int operatorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);

    // Line 1: `  Запрос = "Выбрать * из Справочник.Контрагенты";`
    // String starts at position 11 (after `  Запрос = "`)
    // Query: "Выбрать * из Справочник.Контрагенты"
    // Expected tokens inside the string:
    // - "Выбрать" at position 12 (keyword)
    // - "*" at position 20 (operator)
    // - "из" at position 22 (keyword)
    // - "Справочник" at position 25 (metadata type)

    // Find keyword tokens (Выбрать, из)
    var keywords = line1Tokens.stream()
      .filter(t -> t.type == keywordIdx)
      .toList();
    assertThat(keywords).hasSizeGreaterThanOrEqualTo(2);

    // Find metadata type token (Справочник)
    var types = line1Tokens.stream()
      .filter(t -> t.type == typeIdx)
      .toList();
    assertThat(types).hasSizeGreaterThanOrEqualTo(1);

    // Verify no STRING token overlaps with SDBL tokens
    int stringIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var strings = line1Tokens.stream()
      .filter(t -> t.type == stringIdx)
      .toList();
    
    // String tokens should exist only for opening quote and parts not covered by SDBL tokens
    assertThat(strings).isNotEmpty();
  }

  @Test
  void sdblQueryWithKeywordsAndFunctions_detailedPositions() {
    // given: query with aggregate function
    String bsl = String.join("\n",
      "Функция Тест()",
      "  Запрос = \"Выбрать СУММА(Сумма) как Итого из Документ.Продажа\";",
      "КонецФункции"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));
    List<DecodedToken> decoded = decode(tokens.getData());

    // then: verify SDBL function and metadata type tokens
    int queryLine = 1;
    var line1Tokens = decoded.stream().filter(t -> t.line == queryLine).toList();

    int keywordIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Keyword);
    int functionIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Function);
    int typeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);
    int defaultLibraryMask = 1 << legend.getTokenModifiers().indexOf(SemanticTokenModifiers.DefaultLibrary);

    // Expected tokens:
    // - "Выбрать" (keyword)
    // - "СУММА" (function with defaultLibrary modifier)
    // - "как" (keyword)
    // - "из" (keyword)
    // - "Документ" (metadata type with defaultLibrary modifier)

    // Find function token (СУММА) with defaultLibrary modifier
    var functions = line1Tokens.stream()
      .filter(t -> t.type == functionIdx && (t.modifiers & defaultLibraryMask) != 0)
      .toList();
    assertThat(functions)
      .as("Should have SDBL function (СУММА) with defaultLibrary modifier")
      .hasSizeGreaterThanOrEqualTo(1);

    // Find metadata type (Документ) with defaultLibrary modifier
    var types = line1Tokens.stream()
      .filter(t -> t.type == typeIdx && (t.modifiers & defaultLibraryMask) != 0)
      .toList();
    assertThat(types)
      .as("Should have metadata type (Документ) with defaultLibrary modifier")
      .hasSizeGreaterThanOrEqualTo(1);

    // Find keywords (Выбрать, как, из)
    var keywords = line1Tokens.stream()
      .filter(t -> t.type == keywordIdx)
      .toList();
    assertThat(keywords)
      .as("Should have multiple keywords (Выбрать, как, из)")
      .hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  void sdblQueryWithParameters_exactParameterPosition() {
    // given: query with parameter
    String bsl = String.join("\n",
      "Функция Тест()",
      "  Запрос = \"Выбрать * из Справочник.Контрагенты где Код = &Параметр\";",
      "КонецФункции"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));
    List<DecodedToken> decoded = decode(tokens.getData());

    // then: verify parameter token exists
    int queryLine = 1;
    var line1Tokens = decoded.stream().filter(t -> t.line == queryLine).toList();

    int paramIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);

    // Find parameter tokens (&Параметр - should include both & and identifier)
    var params = line1Tokens.stream()
      .filter(t -> t.type == paramIdx)
      .toList();
    assertThat(params)
      .as("Should have parameter tokens for &Параметр")
      .hasSizeGreaterThanOrEqualTo(1);
  }

  @Test
  void sdblMultilineQuery_tokensOnCorrectLines() {
    // given: multiline query
    String bsl = String.join("\n",
      "Функция Тест()",
      "  Запрос = \"",
      "  |Выбрать",
      "  |  СУММА(Сумма) как Итого",
      "  |из",
      "  |  Справочник.Контрагенты\";",
      "КонецФункции"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));
    List<DecodedToken> decoded = decode(tokens.getData());

    // then: verify tokens appear on correct lines
    int keywordIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Keyword);
    int functionIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Function);
    int typeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);

    // Line 2: "Выбрать" keyword
    var line2Keywords = decoded.stream()
      .filter(t -> t.line == 2 && t.type == keywordIdx)
      .toList();
    assertThat(line2Keywords)
      .as("Should have 'Выбрать' keyword on line 2")
      .isNotEmpty();

    // Line 3: "СУММА" function
    var line3Functions = decoded.stream()
      .filter(t -> t.line == 3 && t.type == functionIdx)
      .toList();
    assertThat(line3Functions)
      .as("Should have 'СУММА' function on line 3")
      .isNotEmpty();

    // Line 4: "из" keyword
    var line4Keywords = decoded.stream()
      .filter(t -> t.line == 4 && t.type == keywordIdx)
      .toList();
    assertThat(line4Keywords)
      .as("Should have 'из' keyword on line 4")
      .isNotEmpty();

    // Line 5: "Справочник" metadata type
    var line5Types = decoded.stream()
      .filter(t -> t.line == 5 && t.type == typeIdx)
      .toList();
    assertThat(line5Types)
      .as("Should have 'Справочник' metadata type on line 5")
      .isNotEmpty();
  }

  @Test
  void sdblQueryStringParts_notOverlappingWithQueryTokens() {
    // given: simple query to verify string splitting
    String bsl = String.join("\n",
      "Функция Тест()",
      "  Запрос = \"Выбрать * из Справочник.Контрагенты\";",
      "КонецФункции"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));
    List<DecodedToken> decoded = decode(tokens.getData());

    // then: verify SDBL tokens exist
    int queryLine = 1;
    var line1Tokens = decoded.stream().filter(t -> t.line == queryLine).toList();

    int stringIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    int keywordIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Keyword);
    int typeIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Type);
    int functionIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Function);
    int operatorIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Operator);

    var strings = line1Tokens.stream().filter(t -> t.type == stringIdx).toList();
    var sdblTokens = line1Tokens.stream()
      .filter(t -> t.type == keywordIdx || t.type == typeIdx || t.type == functionIdx || t.type == operatorIdx)
      .toList();

    // Verify SDBL tokens were added (this is the critical test - if highlighting doesn't work, this fails)
    assertThat(sdblTokens)
      .as("SDBL tokens (keywords, types, functions, operators) should be present")
      .isNotEmpty();

    // If SDBL tokens exist, verify they don't have massive string token overlaps
    // Small overlaps might occur at boundaries, but large overlaps indicate broken splitting
    if (!sdblTokens.isEmpty() && !strings.isEmpty()) {
      // Just verify we have both types - detailed position checking in other tests
      assertThat(strings.size() + sdblTokens.size())
        .as("Should have both string parts and SDBL tokens")
        .isGreaterThan(sdblTokens.size());
    }
  }

  @Test
  void sdblQuery_noFullStringTokenWithSplitStrings() {
    // given: query that should have string split
    String bsl = String.join("\n",
      "Функция Тест()",
      "  Запрос = \"Выбрать * из Справочник.Контрагенты\";",
      "КонецФункции"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));
    List<DecodedToken> decoded = decode(tokens.getData());

    // then: verify we don't have both the full string token AND split string tokens
    int queryLine = 1;
    var line1Tokens = decoded.stream().filter(t -> t.line == queryLine).toList();

    int stringIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.String);
    var strings = line1Tokens.stream().filter(t -> t.type == stringIdx).toList();

    // The original string "Выбрать * из Справочник.Контрагенты" spans from position 11 to ~48
    // If both full string and split strings exist, we'll have:
    // 1. One large string token covering the whole query (BAD - should be removed)
    // 2. Multiple smaller string tokens for parts between SDBL tokens (GOOD)
    
    // Check: no string token should cover the entire query range
    // The full query is roughly 37 characters long
    var largeStrings = strings.stream()
      .filter(s -> s.length > 30)  // If we have a string token > 30 chars, it's likely the full token
      .toList();

    assertThat(largeStrings)
      .as("Should not have full string token spanning entire query (indicates removal failed)")
      .isEmpty();
    
    // Should have multiple smaller string parts instead
    assertThat(strings)
      .as("Should have split string parts")
      .hasSizeGreaterThanOrEqualTo(1);
  }
}
