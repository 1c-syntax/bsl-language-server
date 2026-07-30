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

import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildOptions;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgEdgeType;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ConditionalVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
import com.github._1c_syntax.bsl.languageserver.cfg.WhileLoopVertex;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.index.AbstractDocumentLifecycleClearableIndex;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Тип переменной в конкретной точке кода — расчёт по графу потока управления тела.
 * <p>
 * Отвечает на вопрос «какой тип у переменной здесь», в отличие от объединения по всем
 * присваиваниям во всей области видимости. Присваивание перекрывает прежний тип,
 * оператор-мутатор ({@code Х.Вставить(…)}) дополняет накопленный, а в точках слияния
 * путей типы объединяются, поэтому
 * <pre>
 *   X = 10;
 *   ...        // здесь Число
 *   X = Истина;
 *   ...        // здесь Булево
 * </pre>
 * даёт разные ответы в разных местах.
 * <p>
 * Единица расчёта — <b>тело целиком</b>: один поиск неподвижной точки считает
 * {@link Environment окружение} — тип каждой переменной тела в каждой его точке. Поэтому
 * запрос про любую переменную тела — чтение готового ответа, а не отдельный расчёт.
 * <p>
 * Про присваиваемые типы, изменения мутаторов и сужение по условиям анализатор не знает
 * ничего — их отдают колбэки {@link FlowInputs}, а места изменений передаются готовыми
 * списками позиций. Поэтому здесь нет ни вывода типов выражений, ни индекса ссылок.
 * <p>
 * Кэшируется двоякое, и оба кэша сбрасываются по жизненному циклу документа: разложенное
 * по позициям тело и рассчитанные по нему окружения. Без второго неподвижная точка
 * считалась бы заново на каждый запрос типа, а диагностики спрашивают тип на каждое
 * обращение к члену.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class VariableFlowAnalyzer extends AbstractDocumentLifecycleClearableIndex {

  /**
   * Предел проходов по графу. Тип, приходящий по обратному ребру цикла, доезжает до
   * ранее посещённых вершин за считанные проходы; предел — страховка от незавершающегося
   * расчёта на неожиданной форме графа.
   */
  private static final int MAX_PASSES = 16;

  private final ControlFlowGraphIndex controlFlowGraphIndex;

  private final Map<URI, Map<BSLParser.CodeBlockContext, FlowLayout>> layoutsByUri = new ConcurrentHashMap<>();
  private final Map<URI, Map<BSLParser.CodeBlockContext, Facts>> factsByUri = new ConcurrentHashMap<>();
  private final Map<URI, Map<VariableSymbol, TypeSet>> cellsByUri = new ConcurrentHashMap<>();

  /**
   * Расчёты по телам, идущие прямо сейчас в рамках одного вывода типов.
   * <p>
   * Вывод типа присваивания просит типы переменных из правой части, и если они из того же
   * тела, запрос приходит посреди его же расчёта. Без этого списка он не нашёл бы готового
   * ответа в кэше и запустил бы расчёт тела заново — тем глубже, чем длиннее цепочка
   * зависимостей между переменными. Вместо этого он читает строящееся окружение.
   * <p>
   * Заводится вызывающим на один вывод типов и передаётся в {@link FlowInputs}. Владелец
   * явный, время жизни — вызов вывода; общего изменяемого состояния между потоками нет.
   */
  public static final class FlowSession {

    private final Map<BSLParser.CodeBlockContext, Pass> active = new IdentityHashMap<>();

    /**
     * Ячейки переменных, видимых другим телам: пока идут круги — приближения, после —
     * посчитанные значения. Запрос, пришедший в середину кругов, читает отсюда, иначе
     * круги запускались бы заново из самих себя.
     */
    private final Map<VariableSymbol, TypeSet> cells = new IdentityHashMap<>();

    /** Документы, для которых ячейки в этом выводе типов уже посчитаны. */
    private final Set<URI> cellsDone = new HashSet<>();

    /** Идут ли круги прямо сейчас: изнутри них ячейки заново не считаются. */
    private boolean cellsComputing;
  }

  /**
   * Тип каждой переменной тела в одной его точке.
   * <p>
   * Неизменяемо, поэтому соседние точки, между которыми ничего не поменялось, ссылаются на
   * одно окружение. За счёт этого хранение растёт по числу мест, где тип меняется, а не по
   * числу операторов, помноженному на число переменных.
   *
   * @param types типы переменных; переменных, о которых ничего не известно, в карте нет.
   */
  private record Environment(TypeSet[] types) {

    /**
     * Пустое окружение нулевой длины — обращение к любой переменной даёт пустой набор.
     * Годится как начальное значение для тела любого размера.
     */
    private static final Environment EMPTY = new Environment(new TypeSet[0]);

    /**
     * Окружение, в котором о переменных ничего не известно.
     *
     * @param size число переменных тела.
     * @return окружение нужного размера.
     */
    static Environment blank(int size) {
      var types = new TypeSet[size];
      Arrays.fill(types, TypeSet.EMPTY);
      return new Environment(types);
    }

    /** Тип переменной по её номеру; пустой набор, если про неё здесь ничего не известно. */
    TypeSet get(int index) {
      return index < types.length ? types[index] : TypeSet.EMPTY;
    }

    /**
     * Окружение с изменённым типом одной переменной.
     * <p>
     * Если тип не поменялся, возвращается это же окружение: так соседние точки, между
     * которыми ничего не произошло, ссылаются на один объект.
     */
    Environment with(int index, TypeSet type, int size) {
      if (type.equals(get(index))) {
        return this;
      }
      var changed = resized(size);
      changed[index] = type;
      return new Environment(changed);
    }

    /**
     * Объединение с другим окружением: тип каждой переменной — объединение её типов.
     * Так сходятся пути в точке слияния.
     */
    Environment union(Environment other) {
      if (types.length == 0) {
        return other;
      }
      if (other.types.length == 0) {
        return this;
      }
      TypeSet[] merged = null;
      for (var i = 0; i < types.length; i++) {
        var united = types[i].union(other.types[i]);
        if (merged == null && !united.equals(types[i])) {
          merged = types.clone();
        }
        if (merged != null) {
          merged[i] = united;
        }
      }
      return merged == null ? this : new Environment(merged);
    }

    /** Копия массива нужной длины: пустое окружение растягивается до размера тела. */
    private TypeSet[] resized(int size) {
      if (types.length == size) {
        return types.clone();
      }
      var grown = new TypeSet[size];
      Arrays.fill(grown, TypeSet.EMPTY);
      System.arraycopy(types, 0, grown, 0, types.length);
      return grown;
    }

    @Override
    public boolean equals(Object other) {
      return this == other || other instanceof Environment that && Arrays.equals(types, that.types);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(types);
    }

    /** По содержимому, а не по ссылке на массив: иначе в отладке смысла не видно. */
    @Override
    public String toString() {
      return "Environment" + Arrays.toString(types);
    }
  }

  /**
   * Изменение типа одной переменной одним оператором.
   *
   * @param variable   переменная.
   * @param position   позиция присваивания либо оператора-мутатора.
   * @param definition {@code true} — присваивание (задаёт тип заново),
   *                   {@code false} — мутатор (дополняет накопленный).
   */
  private record Change(VariableSymbol variable, int index, Position position, boolean definition) {
  }

  /**
   * Рассчитанное по телу, что не зависит от точки запроса.
   *
   * @param indexes           номера переменных, для которых расчёт по потоку применим; про
   *                          остальные отвечает прежний путь с обходом области видимости.
   *                          Номер — место переменной в окружении.
   * @param beforeStatement   окружение перед каждым оператором тела.
   * @param changesByStatement изменения типов по операторам, в которых они стоят.
   */
  private record Facts(
    Map<VariableSymbol, Integer> indexes,
    Map<ParserRuleContext, Environment> beforeStatement,
    Map<ParserRuleContext, List<Change>> changesByStatement,
    Environment leaving
  ) {

    /** Номер переменной в окружении; {@code null}, если расчёт по потоку к ней неприменим. */
    @Nullable
    Integer indexOf(VariableSymbol variable) {
      return indexes.get(variable);
    }

    /** Изменение типа переменной в этом операторе; {@code null}, если его тут нет. */
    @Nullable
    Change changeOf(ParserRuleContext statement, VariableSymbol variable) {
      for (var change : changesByStatement.getOrDefault(statement, List.of())) {
        if (change.variable() == variable) {
          return change;
        }
      }
      return null;
    }
  }

  /**
   * Присваиваемые переменной типы в точке присваивания.
   */
  @FunctionalInterface
  public interface AssignedTypes {

    /**
     * Типы, которые переменная получает в этом присваивании.
     *
     * @param variable  переменная, которой присваивают.
     * @param statement оператор графа, в котором стоит присваивание. Передаётся, чтобы
     *                  вызывающему не пришлось искать его спуском по дереву разбора:
     *                  расчёт уже знает этот узел.
     * @param position  позиция присваивания — начало имени переменной в левой части.
     * @return присваиваемые типы; пустой набор, если вывести их не удалось.
     */
    TypeSet at(VariableSymbol variable, ParserRuleContext statement, Position position);
  }

  /**
   * Изменение типа оператором-мутатором: {@code Х.Вставить("Поле", …)},
   * {@code Х.Колонки.Добавить("Имя", …)} и подобными.
   */
  @FunctionalInterface
  public interface Mutations {

    /**
     * Как оператор в этой позиции меняет тип переменной.
     *
     * @param variable переменная-получатель.
     * @param position позиция оператора-мутатора.
     * @param incoming тип переменной перед ним.
     * @return изменённый тип; исходный, если оператор ничего не добавляет.
     */
    TypeSet apply(VariableSymbol variable, Position position, TypeSet incoming);
  }

  /**
   * Сужение типа охраняющим условием на ветке.
   */
  public interface GuardNarrowing {

    /**
     * Как условие сужает тип переменной на своей ветке.
     *
     * @param variable  переменная, тип которой сужается.
     * @param condition выражение условия.
     * @param whenTrue  ветка: {@code true} — истинная, {@code false} — ложная.
     * @param incoming  тип переменной перед условием.
     * @return суженный тип; исходный, если условие про эту переменную ничего не утверждает.
     */
    TypeSet narrow(VariableSymbol variable, BSLParser.ExpressionContext condition, boolean whenTrue, TypeSet incoming);

    /**
     * Переменные, о которых условие вообще что-то утверждает.
     * <p>
     * Без этого пришлось бы спрашивать сужение про каждую переменную тела: условие почти
     * всегда говорит об одной, а рёбер ветвлений и проходов по графу много.
     *
     * @param condition выражение условия.
     * @return переменные из условия; пусто, если условие ни о чём не говорит.
     */
    Set<? extends SourceDefinedSymbol> variablesOf(BSLParser.ExpressionContext condition);

    /**
     * Как условие сужает тип переменной внутри самого себя — по проверкам, вычисляемым
     * до указанной точки.
     * <p>
     * Вычисление цепочки проверок сокращённое: раз до точки дошли, предыдущие проверки
     * в конъюнкции истинны, а в дизъюнкции ложны — и то и другое сужает.
     *
     * @param variable  переменная, тип которой сужается.
     * @param condition выражение условия.
     * @param position  точка внутри условия.
     * @param incoming  тип переменной на входе в условие.
     * @return суженный тип; исходный, если до точки про переменную ничего не утверждается.
     */
    TypeSet narrowBefore(
      VariableSymbol variable,
      BSLParser.ExpressionContext condition,
      Position position,
      TypeSet incoming
    );
  }

  /**
   * Исходные данные расчёта по телу.
   *
   * @param session             расчёты, идущие прямо сейчас в рамках этого вывода типов.
   * @param cacheable           можно ли запоминать результат: вложенный расчёт мог быть
   *                            усечён защитой от циклов, такой результат переиспользовать нельзя.
   * @param variables           переменные тела, за типами которых следит расчёт. Зависят
   *                            только от самого тела: окружение считается на всё тело разом
   *                            и переиспользуется всеми запросами, поэтому набор переменных
   *                            не может зависеть от того, про какую из них спросили первой.
   * @param sharedWithOtherBodies видна ли переменная другим телам модуля: тогда её тип
   *                            задаёт не только это тело, и любой вызов мог его сменить.
   * @param declaredFact        что о переменной объявлено помимо кода: типы параметра,
   *                            типизирующий комментарий и прочее, известное до первого
   *                            присваивания. У переменной, видимой другим телам, это лишь
   *                            начальное приближение — вход ей считает {@link #sharedEntryFact}.
   * @param definitionPositions позиции всех присваиваний переменной в документе;
   *                            учитываются только попавшие в это тело.
   * @param mutationPositions   позиции операторов, меняющих тип переменной на месте.
   * @param assigned            колбэк, отдающий присваиваемые типы по позиции.
   * @param mutations           колбэк, применяющий изменение оператора-мутатора.
   * @param narrowing           колбэк, сужающий тип по охраняющему условию ветки.
   *     <p>
   *     Переменные и места изменений отдаются поставщиками, а не значениями: за ними стоит
   *     обход индекса ссылок и разбор документирующих комментариев. При готовом ответе в
   *     кэше ничего этого не нужно, а тип спрашивают на каждое обращение к переменной.
   */
  public record FlowInputs(
    FlowSession session,
    boolean cacheable,
    Function<BSLParser.CodeBlockContext, Collection<VariableSymbol>> variables,
    Predicate<VariableSymbol> sharedWithOtherBodies,
    Function<VariableSymbol, TypeSet> declaredFact,
    Function<VariableSymbol, Collection<Position>> definitionPositions,
    Function<VariableSymbol, Collection<Position>> mutationPositions,
    AssignedTypes assigned,
    Mutations mutations,
    GuardNarrowing narrowing
  ) {
  }

  /**
   * Тип переменной в точке использования, когда узел использования уже под рукой —
   * например в разборе выражения. Тело и оператор находятся подъёмом по дереву от узла.
   *
   * @param documentContext контекст документа с использованием.
   * @param use             узел использования переменной.
   * @param variable        переменная, тип которой нужен.
   * @param inputs          исходные данные расчёта.
   * @return тип переменной в этой точке; {@code null}, если расчёт неприменим —
   *     тело не найдено или использование не удалось разместить в графе.
   */
  @Nullable
  public TypeSet typeAt(
    DocumentContext documentContext,
    ParserRuleContext use,
    VariableSymbol variable,
    FlowInputs inputs
  ) {
    var body = enclosingBody(use);
    if (body == null) {
      return null;
    }
    var layout = layoutOf(documentContext, body);
    var useStatement = enclosingStatement(use, layout);
    if (useStatement == null) {
      return null;
    }
    return typeAtStatement(
      documentContext, body, layout, useStatement, use, Ranges.create(use).getStart(), false, variable, inputs);
  }

  /**
   * Тип переменной в позиции документа — для вызывающих, у которых узла нет, а есть
   * только позиция (ссылка из индекса, точка курсора). Тело метода ищется перебором
   * объявлений верхнего уровня, оператор — по разложенному телу: спуска по дереву тут нет.
   *
   * @param documentContext контекст документа с использованием.
   * @param position        позиция использования.
   * @param atDefinition    стоит ли позиция на присваивании переменной: тогда тип берётся
   *                        после этого присваивания, а не до него.
   * @param variable        переменная, тип которой нужен.
   * @param inputs          исходные данные расчёта.
   * @return тип переменной в этой точке; {@code null}, если расчёт неприменим.
   */
  @Nullable
  public TypeSet typeAt(
    DocumentContext documentContext,
    Position position,
    boolean atDefinition,
    VariableSymbol variable,
    FlowInputs inputs
  ) {
    var body = bodyAt(documentContext, position);
    if (body == null) {
      return null;
    }
    var layout = layoutOf(documentContext, body);
    var useStatement = layout.statementAt(position);
    if (useStatement == null) {
      return null;
    }
    return typeAtStatement(
      documentContext, body, layout, useStatement, null, position, atDefinition, variable, inputs);
  }

  /**
   * Тип переменной, видимой другим телам, на входе в любое тело.
   * <p>
   * Значение такой переменной задаёт не одно тело: её мог оставить любой метод, который
   * успел отработать. Ответ — объединение того, с чем управление <b>покидает</b> тела,
   * где она меняется: значений на выходе и значений перед каждым вызовом. Тела, которые
   * её только читают, выпускают её такой же, какой получили, и ничего не добавляют.
   *
   * @param documentContext контекст документа с переменной.
   * @param variable        переменная.
   * @param inputs          исходные данные расчёта.
   * @return тип на входе в тело.
   */
  private TypeSet cellOf(DocumentContext documentContext, VariableSymbol variable, FlowInputs inputs) {
    var settled = cellsByUri.getOrDefault(documentContext.getUri(), Map.of()).get(variable);
    if (settled != null) {
      return settled;
    }
    return inputs.session().cells.getOrDefault(variable, inputs.declaredFact().apply(variable));
  }

  /**
   * Посчитать ячейки всех переменных документа, видимых другим телам, — если они ещё не
   * посчитаны в этом выводе типов.
   * <p>
   * Зависимость круговая: чтобы посчитать тело, нужен вход переменной, а чтобы узнать
   * вход — посчитать тела. Разрывается приближениями, как и цикл внутри тела: начинаем с
   * объявленного, считаем тела, объединяем полученное со входом и повторяем, пока
   * значения не перестанут расти.
   * <p>
   * Считается <b>до</b> того, как запрошенное тело попадёт в список идущих расчётов:
   * иначе тело, с которого всё началось, само в ячейки не попало бы — а оно ровно то,
   * где переменная чаще всего и меняется.
   *
   * @param documentContext контекст документа.
   * @param body            тело, расчёт которого запрошен: по нему берётся набор переменных.
   * @param inputs          исходные данные расчёта.
   */
  private void ensureCells(DocumentContext documentContext, BSLParser.CodeBlockContext body, FlowInputs inputs) {
    var session = inputs.session();
    var uri = documentContext.getUri();
    if (session.cellsComputing || !session.cellsDone.add(uri)) {
      return;
    }
    session.cellsComputing = true;
    try {
      var shared = sharedVariablesOf(body, inputs);
      for (var variable : shared) {
        session.cells.put(variable, inputs.declaredFact().apply(variable));
      }
      grow(documentContext, shared, inputs);
      if (inputs.cacheable()) {
        var byVariable = cellsByUri.computeIfAbsent(uri, key -> new ConcurrentHashMap<>());
        shared.forEach(variable -> byVariable.putIfAbsent(variable, session.cells.get(variable)));
      }
    } finally {
      session.cellsComputing = false;
    }
  }

  /** Переменные тела, видимые другим телам: у переменных модуля набор один на весь документ. */
  private static List<VariableSymbol> sharedVariablesOf(BSLParser.CodeBlockContext body, FlowInputs inputs) {
    var shared = new ArrayList<VariableSymbol>(0);
    for (var variable : inputs.variables().apply(body)) {
      if (inputs.sharedWithOtherBodies().test(variable)) {
        shared.add(variable);
      }
    }
    return shared;
  }

  /** Круги по телам, меняющим эти переменные, пока хоть одна ячейка растёт. */
  private void grow(DocumentContext documentContext, List<VariableSymbol> shared, FlowInputs inputs) {
    var session = inputs.session();
    var bodies = changedBodiesOf(documentContext, shared, inputs);
    for (var round = 0; round < MAX_PASSES && !bodies.isEmpty(); round++) {
      var grown = false;
      for (var body : bodies) {
        // Окружения не запоминаются: они посчитаны по промежуточному приближению входа
        // и через круг устареют.
        var facts = compute(documentContext, body, layoutOf(documentContext, body), inputs);
        for (var variable : shared) {
          var index = facts.indexOf(variable);
          if (index == null) {
            continue;
          }
          var united = session.cells.get(variable).union(facts.leaving().get(index));
          grown |= !united.equals(session.cells.put(variable, united));
        }
      }
      if (!grown) {
        break;
      }
    }
  }

  /** Тела, в которых эти переменные меняются, — по одному на тело и без повторов. */
  private List<BSLParser.CodeBlockContext> changedBodiesOf(
    DocumentContext documentContext,
    List<VariableSymbol> shared,
    FlowInputs inputs
  ) {
    var bodies = new ArrayList<BSLParser.CodeBlockContext>(1);
    for (var variable : shared) {
      var positions = new ArrayList<Position>(inputs.definitionPositions().apply(variable));
      positions.addAll(inputs.mutationPositions().apply(variable));
      for (var position : positions) {
        var body = bodyAt(documentContext, position);
        if (body != null && !containsIdentical(bodies, body)) {
          bodies.add(body);
        }
      }
    }
    return bodies;
  }

  /** Есть ли в списке этот же самый узел: тела сравниваются по ссылке, а не по содержимому. */
  private static boolean containsIdentical(List<BSLParser.CodeBlockContext> bodies,
                                           BSLParser.CodeBlockContext body) {
    for (var known : bodies) {
      if (known == body) {
        return true;
      }
    }
    return false;
  }

  /**
   * Удалить кэши по URI документа.
   *
   * @param uri URI документа.
   */
  @Override
  public void clear(URI uri) {
    layoutsByUri.remove(uri);
    factsByUri.remove(uri);
    cellsByUri.remove(uri);
  }

  /**
   * Разложенное тело — из кэша либо посчитанное на месте.
   * <p>
   * Считается <b>вне</b> {@code computeIfAbsent}: разбор тела не мгновенный, а под замком
   * корзины на нём выстраивались бы все потоки пакетного анализа. Двойная работа при гонке
   * безвредна — раскладка от расчёта не зависит.
   */
  private FlowLayout layoutOf(DocumentContext documentContext, BSLParser.CodeBlockContext body) {
    var byBody = layoutsByUri.computeIfAbsent(documentContext.getUri(), uri -> new ConcurrentHashMap<>());
    var cached = byBody.get(body);
    if (cached != null) {
      return cached;
    }
    var graph = controlFlowGraphIndex.graphOf(documentContext, body, CfgBuildOptions.defaults());
    var layout = FlowLayout.of(graph, body);
    var previous = byBody.putIfAbsent(body, layout);
    return previous == null ? layout : previous;
  }

  /** Общая часть обоих входов: проверки применимости и чтение окружения. */
  @Nullable
  private TypeSet typeAtStatement(
    DocumentContext documentContext,
    BSLParser.CodeBlockContext body,
    FlowLayout layout,
    ParserRuleContext useStatement,
    @Nullable ParserRuleContext useNode,
    Position usePosition,
    boolean atDefinition,
    VariableSymbol variable,
    FlowInputs inputs
  ) {
    if (layout.vertexOf(useStatement) == null) {
      return null;
    }
    // Запрос пришёл посреди расчёта этого же тела — из вывода типа чьего-то присваивания.
    // Отвечаем тем, что насчитано к этому моменту: перезапуск расчёта дал бы тот же ответ,
    // только пройдя тело ещё раз.
    var active = inputs.session().active.get(body);
    if (active != null) {
      return active.estimateAt(useStatement, variable);
    }
    var facts = factsOf(documentContext, body, layout, inputs);
    var index = facts.indexOf(variable);
    if (index == null) {
      return null;
    }
    var change = facts.changeOf(useStatement, variable);
    var before = facts.beforeStatement().getOrDefault(useStatement, Environment.EMPTY).get(index);
    var inclusive = useNode == null
      ? atDefinition
      : change != null && change.definition() && Ranges.containsPosition(Ranges.create(useNode), change.position());
    if (!inclusive || change == null) {
      return narrowedInsideCondition(layout, useStatement, usePosition, variable, before, inputs);
    }
    // Использование стоит на самом изменении — нужен тип после него.
    return change.definition()
      ? inputs.assigned().at(variable, useStatement, change.position())
      : inputs.mutations().apply(variable, change.position(), before);
  }

  /**
   * Тип, суженный проверками внутри самого условия — теми, что вычисляются до точки обращения.
   * <p>
   * Сужение по ветвям применяется к рёбрам, то есть к коду за условием. Но и внутри условия
   * порядок значим: раз до проверки дошли, предыдущие в конъюнкции истинны, а в дизъюнкции ложны.
   *
   * @param layout       раскладка тела.
   * @param useStatement оператор, в котором стоит обращение.
   * @param usePosition  точка обращения.
   * @param variable     переменная.
   * @param before       тип на входе в оператор.
   * @param inputs       исходные данные расчёта.
   * @return суженный тип; исходный, если оператор не условие либо до точки про переменную
   *     ничего не утверждается.
   */
  private static TypeSet narrowedInsideCondition(
    FlowLayout layout,
    ParserRuleContext useStatement,
    Position usePosition,
    VariableSymbol variable,
    TypeSet before,
    FlowInputs inputs
  ) {
    if (!(useStatement instanceof BSLParser.ExpressionContext condition)
      || !layout.isCondition(useStatement)) {
      return before;
    }
    return inputs.narrowing().narrowBefore(variable, condition, usePosition, before);
  }

  /**
   * Рассчитанные по телу окружения — из кэша либо посчитанные на месте. Окружения не
   * зависят от точки запроса, поэтому одного расчёта хватает на все обращения ко всем
   * переменным тела.
   */
  private Facts factsOf(
    DocumentContext documentContext,
    BSLParser.CodeBlockContext body,
    FlowLayout layout,
    FlowInputs inputs
  ) {
    var byBody = inputs.cacheable()
      ? factsByUri.computeIfAbsent(documentContext.getUri(), uri -> new ConcurrentHashMap<>())
      : null;
    if (byBody != null) {
      var cached = byBody.get(body);
      if (cached != null) {
        return cached;
      }
    }
    // Расчёт идёт ВНЕ computeIfAbsent: он не мгновенный, и под замком корзины на нём
    // выстраивались бы все потоки пакетного анализа. Вдобавок расчёт тянет вывод типов,
    // который может попросить тип переменной другого тела того же документа, — это
    // рекурсивное обновление той же карты, что запрещено.
    var computed = compute(documentContext, body, layout, inputs);
    if (byBody == null) {
      return computed;
    }
    var previous = byBody.putIfAbsent(body, computed);
    return previous == null ? computed : previous;
  }

  /** Собрать изменения типов по операторам и посчитать по ним окружения тела. */
  private Facts compute(
    DocumentContext documentContext,
    BSLParser.CodeBlockContext body,
    FlowLayout layout,
    FlowInputs inputs
  ) {
    // До того, как тело попадёт в список идущих расчётов: круги по ячейкам считают и его
    // тоже, а тело из этого списка отдало бы им пустой вклад.
    ensureCells(documentContext, body, inputs);
    // Каждой переменной, попавшей в расчёт, достаётся номер — её место в окружении.
    // Окружение хранится массивом по этим номерам, а не картой: копия массива на десяток
    // элементов дешевле копирования и перехеширования карты, а копий тут много — на
    // каждое изменение, слияние и сужение в каждом проходе.
    Map<VariableSymbol, Integer> indexes = new IdentityHashMap<>();
    var variables = new ArrayList<VariableSymbol>();
    Map<ParserRuleContext, List<Change>> changesByStatement = new IdentityHashMap<>();
    for (var variable : inputs.variables().apply(body)) {
      var definitions = inputs.definitionPositions().apply(variable);
      var mutations = inputs.mutationPositions().apply(variable);
      // Если хоть одно изменение типа не легло в граф отдельным оператором, его вклад
      // потерялся бы — тогда точнее прежний путь с обходом всей области видимости.
      // Отсутствие присваиваний расчёту не мешает: тип такой переменной — входной факт по
      // всему телу, и это тот же ответ, что дал бы обход области видимости.
      if (!allPlaced(layout, definitions) || !allPlaced(layout, mutations)) {
        continue;
      }
      var index = variables.size();
      indexes.put(variable, index);
      variables.add(variable);
      collectChanges(layout, variable, index, definitions, true, changesByStatement);
      collectChanges(layout, variable, index, mutations, false, changesByStatement);
    }
    if (variables.isEmpty()) {
      return new Facts(Map.of(), Map.of(), Map.of(), Environment.EMPTY);
    }
    var graph = controlFlowGraphIndex.graphOf(documentContext, body, CfgBuildOptions.defaults());
    var pass = new Pass(this, documentContext, graph, layout, inputs, variables, indexes, changesByStatement);
    var active = inputs.session().active;
    active.put(body, pass);
    try {
      return pass.computeFacts();
    } finally {
      active.remove(body);
    }
  }

  /** Разложить позиции изменений по операторам, в которых они стоят. */
  private static void collectChanges(
    FlowLayout layout,
    VariableSymbol variable,
    int index,
    Collection<Position> positions,
    boolean definition,
    Map<ParserRuleContext, List<Change>> target
  ) {
    layout.index(positions).forEach((statement, position) ->
      target.computeIfAbsent(statement, key -> new ArrayList<>(1))
        .add(new Change(variable, index, position, definition)));
  }

  /** Все ли изменения типа легли в операторы графа. */
  private static boolean allPlaced(FlowLayout layout, Collection<Position> positions) {
    for (var position : positions) {
      if (layout.insideBody(position) && !layout.isPlaced(position)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Один расчёт по телу: держит исходные данные и разложенное тело, чтобы не таскать их
   * через все шаги.
   */
  private static final class Pass {

    private final VariableFlowAnalyzer analyzer;
    private final DocumentContext documentContext;
    private final ControlFlowGraph graph;
    private final FlowLayout layout;
    private final FlowInputs inputs;
    /** Переменные тела по их номерам в окружении. */
    private final List<VariableSymbol> variables;
    private final Map<VariableSymbol, Integer> indexes;
    private final Map<ParserRuleContext, List<Change>> changesByStatement;
    /** Номера переменных, видимых другим телам: их тип сбрасывается на вызовах. */
    private final List<Integer> shared;

    /**
     * Присваиваемые типы, уже посчитанные в этом расчёте. Каждый проход до неподвижной
     * точки заново проходит по тем же присваиваниям, а вывод типа выражения справа —
     * самая дорогая часть шага.
     */
    private final Map<Position, TypeSet> assignedTypes = new HashMap<>();

    /**
     * Окружение перед каждым оператором. Заполняется по ходу расчёта, а не только в конце:
     * пока идут проходы, отсюда отвечают вложенные запросы про переменные этого же тела.
     */
    private final Map<ParserRuleContext, Environment> beforeStatement = new IdentityHashMap<>();

    /** Окружение на входе в тело: считается по первому требованию и переиспользуется. */
    @Nullable
    private Environment entry;

    /**
     * Окружение, с которым управление покидает тело: объединение значений перед каждым
     * вызовом. Нужно переменным, видимым другим телам: вызванный метод застаёт их
     * такими, какими они были в точке вызова.
     */
    private Environment leaving = Environment.EMPTY;

    /** Посчитанное окружение на выходе вершины и вход, для которого он посчитан. */
    private final Map<CfgVertex, Environment> outgoing = new IdentityHashMap<>();
    private final Map<CfgVertex, Environment> outgoingInput = new IdentityHashMap<>();

    private Pass(
      VariableFlowAnalyzer analyzer,
      DocumentContext documentContext,
      ControlFlowGraph graph,
      FlowLayout layout,
      FlowInputs inputs,
      List<VariableSymbol> variables,
      Map<VariableSymbol, Integer> indexes,
      Map<ParserRuleContext, List<Change>> changesByStatement
    ) {
      this.analyzer = analyzer;
      this.documentContext = documentContext;
      this.graph = graph;
      this.layout = layout;
      this.inputs = inputs;
      this.variables = variables;
      this.indexes = indexes;
      this.changesByStatement = changesByStatement;
      this.shared = new ArrayList<>(0);
      for (var i = 0; i < variables.size(); i++) {
        if (inputs.sharedWithOtherBodies().test(variables.get(i))) {
          shared.add(i);
        }
      }
    }

    /**
     * Окружение перед каждым оператором тела и окружение на выходе из него. Считается
     * один раз: сперва окружения по вершинам до неподвижной точки, затем один проход по
     * операторам каждой вершины.
     *
     * @return окружения тела.
     */
    private Facts computeFacts() {
      var byVertex = computeEntryFacts();
      for (var vertex : layout.orderedVertices()) {
        // Через тот же кэш выхода: к концу поиска неподвижной точки он уже посчитан для
        // этих же входных окружений, и повторный проход по операторам не нужен — вместе с
        // ним не нужны и повторные вызовы колбэков мутаторов.
        outgoingOf(vertex, byVertex.getOrDefault(vertex, Environment.EMPTY));
      }
      // Окружение на входе в вершину выхода — это и есть окружение на выходе из тела:
      // в неё сходятся все пути, включая Возврат из середины.
      var exit = byVertex.getOrDefault(graph.getExitPoint(), Environment.EMPTY);
      return new Facts(indexes, beforeStatement, changesByStatement, exit.union(leaving));
    }

    /**
     * Тип переменной перед оператором по тому, что насчитано к этому моменту. Отвечает на
     * вложенный запрос из вывода типа чьего-то присваивания в этом же теле.
     *
     * @param statement оператор, перед которым нужен тип.
     * @param variable  переменная.
     * @return тип; {@code null}, если до этого оператора расчёт ещё не дошёл — тогда
     *     вызывающему отвечает прежний путь с обходом всей области видимости.
     */
    @Nullable
    private TypeSet estimateAt(ParserRuleContext statement, VariableSymbol variable) {
      var index = indexes.get(variable);
      if (index == null) {
        return null;
      }
      var environment = beforeStatement.get(statement);
      return environment == null ? null : environment.get(index);
    }

    /**
     * Окружение на входе в каждую вершину. Расчёт идёт проходами по всем вершинам, пока
     * значения не перестанут меняться: так тип, приходящий по обратному ребру цикла,
     * доезжает до вершин, посещённых раньше него.
     */
    private Map<CfgVertex, Environment> computeEntryFacts() {
      Map<CfgVertex, Environment> facts = new IdentityHashMap<>();
      facts.put(graph.getEntryPoint(), entryEnvironment());

      for (var pass = 0; pass < MAX_PASSES; pass++) {
        var changed = false;
        for (var vertex : layout.orderedVertices()) {
          var incoming = joinOfPredecessors(vertex, facts);
          if (vertex == graph.getEntryPoint()) {
            incoming = incoming.union(entryEnvironment());
          }
          if (!incoming.equals(facts.get(vertex))) {
            facts.put(vertex, incoming);
            changed = true;
          }
        }
        if (!changed) {
          break;
        }
      }
      return facts;
    }

    /**
     * Окружение на входе в тело: что известно о переменных до первого присваивания.
     * Считается один раз — источники объявленного типа тянут разбор комментариев и
     * аннотаций, а спрашивают их в каждом проходе.
     */
    private Environment entryEnvironment() {
      if (entry == null) {
        var types = Environment.blank(variables.size());
        var current = types;
        for (var i = 0; i < variables.size(); i++) {
          current = current.with(i, entryFactOf(variables.get(i)), variables.size());
        }
        entry = current;
      }
      return entry;
    }

    /**
     * Тип переменной на входе в это тело. У переменной, живущей в одном теле, это
     * объявленное о ней; у видимой другим телам — то, с чем из тел выходят.
     */
    private TypeSet entryFactOf(VariableSymbol variable) {
      return inputs.sharedWithOtherBodies().test(variable)
        ? analyzer.cellOf(documentContext, variable, inputs)
        : inputs.declaredFact().apply(variable);
    }

    /**
     * Объединение окружений на выходе всех предшественников вершины. По рёбрам веток
     * условия окружение проходит суженным: на истинной ветке верно само условие, на
     * ложной — его отрицание. Этим же получается сужение охранным предложением
     * ({@code Если Х = Неопределено Тогда Возврат; КонецЕсли;}) — до кода за условием
     * доходит только ложная ветка.
     */
    private Environment joinOfPredecessors(CfgVertex vertex, Map<CfgVertex, Environment> facts) {
      var joined = Environment.EMPTY;
      for (var edge : graph.incomingEdgesOf(vertex)) {
        var predecessor = graph.getEdgeSource(edge);
        var atPredecessor = facts.get(predecessor);
        if (atPredecessor != null) {
          var outgoing = outgoingOf(predecessor, atPredecessor);
          joined = joined.union(narrowedByEdge(predecessor, edge.getType(), outgoing));
        }
      }
      return joined;
    }

    /**
     * Окружение на выходе вершины — то же, что на входе, но с применёнными операторами.
     * <p>
     * Считается один раз на каждое входное окружение, а не на каждое исходящее ребро.
     * Иначе операторы вершины проигрывались бы заново для каждого её последователя, а
     * вместе с ними — колбэки операторов-мутаторов, которые тянут разбор вызова и вывод
     * типа значения. Сравнение входа идёт по ссылке: окружения неизменяемы, и при
     * изменении на месте старого создаётся новое.
     *
     * @param vertex   вершина.
     * @param incoming окружение на входе в неё.
     * @return окружение на выходе.
     */
    private Environment outgoingOf(CfgVertex vertex, Environment incoming) {
      if (outgoingInput.get(vertex) == incoming) {
        return outgoing.get(vertex);
      }
      var result = applyStatements(vertex, incoming);
      outgoingInput.put(vertex, incoming);
      outgoing.put(vertex, result);
      return result;
    }

    /**
     * Окружение, прошедшее по ребру ветки условия. Условие цикла {@code Пока} сужает так
     * же, как условие {@code Если}: на входе в тело оно верно, за циклом — ложно.
     */
    private Environment narrowedByEdge(CfgVertex predecessor, CfgEdgeType edgeType, Environment outgoing) {
      if (edgeType != CfgEdgeType.TRUE_BRANCH && edgeType != CfgEdgeType.FALSE_BRANCH) {
        return outgoing;
      }
      var condition = conditionOf(predecessor);
      if (condition == null) {
        return outgoing;
      }
      // Сужаем только переменные, о которых условие правда что-то утверждает: перебор всех
      // переменных тела давал бы вызов колбэка на каждую, а условие обычно про одну.
      var mentioned = inputs.narrowing().variablesOf(condition);
      if (mentioned.isEmpty()) {
        return outgoing;
      }
      var whenTrue = edgeType == CfgEdgeType.TRUE_BRANCH;
      var narrowed = outgoing;
      for (var symbol : mentioned) {
        var index = indexes.get(symbol);
        if (index == null) {
          continue;
        }
        var variable = variables.get(index);
        narrowed = narrowed.with(
          index,
          inputs.narrowing().narrow(variable, condition, whenTrue, narrowed.get(index)),
          variables.size());
      }
      return narrowed;
    }

    /** Условие вершины-ветвления; {@code null}, если вершина условия не несёт. */
    private static BSLParser.@Nullable ExpressionContext conditionOf(CfgVertex vertex) {
      if (vertex instanceof ConditionalVertex conditional) {
        return conditional.getExpression();
      }
      return vertex instanceof WhileLoopVertex loop ? loop.getExpression() : null;
    }

    /**
     * Применить к окружению все операторы вершины по порядку, попутно запоминая окружение
     * перед каждым из них: пока идут проходы, отсюда отвечают вложенные запросы.
     */
    private Environment applyStatements(CfgVertex vertex, Environment incoming) {
      var current = incoming;
      for (var statement : layout.statementsOf(vertex)) {
        beforeStatement.put(statement, current);
        current = applyStatement(statement, current);
      }
      return current;
    }

    /**
     * Изменение окружения одним оператором: присваивание задаёт тип переменной заново,
     * оператор-мутатор дополняет уже накопленный. Операторов без изменений — большинство,
     * и они окружение не трогают.
     */
    private Environment applyStatement(ParserRuleContext statement, Environment incoming) {
      var changes = changesByStatement.get(statement);
      var current = incoming;
      if (changes != null) {
        for (var change : changes) {
          var variable = change.variable();
          var updated = change.definition()
            ? assignedTypes.computeIfAbsent(
              change.position(), position -> inputs.assigned().at(variable, statement, position))
            : inputs.mutations().apply(variable, change.position(), current.get(change.index()));
          current = current.with(change.index(), updated, variables.size());
        }
      }
      return forgetSharedAfterCall(statement, changes, current);
    }

    /**
     * Вернуть к объединению по области видимости переменные, видимые другим телам,
     * если оператор содержит вызов.
     * <p>
     * Вызванный метод мог присвоить такой переменной что угодно, поэтому дальше по коду
     * известно только объединение по области видимости — оно же входной факт. Присваивание в самом
     * операторе сильнее: оно происходит после того, как вызов вернул управление.
     * Точность здесь оператора, а не выражения: если вызов и обращение стоят в одном
     * операторе, обращение считается случившимся до вызова.
     *
     * @param statement оператор.
     * @param changes   изменения этого оператора; {@code null}, если их нет.
     * @param current   окружение после изменений оператора.
     * @return окружение с возвращёнными к объединению переменными.
     */
    private Environment forgetSharedAfterCall(
      ParserRuleContext statement,
      @Nullable List<Change> changes,
      Environment current
    ) {
      if (shared.isEmpty() || !layout.hasCall(statement)) {
        return current;
      }
      // То, что переменная содержит перед вызовом, вызванный метод видит — значит это
      // часть её значения на входе в другие тела, наравне со значением на выходе.
      leaving = leaving.union(current);
      var result = current;
      for (var index : shared) {
        if (assignedBy(changes, index)) {
          continue;
        }
        result = result.with(index, entryEnvironment().get(index), variables.size());
      }
      return result;
    }

    /** Задаёт ли оператор тип переменной заново. */
    private static boolean assignedBy(@Nullable List<Change> changes, int index) {
      if (changes == null) {
        return false;
      }
      for (var change : changes) {
        if (change.index() == index && change.definition()) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Тело метода или код модуля, которому принадлежит узел, — единица, по которой строится
   * граф потока управления. Это самый внешний блок кода над узлом: вложенные блоки
   * ветвлений и циклов частями графа не являются.
   */
  private static BSLParser.@Nullable CodeBlockContext enclosingBody(ParserRuleContext node) {
    ParserRuleContext current = node;
    BSLParser.CodeBlockContext outermost = null;
    while (current != null) {
      if (current instanceof BSLParser.CodeBlockContext codeBlock) {
        outermost = codeBlock;
      }
      current = current.getParent();
    }
    return outermost;
  }

  /** Ближайший оператор графа, внутри которого находится узел. */
  @Nullable
  private static ParserRuleContext enclosingStatement(ParserRuleContext node, FlowLayout layout) {
    ParserRuleContext current = node;
    while (current != null) {
      if (layout.hasStatement(current)) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  /**
   * Тело метода или код модуля, накрывающие позицию. Перебираются только объявления
   * верхнего уровня — в операторы разбор не спускается.
   */
  private static BSLParser.@Nullable CodeBlockContext bodyAt(DocumentContext documentContext, Position position) {
    var ast = documentContext.getAst();
    var subs = ast.subs();
    if (subs != null) {
      for (var sub : subs.sub()) {
        if (!Ranges.containsPosition(Ranges.create(sub), position)) {
          continue;
        }
        var procedure = sub.procedure();
        if (procedure != null) {
          return procedure.subCodeBlock().codeBlock();
        }
        var function = sub.function();
        return function == null ? null : function.subCodeBlock().codeBlock();
      }
    }
    var fileCodeBlock = ast.fileCodeBlock();
    if (fileCodeBlock == null) {
      return null;
    }
    // Тело модуля в дереве может быть не одно (код до и после объявлений методов), и
    // добраться отсюда можно не до всякого. Отдаём блок, только если он правда накрывает
    // позицию: иначе расчёт пошёл бы по чужому телу.
    var codeBlock = fileCodeBlock.codeBlock();
    return codeBlock != null && Ranges.containsPosition(Ranges.create(codeBlock), position) ? codeBlock : null;
  }
}
