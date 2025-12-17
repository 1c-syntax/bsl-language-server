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
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndexFiller;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class SemanticTokensProviderTest {

  @Autowired
  private SemanticTokensProvider provider;

  @Autowired
  private SemanticTokensLegend legend;

  @Autowired
  private ReferenceIndexFiller referenceIndexFiller;

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
    referenceIndexFiller.fill(documentContext);
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
    referenceIndexFiller.fill(documentContext);
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
      "Процедура CallMe()",
      "КонецПроцедуры",
      "",
      "Процедура Бар()",
      "  CallMe();",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // compute selection range for 'CallMe' on line 4
    int callLine = 4;

    // when
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int methodIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Method);
    assertThat(methodIdx).isGreaterThanOrEqualTo(0);

    // then: there is a Method token on the call line (line 4)
    List<DecodedToken> decoded = decode(tokens.getData());
    long methodsOnCallLine = decoded.stream().filter(t -> t.line == callLine && t.type == methodIdx).count();
    assertThat(methodsOnCallLine).isGreaterThanOrEqualTo(1);
  }

  @Test
  void parameterAndVariableTokenTypes() {
    String bsl = String.join("\n",
      "Процедура Тест(Парам1, Парам2)",
      "  Перем ЛокальнаяПеременная;",
      "  НеявнаяПеременная = 1;",
      "  ЛокальнаяПеременная2 = 2;",
      "  Результат = 3;",
      "  Для ПеременнаяЦикла = 1 По 10 Цикл",
      "  КонецЦикла;",
      "КонецПроцедуры"
    );

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int paramIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    int varIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Variable);
    assertThat(paramIdx).isGreaterThanOrEqualTo(0);
    assertThat(varIdx).isGreaterThanOrEqualTo(0);

    List<DecodedToken> decoded = decode(tokens.getData());

    long paramsInSignature = decoded.stream()
      .filter(t -> t.line == 0 && t.type == paramIdx)
      .count();
    assertThat(paramsInSignature).as("Parameters in signature").isEqualTo(2);

    long localVarDeclaration = decoded.stream()
      .filter(t -> t.line == 1 && t.type == varIdx)
      .count();
    assertThat(localVarDeclaration).as("Explicit variable declaration").isEqualTo(1);

    long implicitVarDeclaration1 = decoded.stream()
      .filter(t -> t.line == 2 && t.type == varIdx)
      .count();
    assertThat(implicitVarDeclaration1).as("First implicit variable declaration").isEqualTo(1);

    long implicitVarDeclaration2 = decoded.stream()
      .filter(t -> t.line == 3 && t.type == varIdx)
      .count();
    assertThat(implicitVarDeclaration2).as("Second implicit variable declaration").isEqualTo(1);

    long implicitVarDeclaration3 = decoded.stream()
      .filter(t -> t.line == 4 && t.type == varIdx)
      .count();
    assertThat(implicitVarDeclaration3).as("Third implicit variable declaration").isEqualTo(1);

    long forLoopVar = decoded.stream()
      .filter(t -> t.line == 5 && t.type == varIdx)
      .count();
    assertThat(forLoopVar).as("For loop variable").isEqualTo(1);

    long allParams = decoded.stream()
      .filter(t -> t.type == paramIdx)
      .count();
    assertThat(allParams).as("Total parameters").isEqualTo(2);

    long allVars = decoded.stream()
      .filter(t -> t.type == varIdx)
      .count();
    assertThat(allVars).as("Total variables").isEqualTo(5);
  }

  @Test
  void parameterAndVariableUsages() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/SemanticTokensProviderParameterTest.bsl"
    );
    referenceIndexFiller.fill(documentContext);

    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int paramIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    int varIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Variable);
    assertThat(paramIdx).isGreaterThanOrEqualTo(0);
    assertThat(varIdx).isGreaterThanOrEqualTo(0);

    List<DecodedToken> decoded = decode(tokens.getData());

    long paramsLine0 = decoded.stream()
      .filter(t -> t.line == 0 && t.type == paramIdx)
      .count();
    assertThat(paramsLine0).as("Parameters in signature (line 0)").isEqualTo(2);

    long varsLine1 = decoded.stream()
      .filter(t -> t.line == 1 && t.type == varIdx)
      .count();
    assertThat(varsLine1).as("Local variable declaration (line 1)").isEqualTo(1);

    long varsLine3 = decoded.stream()
      .filter(t -> t.line == 3 && t.type == varIdx)
      .count();
    assertThat(varsLine3).as("Variable usage on left side (line 3)").isEqualTo(1);

    long paramsLine3 = decoded.stream()
      .filter(t -> t.line == 3 && t.type == paramIdx)
      .count();
    assertThat(paramsLine3).as("Parameter usage on right side (line 3)").isEqualTo(1);

    long varsLine4 = decoded.stream()
      .filter(t -> t.line == 4 && t.type == varIdx)
      .count();
    assertThat(varsLine4).as("Variable usage (line 4)").isEqualTo(1);

    long paramsLine4 = decoded.stream()
      .filter(t -> t.line == 4 && t.type == paramIdx)
      .count();
    assertThat(paramsLine4).as("Parameter usages (line 4)").isEqualTo(2);

    long paramsLine6 = decoded.stream()
      .filter(t -> t.line == 6 && t.type == paramIdx)
      .count();
    assertThat(paramsLine6).as("Parameter in condition (line 6)").isEqualTo(1);

    long paramsLine7 = decoded.stream()
      .filter(t -> t.line == 7 && t.type == paramIdx)
      .count();
    assertThat(paramsLine7).as("Parameter in Сообщить (line 7)").isEqualTo(1);

    long varsLine8 = decoded.stream()
      .filter(t -> t.line == 8 && t.type == varIdx)
      .count();
    assertThat(varsLine8).as("Variable assignment (line 8)").isEqualTo(1);

    long paramsLine8 = decoded.stream()
      .filter(t -> t.line == 8 && t.type == paramIdx)
      .count();
    assertThat(paramsLine8).as("Parameters in expression (line 8)").isEqualTo(2);

    long varsLine11 = decoded.stream()
      .filter(t -> t.line == 11 && t.type == varIdx)
      .count();
    assertThat(varsLine11).as("For loop variable (line 11)").isEqualTo(1);

    long paramsLine11 = decoded.stream()
      .filter(t -> t.line == 11 && t.type == paramIdx)
      .count();
    assertThat(paramsLine11).as("Parameter in loop bound (line 11)").isEqualTo(1);

    long varsLine12 = decoded.stream()
      .filter(t -> t.line == 12 && t.type == varIdx)
      .count();
    assertThat(varsLine12).as("Loop variable usage (line 12)").isEqualTo(1);

    long totalParams = decoded.stream()
      .filter(t -> t.type == paramIdx)
      .count();
    assertThat(totalParams).as("Total parameter tokens").isGreaterThanOrEqualTo(10);

    long totalVars = decoded.stream()
      .filter(t -> t.type == varIdx)
      .count();
    assertThat(totalVars).as("Total variable tokens").isGreaterThanOrEqualTo(6);
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
}
