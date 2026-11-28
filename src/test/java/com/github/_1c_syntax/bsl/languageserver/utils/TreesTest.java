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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.IncrementalTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreesTest {

  @Test
  void testGetTokensWithValidTree() {
    // given
    String input = """
      Процедура Тест()
      КонецПроцедуры""";
    var lexer = new BSLLexer(CharStreams.fromString(input));
    var tokenStream = new IncrementalTokenStream(lexer);
    var parser = new BSLParser(tokenStream);
    ParseTree tree = parser.file();

    // when
    List<Token> tokens = Trees.getTokens(tree);

    // then
    assertThat(tokens)
      .isNotEmpty()
      .hasSize(6);
  }

  @Test
  void groupsOfEqualNodesCollectsFirstOccurrenceWithLaterDuplicates() {
    // given — условия веток: А, Б, а (равно А без учёта регистра), В, Б
    var tree = parse("""
      Если А Тогда
      ИначеЕсли Б Тогда
      ИначеЕсли а Тогда
      ИначеЕсли В Тогда
      ИначеЕсли Б Тогда
      КонецЕсли;""");
    var ifStatement = (BSLParser.IfStatementContext) Trees.findAllRuleNodes(tree, BSLParser.RULE_ifStatement)
      .iterator().next();
    var conditions = new java.util.ArrayList<BSLParser.ExpressionContext>();
    conditions.add(ifStatement.ifBranch().expression());
    ifStatement.elsifBranch().forEach(branch -> conditions.add(branch.expression()));

    // when
    var groups = Trees.groupsOfEqualNodes(conditions, (first, candidate) -> true);

    // then
    assertThat(groups).isEqualTo(List.of(
      List.of(conditions.get(0), conditions.get(2)),
      List.of(conditions.get(1), conditions.get(4))
    ));
  }

  @Test
  void groupsOfEqualNodesSkipsPairsRejectedByFilter() {
    var tree = parse("""
      Если А Тогда
      ИначеЕсли А Тогда
      ИначеЕсли А Тогда
      КонецЕсли;""");
    var ifStatement = (BSLParser.IfStatementContext) Trees.findAllRuleNodes(tree, BSLParser.RULE_ifStatement)
      .iterator().next();
    var conditions = new java.util.ArrayList<BSLParser.ExpressionContext>();
    conditions.add(ifStatement.ifBranch().expression());
    ifStatement.elsifBranch().forEach(branch -> conditions.add(branch.expression()));
    var second = conditions.get(1);

    // фильтр не пускает второе условие кандидатом: в группу первого оно не входит и потому
    // начинает свою — с третьим, хоть тот уже повтор первого
    var groups = Trees.groupsOfEqualNodes(conditions, (first, candidate) -> candidate != second);

    assertThat(groups).isEqualTo(List.of(
      List.of(conditions.get(0), conditions.get(2)),
      List.of(conditions.get(1), conditions.get(2))
    ));
  }

  @Test
  void terminalNodeEndingAtPositionIsTheTokenRightBeforeCaret() {
    // given — «Перем Имя;»: Имя занимает символы 6..9
    var tree = (BSLParser.FileContext) parse("Перем Имя;");

    // then — каретка сразу за Имя даёт Имя, а не следующий токен «;»
    assertThat(Trees.findTerminalNodeEndingAtPosition(tree, new Position(0, 9)))
      .map(ParseTree::getText)
      .contains("Имя");
    // внутри токена — конец не совпадает
    assertThat(Trees.findTerminalNodeEndingAtPosition(tree, new Position(0, 8))).isEmpty();
    // начало строки
    assertThat(Trees.findTerminalNodeEndingAtPosition(tree, new Position(0, 0))).isEmpty();
  }

  @Test
  void innermostEnclosingNodeOfTypeIsTheDeepestContainingPosition() {
    // given — Ф(1, Г(2)); внешний doCall — «(1, Г(2))», внутренний — «(2)»
    var tree = parse("Ф(1, Г(2));");

    // then — внутри внутреннего вызова находится он, между аргументами внешнего — внешний
    assertThat(Trees.findInnermostEnclosing(tree, new Position(0, 7), BSLParser.DoCallContext.class))
      .map(ParseTree::getText)
      .contains("(2)");
    assertThat(Trees.findInnermostEnclosing(tree, new Position(0, 3), BSLParser.DoCallContext.class))
      .map(ParseTree::getText)
      .contains("(1,Г(2))");
    // позиция вне всех вызовов
    assertThat(Trees.findInnermostEnclosing(tree, new Position(0, 0), BSLParser.DoCallContext.class)).isEmpty();
  }

  @Test
  void ancestorOrSelfByAnyRuleStartsFromTheNodeItself() {
    // given — вызов внутри цикла внутри цикла
    var tree = parse("""
      Пока Истина Цикл
        Для Каждого Э Из М Цикл
          Ф();
        КонецЦикла;
      КонецЦикла;
      """);
    var call = Trees.findInnermostEnclosing(tree, new Position(2, 5), BSLParser.DoCallContext.class).orElseThrow();
    var loops = java.util.List.of(BSLParser.RULE_whileStatement, BSLParser.RULE_forEachStatement);

    // when
    var nearestLoop = Trees.getAncestorOrSelfByRuleIndex(call, loops);
    var fromLoopItself = Trees.getAncestorOrSelfByRuleIndex(nearestLoop, loops);

    // then — ближайший цикл, и сам узел, если его правило из набора
    assertThat(nearestLoop).isInstanceOf(BSLParser.ForEachStatementContext.class);
    assertThat(fromLoopItself).isSameAs(nearestLoop);
    assertThat(Trees.getAncestorOrSelfByRuleIndex(call, java.util.List.of(BSLParser.RULE_ifStatement))).isNull();
  }

  @Test
  void enclosesIncludesStartAndExcludesEnd() {
    // given — внутренний вызов «(2)» в «Ф(1, Г(2));» занимает символы 6–8, конец — позиция 9
    var tree = parse("Ф(1, Г(2));");
    var innerCall = Trees.findInnermostEnclosing(tree, new Position(0, 7), BSLParser.DoCallContext.class)
      .orElseThrow();

    // then
    assertThat(Trees.encloses(innerCall, new Position(0, 6))).isTrue();
    assertThat(Trees.encloses(innerCall, new Position(0, 8))).isTrue();
    assertThat(Trees.encloses(innerCall, new Position(0, 9))).isFalse();
    assertThat(Trees.encloses(innerCall, new Position(0, 5))).isFalse();
  }

  private static ParseTree parse(String input) {
    var lexer = new BSLLexer(CharStreams.fromString(input));
    var parser = new BSLParser(new IncrementalTokenStream(lexer));
    return parser.file();
  }

  @Test
  void testGetTokensWithNullTree() {
    // when
    var tokens = Trees.getTokens(null);

    // then
    assertThat(tokens).isEmpty();
  }

  @Test
  void testGetTokensWithEmptyTree() {
    // given
    String input = "";
    var lexer = new BSLLexer(CharStreams.fromString(input));
    var tokenStream = new IncrementalTokenStream(lexer);
    var parser = new BSLParser(tokenStream);
    ParseTree tree = parser.file();

    // when
    var tokens = Trees.getTokens(tree);

    // then
    assertThat(tokens).hasSize(1); // Only EOF token
  }

  @Test
  void testGetTokensReturnsCorrectTokenTypes() {
    // given
    String input = """
      Если Истина Тогда
      Возврат;
      КонецЕсли;""";
    var lexer = new BSLLexer(CharStreams.fromString(input));
    var tokenStream = new IncrementalTokenStream(lexer);
    var parser = new BSLParser(tokenStream);
    ParseTree tree = parser.file();

    // when
    var tokens = Trees.getTokens(tree);

    // then
    assertThat(tokens).hasSize(8);
    assertThat(tokens.get(0).getType()).isEqualTo(BSLLexer.IF_KEYWORD);
    assertThat(tokens.get(1).getType()).isEqualTo(BSLLexer.TRUE);
    assertThat(tokens.get(2).getType()).isEqualTo(BSLLexer.THEN_KEYWORD);
  }

  @Test
  void ancestorOrSelfConsidersNodeItselfThenAncestors() {
    // given
    var lexer = new BSLLexer(CharStreams.fromString("""
      Процедура Тест()
        А = 1;
      КонецПроцедуры"""));
    var parser = new BSLParser(new IncrementalTokenStream(lexer));
    var file = parser.file();
    var statement = Trees.<BSLParser.StatementContext>findAllRuleNodes(file, BSLParser.RULE_statement)
      .stream().findFirst().orElseThrow();
    var sub = Trees.<BSLParser.SubContext>getAncestorByRuleIndex(statement, BSLParser.RULE_sub);

    // then
    assertThat(Trees.<BSLParser.StatementContext>getAncestorOrSelfByRuleIndex(statement, BSLParser.RULE_statement))
      .isSameAs(statement);
    assertThat(Trees.<BSLParser.SubContext>getAncestorOrSelfByRuleIndex(statement, BSLParser.RULE_sub))
      .isSameAs(sub)
      .isNotNull();
    assertThat(Trees.<BSLParser.ModuleVarContext>getAncestorOrSelfByRuleIndex(statement, BSLParser.RULE_moduleVar))
      .isNull();
  }
}
