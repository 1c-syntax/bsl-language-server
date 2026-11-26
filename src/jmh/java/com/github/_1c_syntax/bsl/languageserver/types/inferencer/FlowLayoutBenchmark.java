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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildingParseTreeVisitor;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.eclipse.lsp4j.Position;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Разложенное тело метода: цена построения и цена поиска оператора по позиции.
 * <p>
 * Поиск по позиции — горячий путь: через него отвечает {@code typesAt} по ссылке, то
 * есть hover и всё, что ходит по индексу ссылок. Сейчас это перебор операторов тела,
 * поэтому цена растёт с длиной метода. Бенчмарк меряет именно рост: {@code first} —
 * попадание в начало тела, {@code last} — в конец, и разница между ними показывает,
 * сколько стоит перебор.
 * <p>
 * Параметр {@code methodLength} — длина тела в операторах. В больших модулях 1С методы
 * на сотни операторов обычны.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class FlowLayoutBenchmark {

  @Param({"10", "100", "500"})
  public int methodLength;

  private BSLParser.CodeBlockContext body;
  private ControlFlowGraph graph;
  private FlowLayout layout;
  private Position firstStatement;
  private Position lastStatement;
  private List<Position> everyStatement;

  @Setup(Level.Trial)
  public void setup() {
    var ast = new BSLTokenizer(moduleSource(methodLength)).getAst();
    body = ast.subs().sub(0).procedure().subCodeBlock().codeBlock();
    graph = new CfgBuildingParseTreeVisitor().buildGraph(body);
    layout = FlowLayout.of(graph, body);

    everyStatement = new ArrayList<>();
    for (var statement : body.statement()) {
      var token = statement.getStart();
      everyStatement.add(new Position(token.getLine() - 1, token.getCharPositionInLine() + 1));
    }
    firstStatement = everyStatement.get(0);
    lastStatement = everyStatement.get(everyStatement.size() - 1);
  }

  /**
   * Построение раскладки по готовому графу — делается один раз на тело.
   *
   * @param blackhole приёмник результата.
   */
  @Benchmark
  public void buildLayout(Blackhole blackhole) {
    blackhole.consume(FlowLayout.of(graph, body));
  }

  /**
   * Поиск оператора в начале тела — перебор заканчивается сразу.
   *
   * @param blackhole приёмник результата.
   */
  @Benchmark
  public void statementAtFirst(Blackhole blackhole) {
    blackhole.consume(layout.statementAt(firstStatement));
  }

  /**
   * Поиск оператора в конце тела — перебор проходит все операторы.
   *
   * @param blackhole приёмник результата.
   */
  @Benchmark
  public void statementAtLast(Blackhole blackhole) {
    blackhole.consume(layout.statementAt(lastStatement));
  }

  /**
   * Поиск по каждому оператору тела — столько же запросов, сколько сделает диагностика,
   * спрашивающая тип на каждом операторе.
   *
   * @param blackhole приёмник результата.
   */
  @Benchmark
  public void statementAtEvery(Blackhole blackhole) {
    for (var position : everyStatement) {
      blackhole.consume(layout.statementAt(position));
    }
  }

  /** Тело метода заданной длины: присваивание, ветвление и обращения к переменной. */
  private static String moduleSource(int length) {
    var source = new StringBuilder("Процедура Тест(Флаг)\n\tДанные = Новый Структура;\n");
    source.append("\tЕсли Флаг Тогда\n\t\tДанные = Новый Соответствие;\n\tКонецЕсли;\n");
    for (var i = 0; i < length; i++) {
      source.append("\tЗначение").append(i).append(" = Данные.Количество();\n");
    }
    source.append("КонецПроцедуры\n");
    return source.toString();
  }
}
