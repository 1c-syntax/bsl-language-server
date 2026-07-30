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
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
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
 * Условие разбирается только как <b>конъюнкция</b> таких проверок: каждая из них верна
 * на истинной ветке, поэтому сужения накладываются одно на другое. Дизъюнкция не сужает
 * ничего — из {@code А ИЛИ Б} на истинной ветке не следует ни одна из частей. На ложной
 * ветке сужение применяется только к условию из одной проверки: отрицание конъюнкции —
 * это дизъюнкция, и какая именно часть оказалась ложной, неизвестно.
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

  private final Map<URI, Map<BSLParser.ExpressionContext, CompiledGuard>> compiledByUri = new ConcurrentHashMap<>();

  private final TypeRegistry typeRegistry;
  private final ReferenceResolver referenceResolver;

  /**
   * Утверждение о типе переменной, снятое с одной проверки в условии.
   *
   * @param type     проверяемый тип; {@code null} — проверка на {@code Неопределено}.
   * @param equality было ли сравнение на равенство: {@code <>} переворачивает смысл.
   */
  private record Assertion(
    SourceDefinedSymbol variable,
    @Nullable TypeRef type,
    boolean equality,
    @Nullable Position endsAt
  ) {

    /** То же утверждение с обратным знаком — для проверки под отрицанием. */
    private Assertion negated() {
      return new Assertion(variable, type, !equality, endsAt);
    }

    /** Заканчивается ли проверка, из которой снято утверждение, раньше указанной точки. */
    private boolean endsBefore(Position position) {
      if (endsAt == null) {
        return false;
      }
      return endsAt.getLine() < position.getLine()
        || endsAt.getLine() == position.getLine() && endsAt.getCharacter() <= position.getCharacter();
    }

    /** Применить утверждение к типу на указанной ветке. */
    private TypeSet apply(boolean whenTrue, TypeSet incoming) {
      var ref = type == null ? UNDEFINED : type;
      // «Утверждается ли равенство на этой ветке»: `<>` переворачивает смысл, ложная ветка — ещё раз.
      if (equality != whenTrue) {
        return incoming.without(ref);
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
   * Разобранное охраняющее условие: сколько в нём проверок и что из них следует про
   * анализируемую переменную.
   * <p>
   * Разбор и резолв переменной делаются один раз, а применение к типу — сколько угодно:
   * расчёт по потоку идёт проходами и спрашивает одно и то же условие многократно.
   *
   * @param conjunctCount общее число проверок в конъюнкции, включая не относящиеся к переменной.
   * @param assertions    утверждения, снятые с проверок про эту переменную.
   * @param variables     переменные, о которых условие что-то утверждает. Считаются при
   *                      разборе и хранятся готовыми: расчёт по потоку спрашивает их на
   *                      каждом ребре ветки в каждом проходе, и собирать набор заново
   *                      выходило дороже, чем сэкономленные вызовы сужения.
   */
  public record CompiledGuard(
    int conjunctCount,
    List<Assertion> assertions,
    Set<SourceDefinedSymbol> variables
  ) {

    /** Условие, из которого про переменную ничего не следует. */
    public static final CompiledGuard NONE = new CompiledGuard(0, List.of(), Set.of());

    /**
     * Скомпилированное условие с посчитанным набором переменных.
     *
     * @param conjunctCount общее число проверок в конъюнкции.
     * @param assertions    утверждения по проверкам.
     * @return скомпилированное условие.
     */
    static CompiledGuard of(int conjunctCount, List<Assertion> assertions) {
      if (assertions.isEmpty()) {
        return NONE;
      }
      Set<SourceDefinedSymbol> mentioned = Collections.newSetFromMap(new IdentityHashMap<>());
      for (var assertion : assertions) {
        mentioned.add(assertion.variable());
      }
      return new CompiledGuard(conjunctCount, assertions, mentioned);
    }

    /**
     * Сузить тип переменной на ветке условия.
     *
     * @param variable переменная, тип которой сужается: условие может утверждать что-то
     *                 про несколько переменных, берутся только её проверки.
     * @param whenTrue ветка: {@code true} — истинная, {@code false} — ложная.
     * @param incoming тип переменной перед условием.
     * @return суженный тип; исходный, если сужать нечем.
     */
    public TypeSet apply(SourceDefinedSymbol variable, boolean whenTrue, TypeSet incoming) {
      // На ложной ветке отрицание конъюнкции даёт дизъюнкцию: какая часть оказалась
      // ложной, неизвестно, поэтому сужает только условие из одной проверки.
      if (assertions.isEmpty() || !whenTrue && conjunctCount > 1) {
        return incoming;
      }
      var narrowed = incoming;
      for (var assertion : assertions) {
        if (assertion.variable().equals(variable)) {
          narrowed = assertion.apply(whenTrue, narrowed);
        }
      }
      return narrowed;
    }

    /**
     * Сузить тип переменной внутри самого условия — по проверкам, стоящим левее указанной точки.
     * <p>
     * В конъюнкции до правой части доходят только тогда, когда левая оказалась истинной:
     * в {@code Если Х <> Неопределено И Х.Поле = 5} ко второй проверке первая уже верна.
     * Поэтому проверки левее точки применяются как истинные независимо от того, по какой
     * ветке пойдёт условие в целом.
     * <p>
     * Дизъюнкция сюда не попадает: разбор условия с {@code ИЛИ} не даёт утверждений вовсе,
     * и это правильно — правая часть считается как раз тогда, когда левая ложна.
     *
     * @param variable переменная, тип которой сужается.
     * @param position точка внутри условия, для которой нужен тип.
     * @param incoming тип переменной на входе в условие.
     * @return суженный тип; исходный, если левее точки про переменную ничего не утверждается.
     */
    public TypeSet narrowBefore(SourceDefinedSymbol variable, Position position, TypeSet incoming) {
      var narrowed = incoming;
      for (var assertion : assertions) {
        if (assertion.variable().equals(variable) && assertion.endsBefore(position)) {
          narrowed = assertion.apply(true, narrowed);
        }
      }
      return narrowed;
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
    if (!collectConjuncts(tree, false, checks)) {
      return CompiledGuard.NONE;
    }
    var assertions = new ArrayList<Assertion>();
    for (var conjunct : checks) {
      var assertion = assertionOf(conjunct.check(), documentContext);
      if (assertion != null) {
        assertions.add(conjunct.negated() ? assertion.negated() : assertion);
      }
    }
    return CompiledGuard.of(checks.size(), List.copyOf(assertions));
  }

  /**
   * Разложить условие на проверки, соединённые {@code И}, с учётом отрицаний.
   * <p>
   * Отрицание не отбрасывается, а переворачивает смысл проверки: {@code Не Х = Неопределено}
   * — то же, что {@code Х <> Неопределено}. По законам де Моргана отрицание над {@code ИЛИ}
   * даёт конъюнкцию отрицаний, поэтому под {@code Не} разбирается уже дизъюнкция, а не
   * конъюнкция; двойное отрицание возвращает исходный смысл.
   *
   * @param node     узел условия.
   * @param negated  находится ли узел под нечётным числом отрицаний.
   * @param target   список, куда складываются проверки вместе с их знаком.
   * @return {@code false}, если встретилась дизъюнкция — тогда сужать нельзя.
   */
  private static boolean collectConjuncts(BslExpression node, boolean negated, List<Conjunct> target) {
    if (node instanceof UnaryOperationNode unary && unary.getOperator() == BslOperator.NOT) {
      return collectConjuncts(unary.getOperand(), !negated, target);
    }
    if (node instanceof BinaryOperationNode binary) {
      var operator = binary.getOperator();
      // Под отрицанием конъюнкция и дизъюнкция меняются местами.
      var conjunction = negated ? BslOperator.OR : BslOperator.AND;
      var disjunction = negated ? BslOperator.AND : BslOperator.OR;
      if (operator == disjunction) {
        return false;
      }
      if (operator == conjunction) {
        return collectConjuncts(binary.getLeft(), negated, target)
          && collectConjuncts(binary.getRight(), negated, target);
      }
    }
    target.add(new Conjunct(node, negated));
    return true;
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
    if (!(check instanceof BinaryOperationNode binary)) {
      return null;
    }
    var operator = binary.getOperator();
    if (operator != BslOperator.EQUAL && operator != BslOperator.NOT_EQUAL) {
      return null;
    }
    var equality = operator == BslOperator.EQUAL;

    var endsAt = endOf(check);
    var typeCheck = typeCheckOf(binary, documentContext);
    if (typeCheck != null) {
      var resolved = typeRegistry.resolve(typeCheck.typeName(), documentContext.getFileType()).orElse(null);
      return resolved == null ? null : new Assertion(typeCheck.variable(), resolved, equality, endsAt);
    }
    var undefinedCheck = undefinedCheckOf(binary, documentContext);
    return undefinedCheck == null ? null : new Assertion(undefinedCheck, null, equality, endsAt);
  }

  /**
   * Точка, на которой заканчивается проверка в тексте.
   *
   * @param check узел проверки.
   * @return точка за последним её символом; {@code null}, если узел не привязан к тексту.
   */
  @Nullable
  private static Position endOf(BslExpression check) {
    var ast = check.getRepresentingAst();
    return ast instanceof ParserRuleContext rule ? Ranges.create(rule).getEnd() : null;
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
