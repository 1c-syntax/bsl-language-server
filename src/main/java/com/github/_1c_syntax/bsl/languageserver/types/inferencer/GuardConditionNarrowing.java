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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.index.AbstractDocumentLifecycleClearableIndex;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.AbstractCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TernaryOperatorNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Positions;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сужение типа переменной охраняющим условием.
 * <p>
 * Поддержаны две проверки, которыми в 1С отсекают тип:
 * <ul>
 *   <li>{@code ТипЗнч(Х) = Тип("СправочникОбъект.Товары")} — на истинной ветке
 *       переменная имеет ровно проверенный тип;</li>
 *   <li>{@code Х <> Неопределено} — на истинной ветке {@code Неопределено} из набора
 *       уходит.</li>
 * </ul>
 * Оба сравнения работают и в обратную сторону ({@code <>} против {@code =}), и на
 * ложной ветке — с противоположным смыслом.
 * <p>
 * Условие разбирается как <b>цепочка</b> таких проверок, соединённых одним оператором —
 * либо {@code И}, либо {@code ИЛИ}; отрицание меняет их местами по законам де Моргана.
 * Смешанная цепочка ({@code А И Б ИЛИ В}) не сужает ничего: из исхода условия не следует
 * исход отдельной проверки.
 * <p>
 * Сужение даётся с двух сторон:
 * <ul>
 *   <li><b>на рёбрах ветвления</b> — истинность конъюнкции даёт истинность каждого звена,
 *       ложность дизъюнкции — ложность каждого; на обратной ветке неизвестно, какое звено
 *       решило исход, поэтому сужает только условие из одной проверки;</li>
 *   <li><b>внутри самого условия</b> — вычисление цепочки сокращённое, поэтому раз до звена
 *       дошли, предыдущие в конъюнкции истинны, а в дизъюнкции ложны:
 *       {@code Если Х = Неопределено ИЛИ СтрДлина(Х) = 0} во второй проверке видит {@code Х}
 *       заполненным.</li>
 * </ul>
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class GuardConditionNarrowing extends AbstractDocumentLifecycleClearableIndex {

  private static final TypeRef UNDEFINED = new TypeRef(TypeKind.PRIMITIVE, "Неопределено");

  private static final String TYPE_OF_RU = "ТИПЗНЧ";
  private static final String TYPE_OF_EN = "TYPEOF";
  private static final String TYPE_RU = "ТИП";
  private static final String TYPE_EN = "TYPE";

  /** Метод-читатель ключа: {@code Стр.Свойство("Ключ", Приёмник)}. */
  private static final String PROPERTY_RU = "СВОЙСТВО";
  private static final String PROPERTY_EN = "PROPERTY";

  /** Позиция переменной-приёмника среди аргументов {@code Свойство}. */
  private static final int OUT_PARAMETER_INDEX = 1;

  private final Map<URI, Map<BSLParser.ExpressionContext, CompiledGuard>> compiledByUri = new ConcurrentHashMap<>();

  private final TypeRegistry typeRegistry;
  private final ReferenceResolver referenceResolver;

  /**
   * Утверждение о типе переменной, снятое с одной проверки в условии.
   *
   * @param type              проверяемый тип; {@code null} — проверка на {@code Неопределено}.
   * @param equality          было ли сравнение на равенство: {@code <>} переворачивает смысл.
   * @param provesInequality  доказывает ли проверка <b>отсутствие</b> типа на противоположной
   *                          ветке. У сравнения — да, из него следуют обе стороны. У чтения
   *                          ключа ({@code Стр.Свойство("Ключ", Приёмник)}) — нет: истина
   *                          говорит лишь «ключ нашёлся», а лежать в нём может и
   *                          {@code Неопределено} (его туда мог положить {@code Вставить}).
   */
  private record Assertion(
    SourceDefinedSymbol variable,
    @Nullable TypeRef type,
    boolean equality,
    boolean provesInequality
  ) {

    /** Утверждение сравнения: из него следуют обе ветки. */
    private static Assertion ofComparison(SourceDefinedSymbol variable, @Nullable TypeRef type, boolean equality) {
      return new Assertion(variable, type, equality, true);
    }

    /**
     * Утверждение чтения ключа: значение известно только когда ключ <b>не</b> нашёлся —
     * тогда платформа кладёт в приёмник {@code Неопределено}.
     */
    private static Assertion ofKeyPresence(SourceDefinedSymbol variable) {
      return new Assertion(variable, null, false, false);
    }

    /** То же утверждение с обратным знаком — для проверки под отрицанием. */
    private Assertion negated() {
      return new Assertion(variable, type, !equality, provesInequality);
    }

    /** Применить утверждение к типу на указанной ветке. */
    private TypeSet apply(boolean whenTrue, TypeSet incoming) {
      var ref = type == null ? UNDEFINED : type;
      // «Утверждается ли равенство на этой ветке»: `<>` переворачивает смысл, ложная ветка — ещё раз.
      if (equality != whenTrue) {
        return provesInequality ? incoming.without(ref) : incoming;
      }
      if (type == null) {
        return TypeSet.of(UNDEFINED);
      }
      // Набор заменяется целиком: проверка типа в коде утверждает про переменную больше,
      // чем известно из присваиваний. Если тип уже был в наборе, он остаётся со своими уточнениями.
      var retained = incoming.retaining(ref);
      return retained.isEmpty() ? TypeSet.of(ref) : retained;
    }
  }

  /**
   * Одно звено цепочки проверок: участок текста, который оно занимает, и утверждение,
   * которое из него следует.
   *
   * @param range     участок звена; {@code null}, если узел не привязан к тексту.
   * @param assertion утверждение о типе; {@code null}, если из звена ничего не следует.
   */
  private record ChainLink(@Nullable Range range, @Nullable Assertion assertion) {

    /** Говорит ли звено что-нибудь про эту переменную. */
    private boolean mentions(SourceDefinedSymbol variable) {
      return assertion != null && assertion.variable().equals(variable);
    }
  }

  /**
   * Разобранное охраняющее условие: цепочка его проверок и что из них следует про
   * анализируемую переменную.
   * <p>
   * Разбор и резолв переменной делаются один раз, а применение к типу — сколько угодно:
   * расчёт по потоку идёт проходами и спрашивает одно и то же условие многократно.
   *
   * @param connector чем соединены проверки: {@code AND}, {@code OR} либо {@code null},
   *                  если условие цепочкой одного вида не является и сужать по нему нельзя.
   * @param links     звенья цепочки в порядке вычисления — том, в котором их выдало дерево
   *                  выражения. Звено без утверждения тоже нужно: оно занимает своё место в
   *                  цепочке, и обращение может стоять именно в нём.
   * @param variables переменные, о которых условие что-то утверждает. Считаются при
   *                  разборе и хранятся готовыми: расчёт по потоку спрашивает их на каждом
   *                  ребре ветки в каждом проходе, и собирать набор заново выходило дороже,
   *                  чем сэкономленные вызовы сужения.
   */
  public record CompiledGuard(
    @Nullable BslOperator connector,
    List<ChainLink> links,
    Set<SourceDefinedSymbol> variables
  ) {

    /** Условие, из которого про переменную ничего не следует. */
    public static final CompiledGuard NONE = new CompiledGuard(null, List.of(), Set.of());

    /**
     * Скомпилированное условие с посчитанным набором переменных.
     *
     * @param connector чем соединены проверки.
     * @param links     звенья цепочки в порядке вычисления.
     * @return скомпилированное условие; {@link #NONE}, если утверждений нет вовсе.
     */
    static CompiledGuard of(@Nullable BslOperator connector, List<ChainLink> links) {
      Set<SourceDefinedSymbol> mentioned = Collections.newSetFromMap(new IdentityHashMap<>());
      for (var link : links) {
        if (link.assertion() != null) {
          mentioned.add(link.assertion().variable());
        }
      }
      return mentioned.isEmpty() ? NONE : new CompiledGuard(connector, List.copyOf(links), mentioned);
    }

    /**
     * Сузить тип переменной на ветке условия.
     *
     * @param variable переменная, тип которой сужается: условие может утверждать что-то
     *                 про несколько переменных, берутся только её проверки.
     * @param whenTrue ветка: {@code true} — истинная, {@code false} — ложная. Конъюнкция сужает
     *                 истинную ветку, дизъюнкция — ложную.
     * @param incoming тип переменной перед условием.
     * @return суженный тип; исходный, если сужать нечем.
     */
    public TypeSet apply(SourceDefinedSymbol variable, boolean whenTrue, TypeSet incoming) {
      // Истинность конъюнкции даёт истинность каждого звена, ложность дизъюнкции — ложность
      // каждого: исход ветки переносится на все звенья как есть. Обратные ветки неоднозначны —
      // неизвестно, какое звено решило исход, — там сужает только условие из одной проверки.
      var determined = connector == BslOperator.OR ? !whenTrue : whenTrue;
      if (!determined && links.size() > 1) {
        return incoming;
      }
      var narrowed = incoming;
      for (var link : links) {
        if (link.mentions(variable)) {
          narrowed = link.assertion().apply(whenTrue, narrowed);
        }
      }
      return narrowed;
    }

    /**
     * Сузить тип переменной внутри самого условия — по звеньям цепочки перед тем, в котором
     * стоит точка.
     * <p>
     * Вычисление цепочки в 1С сокращённое, поэтому раз до звена дошли, про предыдущие известно,
     * чем они кончились: в конъюнкции они истинны, в дизъюнкции — ложны. В
     * {@code Если Х <> Неопределено И СтрДлина(Х) > 0} ко второй проверке первая уже верна,
     * а в {@code Если Х = Неопределено ИЛИ СтрДлина(Х) = 0} — как раз неверна, что тоже сужает.
     * Знак берётся независимо от того, по какой ветке пойдёт условие в целом.
     *
     * @param variable переменная, тип которой сужается.
     * @param position точка внутри условия, для которой нужен тип.
     * @param incoming тип переменной на входе в условие.
     * @return суженный тип; исходный, если перед звеном с точкой про переменную ничего
     *     не утверждается.
     */
    public TypeSet narrowBefore(SourceDefinedSymbol variable, Position position, TypeSet incoming) {
      // Звено, в котором стоит обращение: дальше него утверждения не действуют, потому что
      // те проверки ещё не вычислялись. Ищется по вхождению точки в участок звена — это
      // отношение «часть-целое», а не сравнение порядка; сам порядок задан деревом.
      var here = indexOf(position);
      if (here <= 0) {
        // Звена с точкой нет, либо она в самом первом — предыдущих проверок нет.
        return incoming;
      }
      // В конъюнкции до звена доходят, когда предыдущие ИСТИННЫ; в дизъюнкции — когда ЛОЖНЫ.
      var previousHold = connector == BslOperator.AND;
      var narrowed = incoming;
      for (var i = 0; i < here; i++) {
        var link = links.get(i);
        if (link.mentions(variable)) {
          narrowed = link.assertion().apply(previousHold, narrowed);
        }
      }
      return narrowed;
    }

    /** Номер звена, чей участок текста накрывает точку; {@code -1}, если такого нет. */
    private int indexOf(Position position) {
      for (var i = 0; i < links.size(); i++) {
        var range = links.get(i).range();
        if (range != null && Ranges.containsPosition(range, position)) {
          return i;
        }
      }
      return -1;
    }
  }

  /**
   * Разобрать охраняющее условие в утверждения о переменных — из кэша либо на месте.
   * <p>
   * Разбор не зависит от того, чей тип сужают: условие может утверждать что-то про
   * несколько переменных сразу, и каждая проверка несёт свою. Поэтому разбирается оно
   * один раз на документ, а не заново на каждую переменную — резолв переменной через
   * индекс символов дорогой, а условий в методе столько же, сколько и переменных.
   *
   * @param condition       выражение условия.
   * @param documentContext контекст документа с условием.
   * @return разобранное условие; {@link CompiledGuard#NONE}, если сужать по нему нечего.
   */
  public CompiledGuard compile(BSLParser.ExpressionContext condition, DocumentContext documentContext) {
    var byCondition = compiledByUri.computeIfAbsent(documentContext.getUri(), uri -> new ConcurrentHashMap<>());
    var cached = byCondition.get(condition);
    if (cached != null) {
      return cached;
    }
    var compiled = compileCondition(condition, documentContext);
    var previous = byCondition.putIfAbsent(condition, compiled);
    return previous == null ? compiled : previous;
  }

  /**
   * Удалить кэш по URI документа.
   *
   * @param uri URI документа.
   */
  @Override
  public void clear(URI uri) {
    compiledByUri.remove(uri);
  }

  /** Разбор условия без кэш-обвязки. */
  private CompiledGuard compileCondition(BSLParser.ExpressionContext condition, DocumentContext documentContext) {
    var tree = ExpressionTreeBuildingVisitor.buildExpressionTree(condition);
    if (tree == null) {
      return CompiledGuard.NONE;
    }
    var checks = new ArrayList<Conjunct>();
    var connector = collectChain(tree, false, checks);
    if (connector == null && checks.size() > 1) {
      // Цепочка смешанная: из исхода условия не следует исход отдельной проверки.
      return CompiledGuard.NONE;
    }
    var links = new ArrayList<ChainLink>(checks.size());
    for (var conjunct : checks) {
      var assertion = assertionOf(conjunct.check(), documentContext);
      if (assertion != null && conjunct.negated()) {
        assertion = assertion.negated();
      }
      links.add(new ChainLink(rangeOf(conjunct.check()), assertion));
    }
    return CompiledGuard.of(connector, links);
  }

  /**
   * Разложить условие на цепочку проверок, соединённых одним и тем же оператором.
   * <p>
   * Отрицание не отбрасывается, а переворачивает смысл проверки: {@code Не Х = Неопределено}
   * — то же, что {@code Х <> Неопределено}. По законам де Моргана отрицание меняет местами
   * конъюнкцию и дизъюнкцию, поэтому под {@code Не} цепочка из {@code ИЛИ} читается как
   * конъюнкция; двойное отрицание возвращает исходный смысл.
   * <p>
   * Смешанная цепочка не разбирается: {@code И} связывает крепче, поэтому
   * {@code А И Б ИЛИ В} — это {@code (А И Б) ИЛИ В}, и из ложности левой части не следует
   * ложность {@code А} по отдельности. Порядок звеньев — тот, в котором их выдаёт дерево,
   * то есть порядок вычисления.
   *
   * @param node    узел условия.
   * @param negated находится ли узел под нечётным числом отрицаний.
   * @param target  список, куда складываются проверки вместе с их знаком.
   * @return оператор, соединяющий звенья, с поправкой на отрицания; {@code null}, если
   *     цепочка смешанная и сужать по ней нельзя.
   */
  @Nullable
  private static BslOperator collectChain(BslExpression node, boolean negated, List<Conjunct> target) {
    if (node instanceof UnaryOperationNode unary && unary.getOperator() == BslOperator.NOT) {
      return collectChain(unary.getOperand(), !negated, target);
    }
    if (node instanceof BinaryOperationNode binary
      && (binary.getOperator() == BslOperator.AND || binary.getOperator() == BslOperator.OR)) {
      // Под отрицанием конъюнкция и дизъюнкция меняются местами.
      var effective = binary.getOperator() == BslOperator.AND
        ? (negated ? BslOperator.OR : BslOperator.AND)
        : (negated ? BslOperator.AND : BslOperator.OR);
      var left = collectChain(binary.getLeft(), negated, target);
      var right = collectChain(binary.getRight(), negated, target);
      return sameConnector(effective, left) && sameConnector(effective, right) ? effective : null;
    }
    target.add(new Conjunct(node, negated));
    return null;
  }

  /**
   * Совместимо ли звено с оператором цепочки: либо это отдельная проверка (своего оператора
   * не имеет), либо вложенная цепочка того же вида.
   *
   * @param connector оператор цепочки.
   * @param link      оператор звена; {@code null} — отдельная проверка.
   * @return {@code true}, если звено не ломает однородность цепочки.
   */
  private static boolean sameConnector(BslOperator connector, @Nullable BslOperator link) {
    return link == null || link == connector;
  }

  /**
   * Одна проверка условия вместе со знаком, с которым она в него входит.
   *
   * @param check   узел проверки.
   * @param negated стоит ли проверка под отрицанием.
   */
  private record Conjunct(BslExpression check, boolean negated) {
  }

  /** Утверждение, снятое с одной проверки; {@code null}, если проверка ни о чём не говорит. */
  @Nullable
  private Assertion assertionOf(BslExpression check, DocumentContext documentContext) {
    var keyPresence = keyPresenceOf(check, documentContext);
    if (keyPresence != null) {
      return keyPresence;
    }
    if (!(check instanceof BinaryOperationNode binary)) {
      return null;
    }
    var operator = binary.getOperator();
    if (operator != BslOperator.EQUAL && operator != BslOperator.NOT_EQUAL) {
      return null;
    }
    var equality = operator == BslOperator.EQUAL;

    var typeCheck = typeCheckOf(binary, documentContext);
    if (typeCheck != null) {
      var resolved = typeRegistry.resolve(typeCheck.typeName(), documentContext.getFileType()).orElse(null);
      return resolved == null ? null : Assertion.ofComparison(typeCheck.variable(), resolved, equality);
    }
    var undefinedCheck = undefinedCheckOf(binary, documentContext);
    return undefinedCheck == null ? null : Assertion.ofComparison(undefinedCheck, null, equality);
  }

  /**
   * Участок текста, который занимает узел выражения вместе со своим поддеревом.
   * <p>
   * Узел операции привязан к тексту одним лишь оператором, а операнды — своими контекстами,
   * поэтому участок всего подвыражения собирается обходом поддерева.
   *
   * @param node узел выражения.
   * @return участок узла; {@code null}, если к тексту не привязан ни один узел поддерева.
   */
  @Nullable
  private static Range rangeOf(BslExpression node) {
    var own = ownRangeOf(node);
    return switch (node.getNodeType()) {
      case BINARY_OP -> {
        BinaryOperationNode binary = node.cast();
        yield enclosing(enclosing(own, rangeOf(binary.getLeft())), rangeOf(binary.getRight()));
      }
      case UNARY_OP -> enclosing(own, rangeOf(((UnaryOperationNode) node.cast()).getOperand()));
      case TERNARY_OP -> {
        TernaryOperatorNode ternary = node.cast();
        var covered = enclosing(own, rangeOf(ternary.getCondition()));
        covered = enclosing(covered, rangeOf(ternary.getTruePart()));
        yield enclosing(covered, rangeOf(ternary.getFalsePart()));
      }
      case CALL -> {
        AbstractCallNode call = node.cast();
        var covered = own;
        for (var argument : call.arguments()) {
          covered = enclosing(covered, rangeOf(argument));
        }
        yield covered;
      }
      default -> own;
    };
  }

  /** Участок текста самого узла, без поддерева; {@code null}, если узел к тексту не привязан. */
  @Nullable
  private static Range ownRangeOf(BslExpression node) {
    var ast = node.getRepresentingAst();
    return ast == null ? null : Ranges.create(ast);
  }

  /**
   * Наименьший участок, накрывающий оба переданных.
   *
   * @param first  первый участок; {@code null}, если его нет.
   * @param second второй участок; {@code null}, если его нет.
   * @return общий участок; {@code null}, если нет ни одного из двух.
   */
  @Nullable
  private static Range enclosing(@Nullable Range first, @Nullable Range second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    var start = Positions.isBefore(second.getStart(), first.getStart()) ? second.getStart() : first.getStart();
    var end = Positions.isBefore(first.getEnd(), second.getEnd()) ? second.getEnd() : first.getEnd();
    return new Range(start, end);
  }

  /**
   * Проверка вида {@code ТипЗнч(Х) = Тип("Имя")}: какая переменная проверяется и на какой тип.
   *
   * @param variable проверяемая переменная.
   * @param typeName имя типа из строкового литерала.
   */
  private record TypeCheck(SourceDefinedSymbol variable, String typeName) {
  }

  /** Проверка типа в любом порядке операндов, либо {@code null}. */
  @Nullable
  private TypeCheck typeCheckOf(BinaryOperationNode binary, DocumentContext documentContext) {
    var direct = typeCheckOfPair(binary.getLeft(), binary.getRight(), documentContext);
    return direct != null ? direct : typeCheckOfPair(binary.getRight(), binary.getLeft(), documentContext);
  }

  /** Проверка типа, если слева — {@code ТипЗнч(Х)}, а справа — {@code Тип("Имя")}. */
  @Nullable
  private TypeCheck typeCheckOfPair(
    BslExpression typeOfSide,
    BslExpression typeSide,
    DocumentContext documentContext
  ) {
    var argument = callArgument(typeOfSide, TYPE_OF_RU, TYPE_OF_EN);
    if (argument == null) {
      return null;
    }
    var variable = resolvedVariable(argument, documentContext);
    if (variable == null) {
      return null;
    }
    var typeArgument = callArgument(typeSide, TYPE_RU, TYPE_EN);
    if (typeArgument == null) {
      return null;
    }
    var typeName = stringLiteral(typeArgument);
    return typeName == null ? null : new TypeCheck(variable, typeName);
  }

  /**
   * Утверждение проверки {@code Стр.Свойство("Ключ", Приёмник)}.
   * <p>
   * Информативна ровно одна ветка — <b>ложная</b>: ключа нет, и платформа кладёт в приёмник
   * {@code Неопределено}. Из истины же следует только «ключ нашёлся», а какое там значение —
   * вопрос отдельный: {@code Вставить("Ключ", Неопределено)} даёт истину при
   * {@code Неопределено} в приёмнике, так что убирать его с истинной ветки нельзя. Тип
   * значения ключа приходит не отсюда, а от {@link PropertyMethodInference} — он же добавляет
   * к нему {@code Неопределено} на случай отсутствия ключа.
   * <p>
   * Проверка тут не про сам операнд условия, а про его <b>аргумент</b>: условие говорит
   * «нашлось ли», а сужается переменная, которую вызову отдали приёмником.
   *
   * @return утверждение про переменную-приёмник; {@code null}, если это не {@code Свойство}
   *     либо приёмником отдана не переменная.
   */
  @Nullable
  private Assertion keyPresenceOf(BslExpression check, DocumentContext documentContext) {
    if (!(check instanceof BinaryOperationNode binary)
      || binary.getOperator() != BslOperator.DEREFERENCE
      || !(binary.getRight() instanceof MethodCallNode call)) {
      return null;
    }
    var name = call.getName().getText().toUpperCase(Locale.ROOT);
    if (!PROPERTY_RU.equals(name) && !PROPERTY_EN.equals(name)) {
      return null;
    }
    var arguments = call.arguments();
    if (arguments.size() <= OUT_PARAMETER_INDEX) {
      return null;
    }
    var receiver = resolvedVariable(arguments.get(OUT_PARAMETER_INDEX), documentContext);
    return receiver == null ? null : Assertion.ofKeyPresence(receiver);
  }

  /** Переменная из проверки {@code Х = Неопределено} в любом порядке операндов, либо {@code null}. */
  @Nullable
  private SourceDefinedSymbol undefinedCheckOf(BinaryOperationNode binary, DocumentContext documentContext) {
    if (isUndefined(binary.getRight())) {
      return resolvedVariable(binary.getLeft(), documentContext);
    }
    return isUndefined(binary.getLeft()) ? resolvedVariable(binary.getRight(), documentContext) : null;
  }

  /**
   * Единственный аргумент вызова функции с одним из указанных имён, либо {@code null},
   * если это не такой вызов.
   */
  @Nullable
  private static BslExpression callArgument(BslExpression node, String nameRu, String nameEn) {
    if (!(node instanceof MethodCallNode call) || call.arguments().size() != 1) {
      return null;
    }
    var name = call.getName().getText().toUpperCase(Locale.ROOT);
    if (!name.equals(nameRu) && !name.equals(nameEn)) {
      return null;
    }
    return call.arguments().get(0);
  }

  /** Переменная, на которую ссылается узел-идентификатор; резолв по индексу, не по имени. */
  private @Nullable SourceDefinedSymbol resolvedVariable(BslExpression node, DocumentContext documentContext) {
    if (node.getNodeType() != ExpressionNodeType.IDENTIFIER
      || !(node.getRepresentingAst() instanceof TerminalNode terminal)) {
      return null;
    }
    return referenceResolver.findReference(documentContext.getUri(), terminal)
      .map(Reference::symbol)
      .filter(VariableSymbol.class::isInstance)
      .map(SourceDefinedSymbol.class::cast)
      .orElse(null);
  }

  /** Литерал {@code Неопределено}. */
  private static boolean isUndefined(BslExpression node) {
    if (node.getNodeType() != ExpressionNodeType.LITERAL) {
      return false;
    }
    var ast = node.getRepresentingAst();
    if (ast instanceof BSLParser.ConstValueContext constValue) {
      return constValue.UNDEFINED() != null;
    }
    return ast instanceof TerminalNode terminal
      && terminal.getSymbol().getType() == BSLParser.UNDEFINED;
  }

  /** Содержимое строкового литерала без кавычек, либо {@code null}. */
  @Nullable
  private static String stringLiteral(BslExpression node) {
    if (node.getNodeType() != ExpressionNodeType.LITERAL) {
      return null;
    }
    var text = node.getRepresentingAst().getText();
    if (text.length() < 2 || text.charAt(0) != '"' || text.charAt(text.length() - 1) != '"') {
      return null;
    }
    return text.substring(1, text.length() - 1);
  }
}
