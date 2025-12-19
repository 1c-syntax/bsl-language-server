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

import java.util.ArrayList;
import java.util.List;
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


  // region Helper types and methods

  /**
   * Represents expected semantic token for assertion.
   *
   * @param line           0-based line number
   * @param startChar      0-based start character
   * @param length         token length
   * @param tokenType      LSP token type (e.g., SemanticTokenTypes.Keyword)
   * @param tokenModifiers set of LSP modifiers (e.g., SemanticTokenModifiers.Declaration)
   * @param lexeme         optional lexeme for documentation (not used in comparison)
   */
  private record ExpectedToken(
    int line,
    int startChar,
    int length,
    String tokenType,
    Set<String> tokenModifiers,
    String lexeme
  ) {
    ExpectedToken(int line, int startChar, int length, String tokenType, String lexeme) {
      this(line, startChar, length, tokenType, Set.of(), lexeme);
    }

    ExpectedToken(int line, int startChar, int length, String tokenType, String modifier, String lexeme) {
      this(line, startChar, length, tokenType, Set.of(modifier), lexeme);
    }
  }

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

  private void assertTokensMatch(List<DecodedToken> actual, List<ExpectedToken> expected) {
    assertThat(actual)
      .as("Number of tokens")
      .hasSameSizeAs(expected);

    for (int i = 0; i < expected.size(); i++) {
      var exp = expected.get(i);
      var act = actual.get(i);

      int expectedTypeIdx = legend.getTokenTypes().indexOf(exp.tokenType);
      int expectedModifiersMask = computeModifiersMask(exp.tokenModifiers);

      assertThat(act.line)
        .as("Token %d (%s): line", i, exp.lexeme)
        .isEqualTo(exp.line);
      assertThat(act.start)
        .as("Token %d (%s): start", i, exp.lexeme)
        .isEqualTo(exp.startChar);
      assertThat(act.length)
        .as("Token %d (%s): length", i, exp.lexeme)
        .isEqualTo(exp.length);
      assertThat(act.type)
        .as("Token %d (%s): type (expected %s)", i, exp.lexeme, exp.tokenType)
        .isEqualTo(expectedTypeIdx);
      assertThat(act.modifiers)
        .as("Token %d (%s): modifiers", i, exp.lexeme)
        .isEqualTo(expectedModifiersMask);
    }
  }

  private void assertContainsTokens(List<DecodedToken> actual, List<ExpectedToken> expected) {
    for (var exp : expected) {
      int expectedTypeIdx = legend.getTokenTypes().indexOf(exp.tokenType);
      int expectedModifiersMask = computeModifiersMask(exp.tokenModifiers);

      var found = actual.stream()
        .filter(t -> t.line == exp.line
          && t.start == exp.startChar
          && t.length == exp.length
          && t.type == expectedTypeIdx
          && t.modifiers == expectedModifiersMask)
        .findFirst();

      assertThat(found)
        .as("Expected token: %s at [%d:%d], length=%d, type=%s, modifiers=%s",
          exp.lexeme, exp.line, exp.startChar, exp.length, exp.tokenType, exp.tokenModifiers)
        .isPresent();
    }
  }

  private int computeModifiersMask(Set<String> modifiers) {
    int mask = 0;
    for (String mod : modifiers) {
      int idx = legend.getTokenModifiers().indexOf(mod);
      if (idx >= 0) {
        mask |= (1 << idx);
      }
    }
    return mask;
  }

  private SemanticTokens getTokens(String bsl) {
    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    return provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));
  }

  private List<DecodedToken> getDecodedTokens(String bsl) {
    return decode(getTokens(bsl).getData());
  }

  // endregion

  // region Encoder test

  @Test
  void tokenEncodingFormat_deltaLineAndDeltaStart() {
    // Test that the encoder correctly computes delta-line and delta-start values
    // according to LSP SemanticTokens specification
    String bsl = """
      Перем А;
      Перем Б;
      """;

    SemanticTokens tokens = getTokens(bsl);
    List<Integer> data = tokens.getData();

    // Each token is 5 integers: [deltaLine, deltaStart, length, tokenType, tokenModifiers]
    assertThat(data.size() % 5).isZero();

    // Decode and verify absolute positions
    List<DecodedToken> decoded = decode(data);
    assertThat(decoded).isNotEmpty();

    // First token should be at line 0
    assertThat(decoded.get(0).line).isZero();

    // Tokens should be ordered by position
    for (int i = 1; i < decoded.size(); i++) {
      var prev = decoded.get(i - 1);
      var curr = decoded.get(i);
      // Either on a later line, or same line with later start
      assertThat(curr.line > prev.line || (curr.line == prev.line && curr.start >= prev.start + prev.length))
        .as("Token %d should be after token %d", i, i - 1)
        .isTrue();
    }
  }

  // endregion

  // region BSL tokens tests

  @Test
  void annotationWithoutParams() {
    String bsl = """
      &НаКлиенте
      Процедура Тест()
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      new ExpectedToken(0, 0, 10, SemanticTokenTypes.Decorator, "&НаКлиенте"),
      new ExpectedToken(1, 0, 9, SemanticTokenTypes.Keyword, "Процедура"),
      new ExpectedToken(1, 10, 4, SemanticTokenTypes.Method, "Тест"),
      new ExpectedToken(1, 14, 1, SemanticTokenTypes.Operator, "("),
      new ExpectedToken(1, 15, 1, SemanticTokenTypes.Operator, ")"),
      new ExpectedToken(2, 0, 14, SemanticTokenTypes.Keyword, "КонецПроцедуры")
    );

    assertTokensMatch(decoded, expected);
  }

  @Test
  void annotationWithStringParam() {
    String bsl = """
      &Перед("Строка")
      Процедура Тест()
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expectedLine0 = List.of(
      new ExpectedToken(0, 0, 6, SemanticTokenTypes.Decorator, "&Перед"),
      new ExpectedToken(0, 6, 1, SemanticTokenTypes.Operator, "("),
      new ExpectedToken(0, 7, 8, SemanticTokenTypes.String, "\"Строка\""),
      new ExpectedToken(0, 15, 1, SemanticTokenTypes.Operator, ")")
    );

    assertContainsTokens(decoded, expectedLine0);
  }

  @Test
  void annotationWithNamedParam() {
    String bsl = """
      &КастомнаяАннотация(Значение = "Параметр")
      Процедура Тест()
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expectedLine0 = List.of(
      new ExpectedToken(0, 0, 19, SemanticTokenTypes.Decorator, "&КастомнаяАннотация"),
      new ExpectedToken(0, 19, 1, SemanticTokenTypes.Operator, "("),
      new ExpectedToken(0, 20, 8, SemanticTokenTypes.Parameter, "Значение"),
      new ExpectedToken(0, 29, 1, SemanticTokenTypes.Operator, "="),
      new ExpectedToken(0, 31, 10, SemanticTokenTypes.String, "\"Параметр\""),
      new ExpectedToken(0, 41, 1, SemanticTokenTypes.Operator, ")")
    );

    assertContainsTokens(decoded, expectedLine0);
  }

  @Test
  void useDirective() {
    String bsl = """
      #Использовать А
      #Использовать Б
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      new ExpectedToken(0, 0, 13, SemanticTokenTypes.Namespace, "#Использовать"),
      new ExpectedToken(0, 14, 1, SemanticTokenTypes.Variable, "А"),
      new ExpectedToken(1, 0, 13, SemanticTokenTypes.Namespace, "#Использовать"),
      new ExpectedToken(1, 14, 1, SemanticTokenTypes.Variable, "Б")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void regionDirective() {
    String bsl = """
      #Область МояСекция
      Процедура Тест()
      КонецПроцедуры
      #КонецОбласти
      """;

    var decoded = getDecodedTokens(bsl);

    // Verify region tokens
    var expectedTokens = List.of(
      new ExpectedToken(0, 0, 8, SemanticTokenTypes.Namespace, "#Область"),
      new ExpectedToken(0, 9, 9, SemanticTokenTypes.Variable, "МояСекция"),
      new ExpectedToken(3, 0, 13, SemanticTokenTypes.Namespace, "#КонецОбласти")
    );

    assertContainsTokens(decoded, expectedTokens);
  }

  @Test
  void preprocessorDirectives() {
    String bsl = """
      #Если Сервер Тогда
      Процедура Пусто()
      КонецПроцедуры
      #ИначеЕсли Клиент Тогда
      #Иначе
      #КонецЕсли
      """;

    var decoded = getDecodedTokens(bsl);

    // Verify preprocessor macro tokens on specific lines
    var expectedTokens = List.of(
      new ExpectedToken(0, 0, 1, SemanticTokenTypes.Macro, "#"),
      new ExpectedToken(0, 1, 4, SemanticTokenTypes.Macro, "Если"),
      new ExpectedToken(0, 6, 6, SemanticTokenTypes.Macro, "Сервер"),
      new ExpectedToken(0, 13, 5, SemanticTokenTypes.Macro, "Тогда"),
      new ExpectedToken(3, 0, 1, SemanticTokenTypes.Macro, "#"),
      new ExpectedToken(3, 1, 9, SemanticTokenTypes.Macro, "ИначеЕсли"),
      new ExpectedToken(4, 0, 1, SemanticTokenTypes.Macro, "#"),
      new ExpectedToken(4, 1, 5, SemanticTokenTypes.Macro, "Иначе"),
      new ExpectedToken(5, 0, 1, SemanticTokenTypes.Macro, "#"),
      new ExpectedToken(5, 1, 9, SemanticTokenTypes.Macro, "КонецЕсли")
    );

    assertContainsTokens(decoded, expectedTokens);
  }

  @Test
  void literals() {
    String bsl = """
      Процедура Тест()
        Дата = '20010101';
        X = Неопределено;
        Y = Истина;
        Z = Ложь;
        N = 123;
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expectedTokens = List.of(
      new ExpectedToken(1, 9, 10, SemanticTokenTypes.String, "'20010101'"),
      new ExpectedToken(2, 6, 12, SemanticTokenTypes.Keyword, "Неопределено"),
      new ExpectedToken(3, 6, 6, SemanticTokenTypes.Keyword, "Истина"),
      new ExpectedToken(4, 6, 4, SemanticTokenTypes.Keyword, "Ложь"),
      new ExpectedToken(5, 6, 3, SemanticTokenTypes.Number, "123")
    );

    assertContainsTokens(decoded, expectedTokens);
  }

  @Test
  void methodDescriptionComments() {
    String bsl = """
      // Описание процедуры
      // Параметры: Парам - Число
      Процедура ДокТест(Парам)
        // обычный комментарий
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    // Documentation comments on lines 0-1 should have Documentation modifier
    // Body comment on line 3 should NOT have Documentation modifier
    var expected = List.of(
      new ExpectedToken(0, 0, 21, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// Описание процедуры"),
      new ExpectedToken(1, 0, 27, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// Параметры: Парам - Число"),
      new ExpectedToken(3, 2, 22, SemanticTokenTypes.Comment, "// обычный комментарий")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void variableDescriptionComments() {
    String bsl = """
      // Описание переменной
      Перем Перем1; // трейл
      """;

    var decoded = getDecodedTokens(bsl);

    // Both leading (line 0) and trailing (line 1) comments should have documentation modifier
    var expected = List.of(
      new ExpectedToken(0, 0, 22, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// Описание переменной"),
      new ExpectedToken(1, 14, 8, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// трейл")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void multilineDocumentation_mergedWhenSupported() {
    provider.setMultilineTokenSupport(true);

    String bsl = """
      // Первая строка описания
      // Вторая строка описания
      Процедура ДокТест()
        // не документация
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    // When multiline support is enabled, documentation comments should be merged into one token
    // The merged token starts on line 0 and spans across lines
    // Both lines "// Первая строка описания" (26 chars) + "// Вторая строка описания" (25 chars) = 51 chars total
    // Body comment on line 3 should NOT have Documentation modifier
    var expected = List.of(
      // Merged documentation comment (starts at line 0, length is sum of both lines)
      new ExpectedToken(0, 0, 51, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// Первая+Вторая строка описания"),
      // Body comment without documentation modifier
      new ExpectedToken(3, 2, 18, SemanticTokenTypes.Comment, "// не документация")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void variableDefinition_hasDefinitionModifier() {
    String bsl = """
      Перем Перем1;
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      new ExpectedToken(0, 0, 5, SemanticTokenTypes.Keyword, "Перем"),
      new ExpectedToken(0, 6, 6, SemanticTokenTypes.Variable, SemanticTokenModifiers.Definition, "Перем1"),
      new ExpectedToken(0, 12, 1, SemanticTokenTypes.Operator, ";")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void parameterAndVariableTokenTypes() {
    String bsl = """
      Процедура Тест(Парам1, Парам2)
        Перем ЛокальнаяПеременная;
        НеявнаяПеременная = 1;
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expectedTokens = List.of(
      // Parameters in signature
      new ExpectedToken(0, 15, 6, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Definition, "Парам1"),
      new ExpectedToken(0, 23, 6, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Definition, "Парам2"),
      // Explicit variable declaration
      new ExpectedToken(1, 8, 19, SemanticTokenTypes.Variable, SemanticTokenModifiers.Definition, "ЛокальнаяПеременная"),
      // Implicit variable
      new ExpectedToken(2, 2, 17, SemanticTokenTypes.Variable, SemanticTokenModifiers.Definition, "НеявнаяПеременная")
    );

    assertContainsTokens(decoded, expectedTokens);
  }

  @Test
  void sameFileMethodCall() {
    String bsl = """
      Процедура CallMe()
      КонецПроцедуры

      Процедура Бар()
        CallMe();
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    // Method call on line 4
    var methodCallToken = new ExpectedToken(4, 2, 6, SemanticTokenTypes.Method, "CallMe");
    assertContainsTokens(decoded, List.of(methodCallToken));
  }

  @Test
  void parameterAndVariableUsages() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/SemanticTokensProviderParameterTest.bsl"
    );
    referenceIndexFiller.fill(documentContext);

    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));
    var decoded = decode(tokens.getData());

    var expected = List.of(
      // Parameters in signature (line 0)
      new ExpectedToken(0, 15, 6, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Definition, "Парам1"),
      new ExpectedToken(0, 23, 6, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Definition, "Парам2"),
      // Local variable declaration (line 1)
      new ExpectedToken(1, 8, 19, SemanticTokenTypes.Variable, SemanticTokenModifiers.Definition, "ЛокальнаяПеременная"),
      // Variable usage on line 4 (without definition modifier)
      new ExpectedToken(4, 11, 19, SemanticTokenTypes.Variable, "ЛокальнаяПеременная"),
      // Parameter usage on line 3 (without definition modifier)
      new ExpectedToken(3, 24, 6, SemanticTokenTypes.Parameter, "Парам1")
    );

    assertContainsTokens(decoded, expected);
  }

  // endregion

  // region SDBL tokens tests

  @Test
  void sdblQuery_simpleSelect() {
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * из Справочник.Контрагенты";
      КонецФункции
      """;

    var decoded = getDecodedTokens(bsl);

    // Expected SDBL tokens on line 1
    var expectedTokens = List.of(
      // "Выбрать" keyword at position 12 (after `  Запрос = "`)
      new ExpectedToken(1, 12, 7, SemanticTokenTypes.Keyword, "Выбрать"),
      // "*" operator
      new ExpectedToken(1, 20, 1, SemanticTokenTypes.Operator, "*"),
      // "из" keyword
      new ExpectedToken(1, 22, 2, SemanticTokenTypes.Keyword, "из"),
      // "Справочник" metadata namespace
      new ExpectedToken(1, 25, 10, SemanticTokenTypes.Namespace, "Справочник"),
      // "." operator
      new ExpectedToken(1, 35, 1, SemanticTokenTypes.Operator, "."),
      // "Контрагенты" metadata class
      new ExpectedToken(1, 36, 11, SemanticTokenTypes.Class, "Контрагенты")
    );

    assertContainsTokens(decoded, expectedTokens);
  }

  @Test
  void sdblQuery_withAggregateFunction() {
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать СУММА(Сумма) как Итого из Документ.Продажа";
      КонецФункции
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      new ExpectedToken(1, 12, 7, SemanticTokenTypes.Keyword, "Выбрать"),
      new ExpectedToken(1, 20, 5, SemanticTokenTypes.Function, SemanticTokenModifiers.DefaultLibrary, "СУММА"),
      new ExpectedToken(1, 33, 3, SemanticTokenTypes.Keyword, "как"),
      new ExpectedToken(1, 43, 2, SemanticTokenTypes.Keyword, "из"),
      new ExpectedToken(1, 46, 8, SemanticTokenTypes.Namespace, "Документ"),
      new ExpectedToken(1, 55, 7, SemanticTokenTypes.Class, "Продажа")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void sdblQuery_withParameter() {
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * из Справочник.Контрагенты где Код = &Параметр";
      КонецФункции
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      new ExpectedToken(1, 12, 7, SemanticTokenTypes.Keyword, "Выбрать"),
      new ExpectedToken(1, 20, 1, SemanticTokenTypes.Operator, "*"),
      new ExpectedToken(1, 22, 2, SemanticTokenTypes.Keyword, "из"),
      new ExpectedToken(1, 25, 10, SemanticTokenTypes.Namespace, "Справочник"),
      new ExpectedToken(1, 36, 11, SemanticTokenTypes.Class, "Контрагенты"),
      new ExpectedToken(1, 48, 3, SemanticTokenTypes.Keyword, "где"),
      // &Параметр as single Parameter token (& at 58, Параметр is 8 chars, total length 9)
      new ExpectedToken(1, 58, 9, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Readonly, "&Параметр")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void sdblQuery_multiline() {
    String bsl = """
      Функция Тест()
        Запрос = "
        |Выбрать
        |  СУММА(Сумма) как Итого
        |из
        |  Справочник.Контрагенты";
      КонецФункции
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      new ExpectedToken(2, 3, 7, SemanticTokenTypes.Keyword, "Выбрать"),
      new ExpectedToken(3, 5, 5, SemanticTokenTypes.Function, SemanticTokenModifiers.DefaultLibrary, "СУММА"),
      new ExpectedToken(3, 18, 3, SemanticTokenTypes.Keyword, "как"),
      new ExpectedToken(4, 3, 2, SemanticTokenTypes.Keyword, "из"),
      new ExpectedToken(5, 5, 10, SemanticTokenTypes.Namespace, "Справочник"),
      new ExpectedToken(5, 16, 11, SemanticTokenTypes.Class, "Контрагенты")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void sdblQuery_virtualTableMethod() {
    String bsl = """
      Процедура Тест()
        Текст = "ВЫБРАТЬ * ИЗ РегистрСведений.КурсыВалют.СрезПоследних(&Период)";
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expectedTokens = List.of(
      // Metadata namespace
      new ExpectedToken(1, 24, 15, SemanticTokenTypes.Namespace, "РегистрСведений"),
      // Metadata class
      new ExpectedToken(1, 40, 10, SemanticTokenTypes.Class, "КурсыВалют"),
      // Virtual table method
      new ExpectedToken(1, 51, 13, SemanticTokenTypes.Method, "СрезПоследних")
    );

    assertContainsTokens(decoded, expectedTokens);
  }

  @Test
  void sdblQuery_temporaryTable() {
    String bsl = """
      Процедура Тест()
        Запрос = "
        |ВЫБРАТЬ Поле ПОМЕСТИТЬ ВТ_Таблица;
        |ВЫБРАТЬ Поле ИЗ ВТ_Таблица";
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      // First query - line 2, positions based on actual parsing
      new ExpectedToken(2, 3, 7, SemanticTokenTypes.Keyword, "ВЫБРАТЬ"),
      new ExpectedToken(2, 16, 9, SemanticTokenTypes.Keyword, "ПОМЕСТИТЬ"),
      new ExpectedToken(2, 26, 10, SemanticTokenTypes.Variable, SemanticTokenModifiers.Declaration, "ВТ_Таблица"),
      // Second query - line 3
      new ExpectedToken(3, 3, 7, SemanticTokenTypes.Keyword, "ВЫБРАТЬ"),
      new ExpectedToken(3, 16, 2, SemanticTokenTypes.Keyword, "ИЗ"),
      new ExpectedToken(3, 19, 10, SemanticTokenTypes.Variable, "ВТ_Таблица")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void sdblQuery_complexQueryWithJoin() {
    // Complex query with temporary table, join, and field references
    String bsl = """
      Процедура Тест()
        Запрос = "
        |ВЫБРАТЬ
        |    Курсы.Валюта КАК Валюта,
        |    Курсы.Курс КАК Курс,
        |    Курсы.Период КАК Период
        |ПОМЕСТИТЬ ВТ_Курсы
        |ИЗ РегистрСведений.КурсыВалют.СрезПоследних(&Период) КАК Курсы
        |ИНДЕКСИРОВАТЬ ПО Валюта, Период;
        |
        |ВЫБРАТЬ
        |    ВТ.Валюта КАК Валюта,
        |    ВТ.Курс КАК Курс,
        |    СпрВалюта.Код КАК КодВалюты
        |ИЗ ВТ_Курсы КАК ВТ
        |ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Валюты КАК СпрВалюта
        |ПО ВТ.Валюта = СпрВалюта.Ссылка";
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      // First query - line 2: ВЫБРАТЬ
      new ExpectedToken(2, 3, 7, SemanticTokenTypes.Keyword, "ВЫБРАТЬ"),
      // Line 3: Курсы.Валюта КАК Валюта
      new ExpectedToken(3, 7, 5, SemanticTokenTypes.Variable, "Курсы"),
      new ExpectedToken(3, 13, 6, SemanticTokenTypes.Property, "Валюта"),
      new ExpectedToken(3, 20, 3, SemanticTokenTypes.Keyword, "КАК"),
      new ExpectedToken(3, 24, 6, SemanticTokenTypes.Variable, SemanticTokenModifiers.Declaration, "Валюта"),
      // Line 6: ПОМЕСТИТЬ ВТ_Курсы
      new ExpectedToken(6, 3, 9, SemanticTokenTypes.Keyword, "ПОМЕСТИТЬ"),
      new ExpectedToken(6, 13, 8, SemanticTokenTypes.Variable, SemanticTokenModifiers.Declaration, "ВТ_Курсы"),
      // Line 7: ИЗ РегистрСведений.КурсыВалют.СрезПоследних(&Период) КАК Курсы
      new ExpectedToken(7, 3, 2, SemanticTokenTypes.Keyword, "ИЗ"),
      new ExpectedToken(7, 6, 15, SemanticTokenTypes.Namespace, "РегистрСведений"),
      new ExpectedToken(7, 22, 10, SemanticTokenTypes.Class, "КурсыВалют"),
      new ExpectedToken(7, 33, 13, SemanticTokenTypes.Method, "СрезПоследних"),
      new ExpectedToken(7, 47, 7, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Readonly, "&Период"),
      new ExpectedToken(7, 56, 3, SemanticTokenTypes.Keyword, "КАК"),
      new ExpectedToken(7, 60, 5, SemanticTokenTypes.Variable, SemanticTokenModifiers.Declaration, "Курсы"),
      // Line 8: ИНДЕКСИРОВАТЬ ПО Валюта, Период
      new ExpectedToken(8, 3, 13, SemanticTokenTypes.Keyword, "ИНДЕКСИРОВАТЬ"),
      new ExpectedToken(8, 17, 2, SemanticTokenTypes.Keyword, "ПО"),
      // Second query - line 10: ВЫБРАТЬ
      new ExpectedToken(10, 3, 7, SemanticTokenTypes.Keyword, "ВЫБРАТЬ"),
      // Line 14: ИЗ ВТ_Курсы КАК ВТ
      new ExpectedToken(14, 3, 2, SemanticTokenTypes.Keyword, "ИЗ"),
      new ExpectedToken(14, 6, 8, SemanticTokenTypes.Variable, "ВТ_Курсы"),
      new ExpectedToken(14, 15, 3, SemanticTokenTypes.Keyword, "КАК"),
      new ExpectedToken(14, 19, 2, SemanticTokenTypes.Variable, SemanticTokenModifiers.Declaration, "ВТ"),
      // Line 15: ЛЕВОЕ СОЕДИНЕНИЕ Справочник.Валюты КАК СпрВалюта
      new ExpectedToken(15, 3, 5, SemanticTokenTypes.Keyword, "ЛЕВОЕ"),
      new ExpectedToken(15, 9, 10, SemanticTokenTypes.Keyword, "СОЕДИНЕНИЕ"),
      new ExpectedToken(15, 20, 10, SemanticTokenTypes.Namespace, "Справочник"),
      new ExpectedToken(15, 31, 6, SemanticTokenTypes.Class, "Валюты"),
      new ExpectedToken(15, 38, 3, SemanticTokenTypes.Keyword, "КАК"),
      new ExpectedToken(15, 42, 9, SemanticTokenTypes.Variable, SemanticTokenModifiers.Declaration, "СпрВалюта"),
      // Line 16: ПО ВТ.Валюта = СпрВалюта.Ссылка
      new ExpectedToken(16, 3, 2, SemanticTokenTypes.Keyword, "ПО"),
      new ExpectedToken(16, 6, 2, SemanticTokenTypes.Variable, "ВТ"),
      new ExpectedToken(16, 9, 6, SemanticTokenTypes.Property, "Валюта"),
      new ExpectedToken(16, 18, 9, SemanticTokenTypes.Variable, "СпрВалюта"),
      new ExpectedToken(16, 28, 6, SemanticTokenTypes.Property, "Ссылка")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void sdblQuery_noTokenOverlaps() {
    String bsl = """
      Функция Тест()
        Запрос = "Выбрать * из Справочник.Контрагенты";
      КонецФункции
      """;

    var decoded = getDecodedTokens(bsl);

    // Sort tokens by position
    var sortedTokens = decoded.stream()
      .filter(t -> t.line == 1)
      .sorted((a, b) -> Integer.compare(a.start, b.start))
      .toList();

    // Verify no overlaps
    for (int i = 0; i < sortedTokens.size() - 1; i++) {
      var current = sortedTokens.get(i);
      var next = sortedTokens.get(i + 1);
      int currentEnd = current.start + current.length;

      assertThat(currentEnd)
        .as("Token at [%d, %d) should not overlap with next token at [%d, %d)",
          current.start, currentEnd, next.start, next.start + next.length)
        .isLessThanOrEqualTo(next.start);
    }
  }

  @Test
  void sdblQuery_valueFunctionWithPredefinedElement() {
    // Test: Значение(Справочник.Валюты.Рубль)
    // Справочник → Namespace, Валюты → Class, Рубль → EnumMember
    String bsl = """
      Процедура Тест()
        Запрос = "ВЫБРАТЬ * ГДЕ Валюта = Значение(Справочник.Валюты.Рубль)";
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      // Справочник → Namespace (metadata type) at position 44
      new ExpectedToken(1, 44, 10, SemanticTokenTypes.Namespace, "Справочник"),
      // Валюты → Class (metadata object) at position 55
      new ExpectedToken(1, 55, 6, SemanticTokenTypes.Class, "Валюты"),
      // Рубль → EnumMember (predefined element) at position 62
      new ExpectedToken(1, 62, 5, SemanticTokenTypes.EnumMember, "Рубль")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void sdblQuery_valueFunctionWithEmptyRef() {
    // Test: Значение(Справочник.Валюты.ПустаяСсылка)
    // Справочник → Namespace, Валюты → Class, ПустаяСсылка → EnumMember
    String bsl = """
      Процедура Тест()
        Запрос = "ВЫБРАТЬ * ГДЕ Валюта = Значение(Справочник.Валюты.ПустаяСсылка)";
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      // Справочник → Namespace at position 44
      new ExpectedToken(1, 44, 10, SemanticTokenTypes.Namespace, "Справочник"),
      // Валюты → Class at position 55
      new ExpectedToken(1, 55, 6, SemanticTokenTypes.Class, "Валюты"),
      // ПустаяСсылка → EnumMember at position 62
      new ExpectedToken(1, 62, 12, SemanticTokenTypes.EnumMember, "ПустаяСсылка")
    );

    assertContainsTokens(decoded, expected);
  }

  @Test
  void sdblQuery_valueFunctionWithEnum() {
    // Test: Значение(Перечисление.Пол.Мужской)
    // Перечисление → Namespace, Пол → Enum, Мужской → EnumMember
    String bsl = """
      Процедура Тест()
        Запрос = "ВЫБРАТЬ * ГДЕ Пол = Значение(Перечисление.Пол.Мужской)";
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      // Перечисление → Namespace (metadata type) at position 41
      new ExpectedToken(1, 41, 12, SemanticTokenTypes.Namespace, "Перечисление"),
      // Пол → Enum (enum object) at position 54
      new ExpectedToken(1, 54, 3, SemanticTokenTypes.Enum, "Пол"),
      // Мужской → EnumMember (enum value) at position 58
      new ExpectedToken(1, 58, 7, SemanticTokenTypes.EnumMember, "Мужской")
    );

    assertContainsTokens(decoded, expected);
  }

  // endregion
}

