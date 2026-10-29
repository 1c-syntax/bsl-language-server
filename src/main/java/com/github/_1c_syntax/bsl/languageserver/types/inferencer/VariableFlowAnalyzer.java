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

import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildOptions;
import com.github._1c_syntax.bsl.languageserver.cfg.PreprocessorFlow;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.index.AbstractDocumentLifecycleClearableIndex;
import com.github._1c_syntax.bsl.languageserver.index.SourceBoundRecords;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
 * {@link FlowEnvironment окружение} — тип каждой переменной тела в каждой его точке. Поэтому
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
 * <p>
 * Окружения и ячейки для конкретных контекстов создаются по запросу и хранятся отдельно
 * от результата без ограничения режима. Иначе запрос для {@code #Если Сервер}
 * мог бы заменить тип, нужный другому клиенту API. Разложение тела общее: контекст
 * исключает недостижимые вершины и рёбра без изменения исходного графа.
 * Все варианты очищаются через {@link #clear(URI)} по жизненному циклу документа.
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
  static final int MAX_PASSES = 16;

  private final ControlFlowGraphIndex controlFlowGraphIndex;

  private final Map<URI, Map<BSLParser.CodeBlockContext, FlowLayout>> layoutsByUri = new ConcurrentHashMap<>();
  private final Map<URI, Map<BSLParser.CodeBlockContext, Facts>> factsByUri = new ConcurrentHashMap<>();
  /**
   * Окружения по документу, режиму и телу. Создаются только для запрошенных режимов,
   * удаляются при очистке документа; результаты без ограничения режима хранятся в {@code factsByUri}.
   */
  private final Map<URI, Map<ExecutionContext, Map<BSLParser.CodeBlockContext, Facts>>> contextualFactsByUri =
    new ConcurrentHashMap<>();
  /**
   * Ячейки переменных модуля по режимам и версии дерева символов. Смена дерева
   * обесценивает запись целиком, даже если символ переменной сравнивается равным прежнему.
   */
  private final SourceBoundRecords<SymbolTree, Map<ExecutionContext, Map<VariableSymbol, TypeSet>>>
    contextualCellsByUri = new SourceBoundRecords<>();
  /**
   * Ячейки переменных, видимых другим телам.
   * <p>
   * Ключ — символ переменной, а он у нового текста равен прежнему, пока объявление не
   * сдвинулось. Поэтому ячейки привязаны к дереву символов документа, по которому
   * посчитаны: после правки документа прежние не отдаются.
   */
  private final SourceBoundRecords<SymbolTree, Map<VariableSymbol, TypeSet>> cellsByUri = new SourceBoundRecords<>();

  /**
   * Места изменения переменной, уже спрошенные у вызывающего.
   * <p>
   * За ними стоит обход индекса ссылок, а спрашивают их на каждое тело, где переменная
   * видна: у переменной модуля это все тела документа. Без этой памяти обход индекса
   * повторялся бы столько раз, сколько в модуле методов. Привязаны к дереву символов, как
   * и ячейки: позиции прежнего текста после правки не отдаются.
   */
  private final SourceBoundRecords<SymbolTree, Map<VariableSymbol, Changes>> changesByUri =
    new SourceBoundRecords<>();

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

    private final Map<BSLParser.CodeBlockContext, VariableFlowPass> active = new IdentityHashMap<>();

    /**
     * Ячейки переменных, видимых другим телам: пока идут круги — приближения, после —
     * посчитанные значения. Запрос, пришедший в середину кругов, читает отсюда, иначе
     * круги запускались бы заново из самих себя.
     */
    private final Map<VariableSymbol, TypeSet> cells = new IdentityHashMap<>();

    /** Переменные, ячейки которых в этом выводе типов уже посчитаны. */
    private final Set<VariableSymbol> cellsDone = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Идут ли круги прямо сейчас: изнутри них ячейки заново не считаются. */
    private boolean cellsComputing;

    /**
     * Идут ли прямо сейчас круги по ячейкам переменных модуля.
     * <p>
     * Пока они идут, вход таких переменных в любое тело — промежуточное приближение, и всё,
     * что от него посчитано, после кругов устареет.
     *
     * @return {@code true}, если круги начаты и не завершены.
     */
    public boolean cellsComputing() {
      return cellsComputing;
    }

    /**
     * Идёт ли прямо сейчас расчёт по какому-нибудь телу.
     * <p>
     * Пока он идёт, окружение считается приближениями: до неподвижной точки тип переменной
     * в точке слияния путей ещё не окончателен. Значит, и <b>тип любого выражения</b>,
     * посчитанный в это время, — приближение, и запоминать его в кэшах, живущих дольше
     * расчёта, нельзя: следующий запрос про то же место получил бы промежуточный ответ
     * вместо посчитанного.
     *
     * @return {@code true}, если хотя бы один расчёт не завершён.
     */
    public boolean computing() {
      return !active.isEmpty() || cellsComputing;
    }

    /**
     * Идёт ли прямо сейчас расчёт по этому телу — неважно, ради чего он начат.
     *
     * @param body тело метода или модуля.
     * @return {@code true}, если расчёт по телу начат и не завершён.
     */
    public boolean computing(BSLParser.CodeBlockContext body) {
      return active.containsKey(body);
    }
  }



  /**
   * Изменение типа одной переменной одним оператором.
   *
   * @param variable   переменная.
   * @param index номер переменной в окружениях тела.
   * @param position   позиция присваивания либо оператора-мутатора.
   * @param definition {@code true} — присваивание (задаёт тип заново),
   *                   {@code false} — мутатор (дополняет накопленный).
   */
  record Change(VariableSymbol variable, int index, Position position, boolean definition) {
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
  record Facts(
    Map<VariableSymbol, Integer> indexes,
    Map<ParserRuleContext, FlowEnvironment> beforeStatement,
    Map<ParserRuleContext, List<Change>> changesByStatement,
    FlowEnvironment leaving
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
   * @param executionContext    режим для выбора кеша; {@code null} использует общий результат
   *                            по всем возможным контекстам модуля и метода.
   * @param compilationContexts возможные режимы каждого тела с учётом выбранного контекста,
   *                            модуля, директивы и ветки объявления метода. Пустое множество
   *                            исключает тело; пустой Optional отключает модель контекстов 1С.
   * @param sawMissing          видел ли вывод типов значения, которых ещё нет: пока рабочая
   *                            область наполняется, так бывает с функциями модулей, до которых
   *                            очередь не дошла. Посчитанное на них неполно и в кэши, живущие
   *                            дольше вывода, не попадает: следующий расчёт взял бы оттуда
   *                            неполное значение, не узнав, что оно неполное.
   * @param variables           переменные тела, за типами которых следит расчёт. Зависят
   *                            только от самого тела: окружение считается на всё тело разом
   *                            и переиспользуется всеми запросами, поэтому набор переменных
   *                            не может зависеть от того, про какую из них спросили первой.
   * @param sharedWithOtherBodies видна ли переменная другим телам модуля: тогда её тип
   *                            задаёт не только это тело, и любой вызов мог его сменить.
   * @param declaredFact        что о переменной объявлено помимо кода: типы параметра,
   *                            типизирующий комментарий и прочее, известное до первого
   *                            присваивания. У переменной, видимой другим телам, это лишь
   *                            начальное приближение её ячейки.
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
    @Nullable ExecutionContext executionContext,
    Function<BSLParser.CodeBlockContext, Optional<Set<ExecutionContext>>> compilationContexts,
    BooleanSupplier sawMissing,
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
   * Тип переменной по всей её области видимости — ответ там, где точки исполнения нет.
   * <p>
   * Так спрашивают про объявление ({@code Перем Кэш;} оператором не является) и про
   * прочие места вне операторов. Ответ — то, с чем переменная <b>покидает</b> свою
   * область: у переменной модуля это её ячейка, собранная по всем телам, у переменной
   * одного тела — значения на выходе из него и перед его вызовами.
   *
   * @param documentContext контекст документа с переменной.
   * @param declaration     позиция объявления переменной: по ней ищется её тело.
   * @param variable        переменная.
   * @param inputs          исходные данные расчёта.
   * @return тип переменной по области видимости.
   */
  public TypeSet typesAcrossScope(
    DocumentContext documentContext,
    Position declaration,
    VariableSymbol variable,
    FlowInputs inputs
  ) {
    var body = bodyAt(documentContext, declaration);
    if (body != null) {
      var facts = factsOf(documentContext, body, layoutOf(documentContext, body), inputs);
      var index = facts.indexOf(variable);
      if (index != null) {
        return facts.leaving().get(index);
      }
    }
    // Тела у объявления нет либо переменная в нём не живёт — так стоит `Перем` уровня
    // модуля. Значение такой переменной оставляют тела, и оно как раз в её ячейке.
    ensureCellsFor(documentContext, List.of(variable), inputs);
    return cellOf(documentContext, variable, inputs);
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
  TypeSet cellOf(DocumentContext documentContext, VariableSymbol variable, FlowInputs inputs) {
    var settled = cellsOf(documentContext, inputs.executionContext()).get(variable);
    if (settled != null) {
      return settled;
    }
    var current = inputs.session().cells.get(variable);
    return current == null ? inputs.declaredFact().apply(variable) : current;
  }

  /**
   * Получить хранилище завершённых ячеек для текущей версии дерева и выбранного режима.
   *
   * @param documentContext документ — владелец переменных.
   * @param context режим компиляции или {@code null} для общего хранилища.
   * @return изменяемая потокобезопасная карта, создаваемая при первом обращении.
   */
  private Map<VariableSymbol, TypeSet> cellsOf(DocumentContext documentContext, @Nullable ExecutionContext context) {
    if (context == null) {
      return cellsByUri.get(documentContext.getUri(), documentContext.getSymbolTreeNoLock(),
        tree -> new ConcurrentHashMap<>());
    }
    return contextualCellsByUri
      .get(documentContext.getUri(), documentContext.getSymbolTreeNoLock(), tree -> new ConcurrentHashMap<>())
      .computeIfAbsent(context, ignored -> new ConcurrentHashMap<>());
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
    if (inputs.session().cellsComputing) {
      return;
    }
    ensureCellsFor(documentContext, sharedVariablesOf(body, inputs), inputs);
  }

  /**
   * Посчитать ячейки перечисленных переменных — тех из них, у которых ячейки ещё нет.
   *
   * @param documentContext контекст документа.
   * @param shared          переменные, видимые другим телам.
   * @param inputs          исходные данные расчёта.
   */
  private void ensureCellsFor(DocumentContext documentContext, List<VariableSymbol> shared, FlowInputs inputs) {
    var session = inputs.session();
    // Готовые ячейки — общие на весь документ и переживают отдельный вывод типов. Без
    // этой проверки круги гонялись бы заново на каждый запрос типа: своя память о них
    // заводится на один вывод, а запросов на документ тысячи. Посчитанное кладётся туда
    // же, откуда прочитано, — к дереву символов, по которому шёл расчёт.
    var settled = cellsOf(documentContext, inputs.executionContext());
    var pending = new ArrayList<VariableSymbol>(shared.size());
    for (var variable : shared) {
      if (!settled.containsKey(variable) && session.cellsDone.add(variable)) {
        pending.add(variable);
      }
    }
    if (pending.isEmpty()) {
      return;
    }
    // Признак возвращается к прежнему значению, а не гасится: круги по одной переменной
    // могут запуститься из кругов по другой, и снятие признака открыло бы внешним кругам
    // дорогу запускать себя заново.
    var outerComputing = session.cellsComputing;
    session.cellsComputing = true;
    try {
      for (var variable : pending) {
        session.cells.put(variable, inputs.declaredFact().apply(variable));
      }
      grow(documentContext, pending, inputs);
      if (inputs.cacheable() && !inputs.sawMissing().getAsBoolean()) {
        pending.forEach(variable -> settled.putIfAbsent(variable, session.cells.get(variable)));
      }
    } finally {
      session.cellsComputing = outerComputing;
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
        if (inputs.compilationContexts().apply(body).map(Set::isEmpty).orElse(false)) {
          continue;
        }
        // Окружения не запоминаются: они посчитаны по промежуточному приближению входа
        // и через круг устареют.
        var facts = compute(documentContext, body, layoutOf(documentContext, body), inputs);
        grown |= growCells(shared, session, facts);
      }
      if (!grown) {
        break;
      }
    }
  }

  /** Добавить выходные типы тела к текущим приближениям общих ячеек. */
  private static boolean growCells(List<VariableSymbol> shared, FlowSession session, Facts facts) {
    var grown = false;
    for (var variable : shared) {
      var index = facts.indexOf(variable);
      if (index == null) {
        continue;
      }
      var united = session.cells.get(variable).union(facts.leaving().get(index));
      grown |= !united.equals(session.cells.put(variable, united));
    }
    return grown;
  }

  /** Тела, в которых эти переменные меняются, — по одному на тело и без повторов. */
  private List<BSLParser.CodeBlockContext> changedBodiesOf(
    DocumentContext documentContext,
    List<VariableSymbol> shared,
    FlowInputs inputs
  ) {
    // Тела сравниваются по ссылке: узлы дерева разбора равенства по содержимому не имеют, —
    // и идут в порядке, в каком встретились. Порядок кругов на неподвижную точку влиять не
    // должен, но набор с хешами по идентичности менял бы его от запуска к запуску, и любая
    // зависимость от порядка превращалась бы в невоспроизводимый результат.
    Set<BSLParser.CodeBlockContext> bodies = new LinkedHashSet<>();
    for (var variable : shared) {
      var changes = changesOf(documentContext, variable, inputs);
      Stream.concat(changes.definitions().stream(), changes.mutations().stream())
        .map(position -> bodyAt(documentContext, position))
        .filter(Objects::nonNull)
        .forEach(bodies::add);
    }
    return List.copyOf(bodies);
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
    contextualFactsByUri.remove(uri);
    cellsByUri.remove(uri);
    contextualCellsByUri.remove(uri);
    changesByUri.remove(uri);
  }

  /** Изменение общих режимов обесценивает типы, но не раскладку неизменённого AST. */
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @EventListener
  public void handleConfigurationChanged(LanguageServerConfigurationChangedEvent event) {
    factsByUri.clear();
    contextualFactsByUri.clear();
    cellsByUri.clear();
    contextualCellsByUri.clear();
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
    var before = facts.beforeStatement().getOrDefault(useStatement, FlowEnvironment.EMPTY).get(index);
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
   * Рассчитанные по телу окружения — из кеша выбранного режима либо посчитанные на месте.
   * Окружения не зависят от точки запроса, поэтому одного расчёта хватает на все обращения
   * ко всем переменным тела в этом режиме.
   * <p>
   * Запись разрешена только для самостоятельного расчёта с полными исходными данными
   * и завершёнными ячейками переменных модуля. Промежуточный результат возвращается
   * текущему запросу, но не публикуется для следующих запросов.
   */
  private Facts factsOf(
    DocumentContext documentContext,
    BSLParser.CodeBlockContext body,
    FlowLayout layout,
    FlowInputs inputs
  ) {
    Map<BSLParser.CodeBlockContext, Facts> byBody = null;
    if (inputs.cacheable()) {
      byBody = inputs.executionContext() == null
        ? factsByUri.computeIfAbsent(documentContext.getUri(), uri -> new ConcurrentHashMap<>())
        : contextualFactsByUri.computeIfAbsent(documentContext.getUri(), uri -> new ConcurrentHashMap<>())
          .computeIfAbsent(inputs.executionContext(), context -> new ConcurrentHashMap<>());
    }
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
    // Посреди кругов по ячейкам вход переменных модуля — промежуточное приближение, как и в
    // самих кругах (см. grow). Сюда такой расчёт попадает изнутри них: тело, меняющее
    // переменную, вызывает функцию того же модуля, и за её значением считается её тело.
    // Запомненное, оно пережило бы круги и отвечало бы приближением всем, в том числе
    // запросу, с которого круги и начались. Так же не запоминается окружение, посчитанное
    // на значениях, которых ещё нет: следующий расчёт принял бы его за полное.
    if (byBody == null || inputs.session().cellsComputing || inputs.sawMissing().getAsBoolean()) {
      return computed;
    }
    var previous = byBody.putIfAbsent(body, computed);
    return previous == null ? computed : previous;
  }

  /**
   * Места изменения переменной по всему документу: присваивания и операторы-мутаторы.
   *
   * @param documentContext контекст документа.
   * @param variable        переменная.
   * @param inputs          исходные данные расчёта.
   * @return места изменения; спрашиваются у вызывающего один раз на переменную.
   */
  private Changes changesOf(DocumentContext documentContext, VariableSymbol variable, FlowInputs inputs) {
    return changesByUri
      .get(documentContext.getUri(), documentContext.getSymbolTreeNoLock(), tree -> new ConcurrentHashMap<>())
      .computeIfAbsent(variable, key -> new Changes(
        inputs.definitionPositions().apply(key),
        inputs.mutationPositions().apply(key)));
  }

  /**
   * Места изменения одной переменной.
   *
   * @param definitions позиции присваиваний.
   * @param mutations   позиции операторов, меняющих тип на месте.
   */
  record Changes(Collection<Position> definitions, Collection<Position> mutations) {
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
    var variables = FlowVariables.collect(body, layout, inputs,
      variable -> changesOf(documentContext, variable, inputs));
    if (variables.variables().isEmpty()) {
      return new Facts(Map.of(), Map.of(), Map.of(), FlowEnvironment.EMPTY);
    }
    var graph = controlFlowGraphIndex.graphOf(documentContext, body, CfgBuildOptions.defaults());
    var compiled = inputs.compilationContexts().apply(body)
      .map(contexts -> PreprocessorFlow.of(graph, contexts)).orElse(null);
    var pass = new VariableFlowPass(this, documentContext, graph, layout, inputs, compiled, variables);
    var active = inputs.session().active;
    active.put(body, pass);
    try {
      return pass.computeFacts();
    } finally {
      active.remove(body);
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
  static BSLParser.@Nullable CodeBlockContext bodyAt(DocumentContext documentContext, Position position) {
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
    // Тело модуля в дереве может быть не одно: код до объявлений методов (так пишут на
    // OneScript) и после них (так требует 1С). Отдаём блок, только если он правда
    // накрывает позицию: иначе расчёт пошёл бы по чужому телу.
    var beforeSubs = ast.fileCodeBlockBeforeSub();
    var covering = covering(beforeSubs == null ? null : beforeSubs.codeBlock(), position);
    if (covering != null) {
      return covering;
    }
    var fileCodeBlock = ast.fileCodeBlock();
    return covering(fileCodeBlock == null ? null : fileCodeBlock.codeBlock(), position);
  }

  /** Блок кода, если он есть и накрывает позицию. */
  private static BSLParser.@Nullable CodeBlockContext covering(
    BSLParser.@Nullable CodeBlockContext codeBlock,
    Position position
  ) {
    return codeBlock != null && Ranges.containsPosition(Ranges.create(codeBlock), position) ? codeBlock : null;
  }
}
