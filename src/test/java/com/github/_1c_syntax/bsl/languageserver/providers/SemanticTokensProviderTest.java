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
import org.eclipse.lsp4j.SemanticTokensDeltaParams;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
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
      // Параметры:
      //  Парам - Число - описание
      Процедура ДокТест(Парам)
        // обычный комментарий
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    // Documentation comments are now split around BSL doc keywords and operators.
    // Line 0: "// Описание процедуры" - no BSL doc elements, full line as Comment+Documentation
    // Line 1: "// Параметры:" - keyword in structural position
    // Line 2: "//  Парам - Число - описание" - parameter name, type, operator, description
    // Body comment on line 4 should NOT have Documentation modifier
    var expected = List.of(
      // Line 0: full line as Comment+Documentation
      new ExpectedToken(0, 0, 21, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// Описание процедуры"),
      // Line 1: "// " before keyword
      new ExpectedToken(1, 0, 3, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// "),
      // Line 1: "Параметры:" keyword
      new ExpectedToken(1, 3, 10, SemanticTokenTypes.Macro, SemanticTokenModifiers.Documentation, "Параметры:"),
      // Line 2: "//  " before param name
      new ExpectedToken(2, 0, 4, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "//  "),
      // Line 2: "Парам" parameter name
      new ExpectedToken(2, 4, 5, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Documentation, "Парам"),
      // Line 2: " " between param name and dash
      new ExpectedToken(2, 9, 1, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " "),
      // Line 2: "-" operator
      new ExpectedToken(2, 10, 1, SemanticTokenTypes.Operator, SemanticTokenModifiers.Documentation, "-"),
      // Line 2: " " between dash and type
      new ExpectedToken(2, 11, 1, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " "),
      // Line 2: "Число" type
      new ExpectedToken(2, 12, 5, SemanticTokenTypes.Type, SemanticTokenModifiers.Documentation, "Число"),
      // Line 2: " " between type and second dash
      new ExpectedToken(2, 17, 1, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " "),
      // Line 2: "-" second operator
      new ExpectedToken(2, 18, 1, SemanticTokenTypes.Operator, SemanticTokenModifiers.Documentation, "-"),
      // Line 2: " описание" description text
      new ExpectedToken(2, 19, 9, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " описание"),
      // Line 3: Процедура keyword
      new ExpectedToken(3, 0, 9, SemanticTokenTypes.Keyword, "Процедура"),
      // Line 3: ДокТест method name
      new ExpectedToken(3, 10, 7, SemanticTokenTypes.Method, "ДокТест"),
      // Line 3: ( operator
      new ExpectedToken(3, 17, 1, SemanticTokenTypes.Operator, "("),
      // Line 3: Парам parameter definition
      new ExpectedToken(3, 18, 5, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Definition, "Парам"),
      // Line 3: ) operator
      new ExpectedToken(3, 23, 1, SemanticTokenTypes.Operator, ")"),
      // Line 4: body comment (no Documentation modifier)
      new ExpectedToken(4, 2, 22, SemanticTokenTypes.Comment, "// обычный комментарий"),
      // Line 5: КонецПроцедуры keyword
      new ExpectedToken(5, 0, 14, SemanticTokenTypes.Keyword, "КонецПроцедуры")
    );

    assertTokensMatch(decoded, expected);
  }

  @Test
  void variableDescriptionComments() {
    String bsl = """
      // Описание переменной
      Перем Перем1; // трейл
      """;

    var decoded = getDecodedTokens(bsl);

    // Leading comment on line 0 and trailing comment on line 1 should have documentation modifier
    var expected = List.of(
      // Line 0: leading comment as Comment+Documentation
      new ExpectedToken(0, 0, 22, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// Описание переменной"),
      // Line 1: Перем keyword
      new ExpectedToken(1, 0, 5, SemanticTokenTypes.Keyword, "Перем"),
      // Line 1: Перем1 variable definition
      new ExpectedToken(1, 6, 6, SemanticTokenTypes.Variable, SemanticTokenModifiers.Definition, "Перем1"),
      // Line 1: ; operator
      new ExpectedToken(1, 12, 1, SemanticTokenTypes.Operator, ";"),
      // Line 1: trailing comment as Comment+Documentation
      new ExpectedToken(1, 14, 8, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// трейл")
    );


    assertTokensMatch(decoded, expected);
  }

  @Test
  void bslDocKeywordsHighlighting() {
    String bsl = """
      // Описание функции
      //
      // Параметры:
      //   Имя - Строка - имя пользователя
      //   Возраст - Число - возраст
      //
      // Возвращаемое значение:
      //   Булево - результат проверки
      //
      Функция ПроверитьДанные(Имя, Возраст) Экспорт
        Возврат Истина;
      КонецФункции
      """;

    var decoded = getDecodedTokens(bsl);

    // BSL doc keywords should be highlighted as Macro with Documentation modifier
    // Parameter names should be highlighted as Parameter
    // Type names should be highlighted as Type
    // Dash operators should be highlighted as Operator
    // Comment parts around keywords/operators are Comment+Documentation
    var expected = List.of(
      // Line 0: full comment
      new ExpectedToken(0, 0, 19, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// Описание функции"),
      // Line 1: empty comment "//"
      new ExpectedToken(1, 0, 2, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "//"),
      // Line 2: "// " before keyword
      new ExpectedToken(2, 0, 3, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// "),
      // Line 2: "Параметры:" keyword
      new ExpectedToken(2, 3, 10, SemanticTokenTypes.Macro, SemanticTokenModifiers.Documentation, "Параметры:"),
      // Line 3: "//   " before parameter name
      new ExpectedToken(3, 0, 5, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "//   "),
      // Line 3: "Имя" parameter name
      new ExpectedToken(3, 5, 3, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Documentation, "Имя"),
      // Line 3: " " before dash
      new ExpectedToken(3, 8, 1, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " "),
      // Line 3: first "-" operator
      new ExpectedToken(3, 9, 1, SemanticTokenTypes.Operator, SemanticTokenModifiers.Documentation, "-"),
      // Line 3: " " after dash
      new ExpectedToken(3, 10, 1, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " "),
      // Line 3: "Строка" type
      new ExpectedToken(3, 11, 6, SemanticTokenTypes.Type, SemanticTokenModifiers.Documentation, "Строка"),
      // Line 3: " " before dash
      new ExpectedToken(3, 17, 1, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " "),
      // Line 3: second "-" operator
      new ExpectedToken(3, 18, 1, SemanticTokenTypes.Operator, SemanticTokenModifiers.Documentation, "-"),
      // Line 3: " имя пользователя" after dash
      new ExpectedToken(3, 19, 17, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " имя пользователя"),
      // Line 4: "//   " before parameter name
      new ExpectedToken(4, 0, 5, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "//   "),
      // Line 4: "Возраст" parameter name
      new ExpectedToken(4, 5, 7, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Documentation, "Возраст"),
      // Line 4: " " before dash
      new ExpectedToken(4, 12, 1, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " "),
      // Line 4: first "-" operator
      new ExpectedToken(4, 13, 1, SemanticTokenTypes.Operator, SemanticTokenModifiers.Documentation, "-"),
      // Line 4: " " after dash
      new ExpectedToken(4, 14, 1, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " "),
      // Line 4: "Число" type
      new ExpectedToken(4, 15, 5, SemanticTokenTypes.Type, SemanticTokenModifiers.Documentation, "Число"),
      // Line 4: " " before dash
      new ExpectedToken(4, 20, 1, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " "),
      // Line 4: second "-" operator
      new ExpectedToken(4, 21, 1, SemanticTokenTypes.Operator, SemanticTokenModifiers.Documentation, "-"),
      // Line 4: " возраст" after dash
      new ExpectedToken(4, 22, 8, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " возраст"),
      // Line 5: empty comment "//"
      new ExpectedToken(5, 0, 2, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "//"),
      // Line 6: "// " before keyword
      new ExpectedToken(6, 0, 3, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// "),
      // Line 6: "Возвращаемое значение:" keyword
      new ExpectedToken(6, 3, 22, SemanticTokenTypes.Macro, SemanticTokenModifiers.Documentation, "Возвращаемое значение:"),
      // Line 7: "//   " before type
      new ExpectedToken(7, 0, 5, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "//   "),
      // Line 7: "Булево" type
      new ExpectedToken(7, 5, 6, SemanticTokenTypes.Type, SemanticTokenModifiers.Documentation, "Булево"),
      // Line 7: " " before dash
      new ExpectedToken(7, 11, 1, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " "),
      // Line 7: "-" operator
      new ExpectedToken(7, 12, 1, SemanticTokenTypes.Operator, SemanticTokenModifiers.Documentation, "-"),
      // Line 7: " результат проверки" after dash
      new ExpectedToken(7, 13, 19, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " результат проверки"),
      // Line 8: empty comment "//"
      new ExpectedToken(8, 0, 2, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "//"),
      // Line 9: Функция keyword
      new ExpectedToken(9, 0, 7, SemanticTokenTypes.Keyword, "Функция"),
      // Line 9: ПроверитьДанные function name
      new ExpectedToken(9, 8, 15, SemanticTokenTypes.Function, "ПроверитьДанные"),
      // Line 9: ( operator
      new ExpectedToken(9, 23, 1, SemanticTokenTypes.Operator, "("),
      // Line 9: Имя parameter definition
      new ExpectedToken(9, 24, 3, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Definition, "Имя"),
      // Line 9: , operator
      new ExpectedToken(9, 27, 1, SemanticTokenTypes.Operator, ","),
      // Line 9: Возраст parameter definition
      new ExpectedToken(9, 29, 7, SemanticTokenTypes.Parameter, SemanticTokenModifiers.Definition, "Возраст"),
      // Line 9: ) operator
      new ExpectedToken(9, 36, 1, SemanticTokenTypes.Operator, ")"),
      // Line 9: Экспорт keyword
      new ExpectedToken(9, 38, 7, SemanticTokenTypes.Keyword, "Экспорт"),
      // Line 10: Возврат keyword
      new ExpectedToken(10, 2, 7, SemanticTokenTypes.Keyword, "Возврат"),
      // Line 10: Истина keyword (literal)
      new ExpectedToken(10, 10, 6, SemanticTokenTypes.Keyword, "Истина"),
      // Line 10: ; operator
      new ExpectedToken(10, 16, 1, SemanticTokenTypes.Operator, ";"),
      // Line 11: КонецФункции keyword
      new ExpectedToken(11, 0, 12, SemanticTokenTypes.Keyword, "КонецФункции")
    );

    assertTokensMatch(decoded, expected);
  }

  @Test
  void bslDocDeprecatedKeyword() {
    String bsl = """
      // Устарела. Используйте новый метод
      Процедура СтарыйМетод()
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    // Line 0: "// " Comment, "Устарела." Macro, " Используйте новый метод" Comment
    var expected = List.of(
      // Line 0: "// " before keyword
      new ExpectedToken(0, 0, 3, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// "),
      // Line 0: "Устарела." keyword
      new ExpectedToken(0, 3, 9, SemanticTokenTypes.Macro, SemanticTokenModifiers.Documentation, "Устарела."),
      // Line 0: " Используйте новый метод" after keyword
      new ExpectedToken(0, 12, 24, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, " Используйте новый метод"),
      // Line 1: Процедура keyword
      new ExpectedToken(1, 0, 9, SemanticTokenTypes.Keyword, "Процедура"),
      // Line 1: СтарыйМетод method name
      new ExpectedToken(1, 10, 11, SemanticTokenTypes.Method, "СтарыйМетод"),
      // Line 1: ( operator
      new ExpectedToken(1, 21, 1, SemanticTokenTypes.Operator, "("),
      // Line 1: ) operator
      new ExpectedToken(1, 22, 1, SemanticTokenTypes.Operator, ")"),
      // Line 2: КонецПроцедуры keyword
      new ExpectedToken(2, 0, 14, SemanticTokenTypes.Keyword, "КонецПроцедуры")
    );

    assertTokensMatch(decoded, expected);
  }

  @Test
  void bslDocExampleKeyword() {
    String bsl = """
      // Описание
      //
      // Пример:
      //   Результат = МойМетод();
      //
      Процедура МойМетод()
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    // Line 0: full line as Comment+Documentation
    // Line 1: "//" as Comment+Documentation
    // Line 2: "// " Comment, "Пример:" Macro
    // Line 3: full line as Comment+Documentation
    // Line 4: "//" as Comment+Documentation
    var expected = List.of(
      // Line 0: full comment
      new ExpectedToken(0, 0, 11, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// Описание"),
      // Line 1: empty comment "//"
      new ExpectedToken(1, 0, 2, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "//"),
      // Line 2: "// " before keyword
      new ExpectedToken(2, 0, 3, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "// "),
      // Line 2: "Пример:" keyword
      new ExpectedToken(2, 3, 7, SemanticTokenTypes.Macro, SemanticTokenModifiers.Documentation, "Пример:"),
      // Line 3: full comment
      new ExpectedToken(3, 0, 28, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "//   Результат = МойМетод();"),
      // Line 4: empty comment "//"
      new ExpectedToken(4, 0, 2, SemanticTokenTypes.Comment, SemanticTokenModifiers.Documentation, "//"),
      // Line 5: Процедура keyword
      new ExpectedToken(5, 0, 9, SemanticTokenTypes.Keyword, "Процедура"),
      // Line 5: МойМетод method name
      new ExpectedToken(5, 10, 8, SemanticTokenTypes.Method, "МойМетод"),
      // Line 5: ( operator
      new ExpectedToken(5, 18, 1, SemanticTokenTypes.Operator, "("),
      // Line 5: ) operator
      new ExpectedToken(5, 19, 1, SemanticTokenTypes.Operator, ")"),
      // Line 6: КонецПроцедуры keyword
      new ExpectedToken(6, 0, 14, SemanticTokenTypes.Keyword, "КонецПроцедуры")
    );

    assertTokensMatch(decoded, expected);
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

  @Test
  void sdblQuery_tableWithObjectTableName() {
    // Test: Справочник.Пользователи.ГруппыДоступа
    // Справочник → Namespace, Пользователи → Class, ГруппыДоступа → Class (table part is a full table)
    String bsl = """
      Процедура Тест()
        Запрос = "ВЫБРАТЬ * ИЗ Справочник.Пользователи.ГруппыДоступа";
      КонецПроцедуры
      """;

    var decoded = getDecodedTokens(bsl);

    var expected = List.of(
      // Справочник → Namespace (metadata type) at position 25
      new ExpectedToken(1, 25, 10, SemanticTokenTypes.Namespace, "Справочник"),
      // Пользователи → Class (metadata object) at position 36
      new ExpectedToken(1, 36, 12, SemanticTokenTypes.Class, "Пользователи"),
      // ГруппыДоступа → Class (table part is a full table) at position 49
      new ExpectedToken(1, 49, 13, SemanticTokenTypes.Class, "ГруппыДоступа")
    );

    assertContainsTokens(decoded, expected);
  }

  // endregion

  // region Delta tokens tests

  @Test
  void fullTokensReturnsResultId() {
    // given
    String bsl = """
      Перем А;
      """;

    // when
    SemanticTokens tokens = getTokens(bsl);

    // then
    assertThat(tokens.getResultId()).isNotNull();
    assertThat(tokens.getResultId()).isNotEmpty();
  }

  @Test
  void deltaWithSameDocument_returnsEmptyEdits() {
    // given
    String bsl = """
      Перем А;
      """;

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    SemanticTokens initialTokens = provider.getSemanticTokensFull(
      documentContext,
      new SemanticTokensParams(textDocumentIdentifier)
    );
    assertThat(initialTokens.getResultId()).isNotNull();

    // when
    var deltaParams = new SemanticTokensDeltaParams(textDocumentIdentifier, initialTokens.getResultId());
    var result = provider.getSemanticTokensFullDelta(documentContext, deltaParams);

    // then
    assertThat(result.isRight()).isTrue();
    var delta = result.getRight();
    assertThat(delta.getResultId()).isNotNull();
    assertThat(delta.getResultId()).isNotEqualTo(initialTokens.getResultId());
    assertThat(delta.getEdits()).isEmpty();
  }

  @Test
  void deltaWithUnknownPreviousResultId_returnsFullTokens() {
    // given
    String bsl = """
      Перем А;
      """;

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    // when
    var deltaParams = new SemanticTokensDeltaParams(textDocumentIdentifier, "unknown-result-id");
    var result = provider.getSemanticTokensFullDelta(documentContext, deltaParams);

    // then
    assertThat(result.isLeft()).isTrue();
    var fullTokens = result.getLeft();
    assertThat(fullTokens.getResultId()).isNotNull();
    assertThat(fullTokens.getData()).isNotEmpty();
  }

  @Test
  void deltaWithChangedDocument_returnsEdits() {
    // given
    String bsl1 = """
      Перем А;
      """;

    String bsl2 = """
      Перем А;
      Перем Б;
      """;

    DocumentContext context1 = TestUtils.getDocumentContext(bsl1);
    referenceIndexFiller.fill(context1);
    TextDocumentIdentifier textDocId1 = TestUtils.getTextDocumentIdentifier(context1.getUri());
    SemanticTokens tokens1 = provider.getSemanticTokensFull(context1, new SemanticTokensParams(textDocId1));

    URI differentUri = URI.create("file:///fake/different-document.bsl");
    DocumentContext context2 = TestUtils.getDocumentContext(differentUri, bsl2);
    referenceIndexFiller.fill(context2);
    TextDocumentIdentifier textDocId2 = TestUtils.getTextDocumentIdentifier(context2.getUri());

    // when
    var deltaParams = new SemanticTokensDeltaParams(textDocId2, tokens1.getResultId());
    var result = provider.getSemanticTokensFullDelta(context2, deltaParams);

    // then
    assertThat(result.isLeft()).isTrue();
    var fullTokens = result.getLeft();
    assertThat(fullTokens.getResultId()).isNotNull();
  }

  @Test
  void deltaWithModifiedSameDocument_returnsEdits() {
    // given
    String bsl1 = """
      Перем А;
      """;

    String bsl2 = """
      Перем А;
      Перем Б;
      """;

    DocumentContext context1 = TestUtils.getDocumentContext(bsl1);
    referenceIndexFiller.fill(context1);
    TextDocumentIdentifier textDocId1 = TestUtils.getTextDocumentIdentifier(context1.getUri());
    SemanticTokens tokens1 = provider.getSemanticTokensFull(context1, new SemanticTokensParams(textDocId1));

    DocumentContext context2 = TestUtils.getDocumentContext(context1.getUri(), bsl2);
    referenceIndexFiller.fill(context2);

    // when
    var deltaParams = new SemanticTokensDeltaParams(textDocId1, tokens1.getResultId());
    var result = provider.getSemanticTokensFullDelta(context2, deltaParams);

    // then
    assertThat(result.isRight()).isTrue();
    var delta = result.getRight();
    assertThat(delta.getResultId()).isNotNull();
    assertThat(delta.getEdits()).isNotEmpty();
    var edit = delta.getEdits().get(0);
    assertThat(edit.getDeleteCount() + (edit.getData() != null ? edit.getData().size() : 0))
      .isGreaterThan(0);
  }

  @Test
  void clearCache_removesCachedTokenData() {
    // given
    String bsl = """
      Перем А;
      """;

    DocumentContext documentContext = TestUtils.getDocumentContext(bsl);
    referenceIndexFiller.fill(documentContext);
    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());

    SemanticTokens initialTokens = provider.getSemanticTokensFull(
      documentContext,
      new SemanticTokensParams(textDocumentIdentifier)
    );

    // when
    provider.clearCache(documentContext.getUri());

    var deltaParams = new SemanticTokensDeltaParams(textDocumentIdentifier, initialTokens.getResultId());
    var result = provider.getSemanticTokensFullDelta(documentContext, deltaParams);

    // then
    assertThat(result.isLeft()).isTrue();
  }

  @Test
  void deltaWithLineInsertedAtBeginning_shouldHaveSmallDelta() {
    // given
    String bsl1 = """
      Перем А;
      Перем Б;
      Перем В;
      """;

    String bsl2 = """
      Перем Новая;
      Перем А;
      Перем Б;
      Перем В;
      """;

    DocumentContext context1 = TestUtils.getDocumentContext(bsl1);
    referenceIndexFiller.fill(context1);
    TextDocumentIdentifier textDocId1 = TestUtils.getTextDocumentIdentifier(context1.getUri());
    SemanticTokens tokens1 = provider.getSemanticTokensFull(context1, new SemanticTokensParams(textDocId1));

    DocumentContext context2 = TestUtils.getDocumentContext(context1.getUri(), bsl2);
    referenceIndexFiller.fill(context2);

    // when
    var deltaParams = new SemanticTokensDeltaParams(textDocId1, tokens1.getResultId());
    var result = provider.getSemanticTokensFullDelta(context2, deltaParams);

    // then - should return delta with small edits (just the new token + changed deltaLine)
    assertThat(result.isRight()).isTrue();
    var delta = result.getRight();
    var edit = delta.getEdits().get(0);
    // For inserting at beginning: prefix=0, suffix should match most of the old data
    // deleteCount should be small (just the first deltaLine that changed)
    // insertData should be the new token + updated first deltaLine
    assertThat(edit.getDeleteCount()).isLessThan(tokens1.getData().size());
  }

  @Test
  void deltaWithLineInsertedInMiddle_shouldReturnOptimalDelta() {
    // given - simulate inserting a line in middle of document
    String bsl1 = """
      Перем А;
      Перем Б;
      Перем В;
      Перем Г;
      """;

    String bsl2 = """
      Перем А;
      Перем Б;
      Перем Новая;
      Перем В;
      Перем Г;
      """;

    DocumentContext context1 = TestUtils.getDocumentContext(bsl1);
    referenceIndexFiller.fill(context1);
    TextDocumentIdentifier textDocId1 = TestUtils.getTextDocumentIdentifier(context1.getUri());
    SemanticTokens tokens1 = provider.getSemanticTokensFull(context1, new SemanticTokensParams(textDocId1));
    int originalDataSize = tokens1.getData().size();

    DocumentContext context2 = TestUtils.getDocumentContext(context1.getUri(), bsl2);
    referenceIndexFiller.fill(context2);

    // when
    var deltaParams = new SemanticTokensDeltaParams(textDocId1, tokens1.getResultId());
    var result = provider.getSemanticTokensFullDelta(context2, deltaParams);

    // then - should return delta, not full tokens
    assertThat(result.isRight()).isTrue();
    var delta = result.getRight();
    assertThat(delta.getEdits()).isNotEmpty();
    var edit = delta.getEdits().get(0);
    // For insertion in middle: 
    // - prefix matches up to insertion point
    // - suffix matches tokens after insertion (they have same relative deltaLine)
    // - The edit should be smaller than the full data
    int editSize = edit.getDeleteCount() + (edit.getData() != null ? edit.getData().size() : 0);
    assertThat(editSize).isLessThan(originalDataSize);
  }

  @Test
  void deltaWithTextInsertedOnSameLine_shouldReturnOptimalDelta() {
    // given - simulate inserting text on the same line without line breaks
    // This tests the case raised by @nixel2007: text insertion without newline
    String bsl1 = """
      Перем А;
      """;

    String bsl2 = """
      Перем Новая, А;
      """;

    DocumentContext context1 = TestUtils.getDocumentContext(bsl1);
    referenceIndexFiller.fill(context1);
    TextDocumentIdentifier textDocId1 = TestUtils.getTextDocumentIdentifier(context1.getUri());
    SemanticTokens tokens1 = provider.getSemanticTokensFull(context1, new SemanticTokensParams(textDocId1));

    // Verify original tokens structure
    var decoded1 = decode(tokens1.getData());
    var expected1 = List.of(
      new ExpectedToken(0, 0, 5, SemanticTokenTypes.Keyword, "Перем"),
      new ExpectedToken(0, 6, 1, SemanticTokenTypes.Variable, SemanticTokenModifiers.Definition, "А"),
      new ExpectedToken(0, 7, 1, SemanticTokenTypes.Operator, ";")
    );
    assertTokensMatch(decoded1, expected1);

    DocumentContext context2 = TestUtils.getDocumentContext(context1.getUri(), bsl2);
    referenceIndexFiller.fill(context2);
    SemanticTokens tokens2 = provider.getSemanticTokensFull(context2, new SemanticTokensParams(textDocId1));

    // Verify modified tokens structure
    var decoded2 = decode(tokens2.getData());
    var expected2 = List.of(
      new ExpectedToken(0, 0, 5, SemanticTokenTypes.Keyword, "Перем"),
      new ExpectedToken(0, 6, 5, SemanticTokenTypes.Variable, SemanticTokenModifiers.Definition, "Новая"),
      new ExpectedToken(0, 11, 1, SemanticTokenTypes.Operator, ","),
      new ExpectedToken(0, 13, 1, SemanticTokenTypes.Variable, SemanticTokenModifiers.Definition, "А"),
      new ExpectedToken(0, 14, 1, SemanticTokenTypes.Operator, ";")
    );
    assertTokensMatch(decoded2, expected2);

    // when
    var deltaParams = new SemanticTokensDeltaParams(textDocId1, tokens1.getResultId());
    var result = provider.getSemanticTokensFullDelta(context2, deltaParams);

    // then - should return delta, not full tokens
    assertThat(result.isRight()).isTrue();
    var delta = result.getRight();
    assertThat(delta.getEdits()).isNotEmpty();
    assertThat(delta.getEdits()).hasSize(1);
    
    // Verify the delta edit details
    // Original: [Перем, А, ;] - 3 tokens = 15 integers
    // Modified: [Перем, Новая, ,, А, ;] - 5 tokens = 25 integers
    // 
    // With lineOffset=0 inline edit handling:
    // - Prefix match: "Перем" (1 token = 5 integers)
    // - Suffix match: "А" and ";" (2 tokens = 10 integers)
    //   Note: "А" matches because the algorithm allows deltaStart to differ when lineOffset=0
    // - Edit deletes: nothing (0 integers)
    // - Edit inserts: "Новая" and "," (2 tokens = 10 integers)
    var edit = delta.getEdits().get(0);
    assertThat(edit.getStart())
      .as("Edit should start after the prefix match (Перем = 5 integers)")
      .isEqualTo(5);
    assertThat(edit.getDeleteCount())
      .as("Edit should delete nothing (suffix match includes А and ;)")
      .isEqualTo(0);
    assertThat(edit.getData())
      .as("Edit should insert Новая and , tokens (2 tokens = 10 integers)")
      .isNotNull()
      .hasSize(10);
    
    // Verify the edit is optimal (smaller than sending all new tokens)
    int editSize = edit.getDeleteCount() + edit.getData().size();
    assertThat(editSize).isLessThan(tokens2.getData().size());
  }

  // endregion
}

