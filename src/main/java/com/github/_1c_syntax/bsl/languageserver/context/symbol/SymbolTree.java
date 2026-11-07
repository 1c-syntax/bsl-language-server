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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Символьное дерево документа. Содержит все символы документа, вложенные друг в друга по принципу родитель -&gt; дети
 */
@Value
public class SymbolTree {
  private static final int MIN_ORDERED_CHILDREN = 8;

  /**
   * Корневой символ модуля документа.
   */
  ModuleSymbol module;

  Basis basis;

  /**
   * Условия препроцессора по диапазону имени метода в этой версии дерева.
   * Отсутствующий ключ означает все контексты, значение {@code null} — неизвестное условие,
   * пустое множество — недостижимую ветку. Диапазоны и множества не удерживают AST.
   */
  @Getter(AccessLevel.NONE)
  // Range — сторонний тип LSP; здесь требуется равенство диапазонов, без порядка ключей.
  Map<Range, Set<ExecutionContext>> methodConditions;

  /**
   * Список всех символов всех уровней (за исключением символа модуля документа), преобразованных в плоский список.
   */
  @Getter(lazy = true)
  List<SourceDefinedSymbol> childrenFlat = createChildrenFlat();

  /**
   * Список методов документа.
   */
  @Getter(lazy = true)
  List<MethodSymbol> methods = createMethods();

  /**
   * Методы, упорядоченные по имени без учёта регистра; из одноимённых — первый объявленный.
   * Массив, а не дерево поиска: деревьев символов на большой конфигурации десятки тысяч,
   * и узел на каждый метод обходился бы в десятки мегабайт.
   */
  @Getter(value = AccessLevel.PRIVATE, lazy = true)
  MethodSymbol[] methodsByName = createMethodsByName();

  /**
   * Конструктор OneScript-класса ({@code ПриСозданииОбъекта} / {@code OnObjectCreate}),
   * если он объявлен в документе.
   */
  @Getter(lazy = true)
  Optional<ConstructorSymbol> constructor = createConstructor();

  /**
   * Плоский список всех переменных документа
   */
  @Getter(lazy = true)
  List<VariableSymbol> variables = createVariables();

  /**
   * Переменные по области объявления: в каждой области упорядочены по имени без учёта
   * регистра, из одноимённых — первая объявленная.
   */
  @Getter(value = AccessLevel.PRIVATE, lazy = true)
  Map<SourceDefinedSymbol, VariableSymbol[]> variablesByScope = createVariablesByScope();

  /**
   * Индекс символов по строке их {@code selectionRange} (диапазона имени в
   * объявлении) для быстрого поиска «символ, чьё имя стоит в позиции».
   * Заменяет линейный скан {@link #getChildrenFlat()} в
   * {@code SourceDefinedSymbolDeclarationReferenceFinder} на двоичный поиск по
   * строке. На одной строке может быть несколько объявлений (разные колонки) —
   * их разводит {@link Ranges#containsPosition}. Внутри строки порядок — как в
   * {@code getChildrenFlat()} (pre-order DFS), чтобы сохранить tie-break
   * {@code findFirst} прежнего скана.
   */
  @Getter(value = AccessLevel.PRIVATE, lazy = true)
  SelectionLines symbolsBySelectionLine = createSymbolsBySelectionLine();

  /** Области, чьи дети упорядочены по обеим границам; порядок исходных символов сохраняется. */
  @Getter(value = AccessLevel.PRIVATE, lazy = true)
  Set<SourceDefinedSymbol> positionOrderedParents = createPositionOrderedParents();

  /**
   * На чём, кроме текста документа, построено дерево: привязка документа к метаданным и голые
   * имена, признанные при построении реквизитами своего типа. Символов у таких имён нет, поэтому
   * из самого дерева их не восстановить.
   *
   * @param moduleType         тип модуля документа.
   * @param mdoRef             ссылка на объект метаданных документа.
   * @param bareSelfProperties голые имена, признанные реквизитами своего типа.
   * @param loopVariables      переменные, впервые объявленные заголовком цикла.
   */
  public record Basis(ModuleType moduleType, String mdoRef, Set<String> bareSelfProperties,
                      Set<VariableSymbol> loopVariables) {
    /**
     * Основание дерева, построенного не по документу: годным для документа такое дерево не бывает.
     * Узнаётся по ссылке, а не по значениям полей.
     */
    public static final Basis NONE = new Basis(ModuleType.UNKNOWN, "", Set.of());

    public Basis {
      bareSelfProperties = Set.copyOf(bareSelfProperties);
      loopVariables = Set.copyOf(loopVariables);
    }

    public Basis(ModuleType moduleType, String mdoRef, Set<String> bareSelfProperties) {
      this(moduleType, mdoRef, bareSelfProperties, Set.of());
    }

  }

  /**
   * Символы, упорядоченные по строке {@code selectionRange}: {@code symbols[i]} объявлен на
   * строке {@code lines[i]}. Два массива вместо карты «строка → список»: на большой
   * конфигурации у деревьев миллионы символов, а карта платила за каждый узлом, ключом и списком.
   */
  private static final class SelectionLines {
    /** Строки, по возрастанию. */
    private final int[] lines;
    /** Символы тех же позиций. */
    private final SourceDefinedSymbol[] symbols;

    private SelectionLines(int[] lines, SourceDefinedSymbol[] symbols) {
      this.lines = lines;
      this.symbols = symbols;
    }
  }

  /**
   * Создать дерево без ограничений препроцессора для объявлений методов.
   *
   * @param module корневой символ документа.
   */
  public SymbolTree(ModuleSymbol module) {
    this(module, Map.of());
  }

  /**
   * Создать дерево с сохранёнными условиями объявлений методов.
   * <p>
   * Карта, её ключи и множества используются без защитного копирования.
   * Вызывающий код должен обеспечить их неизменность в течение жизни дерева.
   *
   * @param module корневой символ документа.
   * @param methodConditions условия по диапазонам имён методов этой версии документа;
   *     значения {@code null} допустимы и означают неизвестное условие.
   */
  public SymbolTree(ModuleSymbol module, Map<Range, Set<ExecutionContext>> methodConditions) {
    this(module, methodConditions, Basis.NONE);
  }

  public SymbolTree(ModuleSymbol module, Basis basis) {
    this(module, Map.of(), basis);
  }

  /** Основание классификации и условия объявлений относятся к одной версии дерева. */
  public SymbolTree(ModuleSymbol module, Map<Range, Set<ExecutionContext>> methodConditions, Basis basis) {
    this.module = module;
    this.methodConditions = methodConditions;
    this.basis = basis;
  }

  /**
   * Получить контексты ветки препроцессора, в которой объявлен метод.
   * <p>
   * Доступно после выгрузки AST. Контексты модуля, директивы компиляции и настройки
   * диагностики не учитываются. Возвращённое множество изменять нельзя.
   *
   * @param method метод из этой версии символьного дерева.
   * @return все контексты при отсутствии ограничения, пустое множество для недостижимой
   *     ветки или {@code null}, если условие нельзя вычислить.
   */
  public @Nullable Set<ExecutionContext> getMethodPreprocessorContexts(MethodSymbol method) {
    return methodConditions.getOrDefault(method.getSubNameRange(), ExecutionContext.ALL);
  }

  /**
   * @return Список символов верхнего уровня за исключением символа модуля документа.
   */
  public List<SourceDefinedSymbol> getChildren() {
    return module.getChildren();
  }

  /**
   * Список всех символов всех уровней указанного типа (за исключением символа модуля документа),
   * преобразованных в плоский список.
   *
   * @param clazz класс искомого символа.
   * @param <T>   тип искомого символа.
   * @return плоский список символов указанного типа.
   */
  public <T> List<T> getChildrenFlat(Class<T> clazz) {
    return getChildrenFlat().stream()
      .filter(clazz::isInstance)
      .map(clazz::cast)
      .collect(Collectors.toList());
  }

  /**
   * @return Список областей, расположенных на верхнем уровне документа.
   */
  public List<RegionSymbol> getModuleLevelRegions() {
    return getChildren().stream()
      .filter(RegionSymbol.class::isInstance)
      .map(RegionSymbol.class::cast)
      .collect(Collectors.toList());
  }

  /**
   * @return плоский список всех областей документа.
   */
  public List<RegionSymbol> getRegionsFlat() {
    return getChildrenFlat(RegionSymbol.class);
  }

  /**
   * Попытка поиска символа метода по узлу дерева разбора.
   * <p>
   * Implementation note - Поиск осуществляется по месту определения метода (declaration).
   *
   * @param ctx узел дерева разбора документа.
   * @return найденный символ метода.
   */
  public Optional<MethodSymbol> getMethodSymbol(ParserRuleContext ctx) {
    return SymbolTreeLookup.methodByNode(this, ctx);
  }

  /**
   * Поиск MethodSymbol в дереве по указанному имени (без учета регистра).
   *
   * @param methodName Имя метода
   * @return MethodSymbol, если он был найден в дереве символов.
   */
  public Optional<MethodSymbol> getMethodSymbol(String methodName) {
    return findByName(getMethodsByName(), methodName);
  }

  /**
   * Попытка поиска символа переменной по узлу дерева разбора.
   * <p>
   * Implementation note Поиск осуществляется по месту определения переменной (declaration).
   *
   * @param ctx узел дерева разбора документа.
   * @return найденный символ переменной.
   */
  public Optional<VariableSymbol> getVariableSymbol(ParserRuleContext ctx) {
    return SymbolTreeLookup.variableByNode(this, ctx);
  }

  /**
   * Поиск VariableSymbol в дереве по указанному имени (без учета регистра) и области объявления.
   *
   * @param variableName Имя переменной
   * @param scopeSymbol  Символ, внутри которого осуществляется поиск.
   *                     Например, {@link ModuleSymbol} или {@link MethodSymbol}.
   * @return VariableSymbol, если он был найден в дереве символов.
   */
  public Optional<VariableSymbol> getVariableSymbol(String variableName, SourceDefinedSymbol scopeSymbol) {
    var inScope = getVariablesByScope().get(scopeSymbol);
    return inScope == null ? Optional.empty() : findByName(inScope, variableName);
  }

  /**
   * Переменные, объявленные в области {@code scopeSymbol}: по одной на имя без учёта регистра
   * (первая объявленная), упорядочены по имени.
   *
   * @param scopeSymbol область объявления — {@link ModuleSymbol} или {@link MethodSymbol}.
   * @return неизменяемый список переменных; пустой, если в области их нет.
   */
  public List<VariableSymbol> getVariablesOfScope(SourceDefinedSymbol scopeSymbol) {
    var inScope = getVariablesByScope().get(scopeSymbol);
    return inScope == null ? List.of() : Collections.unmodifiableList(Arrays.asList(inScope));
  }

  /**
   * Переменная с именем {@code variableName}, видимая в точке узла {@code node}:
   * сперва — в охватывающем методе (по {@code RULE_sub}), иначе — на уровне модуля.
   * Из метода видны только переменные модуля, объявленные {@code Перем} (см.
   * {@link #getModuleVariableVisibleFromMethods(String)}). Единый резолв видимости
   * переменной для потребителей, которым нужно отличить объявленную переменную от
   * одноимённого self-члена (система типов, индексатор ссылок).
   *
   * @param node         узел, в точке которого проверяется видимость.
   * @param variableName имя переменной (без учёта регистра — как хранит дерево).
   * @return видимая переменная; empty, если такой в области видимости нет.
   */
  public Optional<VariableSymbol> getVariableSymbolInScope(ParserRuleContext node, String variableName) {
    return SymbolTreeLookup.variableInScope(this, node, variableName);
  }

  /**
   * Переменная уровня модуля, видимая из методов. Такова только переменная, объявленная
   * {@code Перем} на уровне модуля. Переменная, созданная присваиванием в теле модуля, живёт
   * в одном теле модуля, а переменные и параметры метода с ошибками разбора, оказавшиеся на
   * уровне модуля, принадлежат своему методу.
   *
   * @param variableName имя переменной (без учёта регистра — как хранит дерево).
   * @return переменная модуля; empty, если такой нет либо из методов она не видна.
   */
  public Optional<VariableSymbol> getModuleVariableVisibleFromMethods(String variableName) {
    return getVariableSymbol(variableName, module)
      .filter(variable -> variable.getKind() == VariableKind.MODULE);
  }

  /**
   * Поиск самого вложенного символа, содержащего указанную позицию.
   * <p>
   * Сохраняет порядок дочерних символов при перекрытии диапазонов. Области сами
   * не возвращаются. Упорядоченные списки детей допускают двоичный поиск первой
   * возможной границы; остальные просматриваются последовательно.
   *
   * @param position Позиция в документе.
   * @return Символ, содержащий позицию, или символ модуля, если позиция вне всех символов.
   */
  public SourceDefinedSymbol getSymbolAtPosition(Position position) {
    return findSymbolAtPosition(module, position);
  }

  /**
   * Символ, чьё имя ({@code selectionRange}) накрывает позицию — то есть позиция
   * стоит на объявлении символа. Двоичный поиск по строке через
   * {@link #getSymbolsBySelectionLine()}; при нескольких объявлениях на строке
   * возвращает первый по порядку {@code getChildrenFlat()} накрывающий символ.
   *
   * @param position позиция в документе.
   * @return символ, на объявлении которого стоит позиция, либо empty.
   */
  public Optional<SourceDefinedSymbol> findSymbolBySelectionRange(Position position) {
    var index = getSymbolsBySelectionLine();
    var lines = index.lines;
    var line = position.getLine();
    for (var i = firstAtOrAfter(lines, line); i < lines.length && lines[i] == line; i++) {
      var symbol = index.symbols[i];
      if (Ranges.containsPosition(symbol.getSelectionRange(), position)) {
        return Optional.of(symbol);
      }
    }
    return Optional.empty();
  }

  /** Первая позиция в упорядоченном {@code lines}, где строка не меньше {@code line}. */
  private static int firstAtOrAfter(int[] lines, int line) {
    var low = 0;
    var high = lines.length;
    while (low < high) {
      var mid = (low + high) >>> 1;
      if (lines[mid] < line) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    return low;
  }

  /** Символ с именем {@code name} без учёта регистра в массиве, упорядоченном по имени. */
  private static <T extends Symbol> Optional<T> findByName(T[] sortedByName, String name) {
    var low = 0;
    var high = sortedByName.length - 1;
    while (low <= high) {
      var mid = (low + high) >>> 1;
      var comparison = String.CASE_INSENSITIVE_ORDER.compare(sortedByName[mid].getName(), name);
      if (comparison < 0) {
        low = mid + 1;
      } else if (comparison > 0) {
        high = mid - 1;
      } else {
        return Optional.of(sortedByName[mid]);
      }
    }
    return Optional.empty();
  }

  private SourceDefinedSymbol findSymbolAtPosition(SourceDefinedSymbol parent, Position position) {
    var children = parent.getChildren();
    var ordered = children.size() >= MIN_ORDERED_CHILDREN && getPositionOrderedParents().contains(parent);
    var first = ordered ? firstEndingAtOrAfter(children, position) : 0;
    for (var iterator = children.listIterator(first); iterator.hasNext();) {
      var child = iterator.next();
      if (ordered && before(position, child.getRange().getStart())) {
        break;
      }
      if (child.containsPosition(position)) {
        // Рекурсивно ищем более вложенный символ
        var found = findSymbolAtPosition(child, position);
        // Пропускаем Namespace (Region) - ищем дальше или возвращаем parent
        if (found.getSymbolKind() == SymbolKind.Namespace) {
          continue;
        }
        return found;
      }
    }
    // Если среди детей не нашли — возвращаем текущий символ
    return parent;
  }

  private Set<SourceDefinedSymbol> createPositionOrderedParents() {
    Set<SourceDefinedSymbol> result = Collections.newSetFromMap(new IdentityHashMap<>());
    if (orderedRanges(module.getChildren())) {
      result.add(module);
    }
    for (var symbol : getChildrenFlat()) {
      if (orderedRanges(symbol.getChildren())) {
        result.add(symbol);
      }
    }
    return Collections.unmodifiableSet(result);
  }

  private static boolean orderedRanges(List<SourceDefinedSymbol> children) {
    if (children.size() < MIN_ORDERED_CHILDREN || !(children instanceof RandomAccess)) {
      return false;
    }
    Range previous = null;
    for (var child : children) {
      var range = child.getRange();
      if (before(range.getEnd(), range.getStart())
        || previous != null && (before(range.getStart(), previous.getStart())
          || before(range.getEnd(), previous.getEnd()))) {
        return false;
      }
      previous = range;
    }
    return true;
  }

  private static int firstEndingAtOrAfter(List<SourceDefinedSymbol> children, Position position) {
    var low = 0;
    var high = children.size();
    while (low < high) {
      var middle = (low + high) >>> 1;
      if (before(children.get(middle).getRange().getEnd(), position)) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }
    return low;
  }

  private static boolean before(Position left, Position right) {
    return left.getLine() < right.getLine()
      || left.getLine() == right.getLine() && left.getCharacter() < right.getCharacter();
  }

  private List<SourceDefinedSymbol> createChildrenFlat() {
    var symbols = new ArrayList<SourceDefinedSymbol>();
    getChildren().forEach(child -> flatten(child, symbols));
    // Список живёт столько же, сколько дерево, — без запаса, оставшегося от роста.
    symbols.trimToSize();

    return symbols;
  }

  private List<MethodSymbol> createMethods() {
    return flatOfExactSize(MethodSymbol.class);
  }

  private Optional<ConstructorSymbol> createConstructor() {
    return getChildrenFlat(ConstructorSymbol.class).stream().findFirst();
  }

  private List<VariableSymbol> createVariables() {
    return flatOfExactSize(VariableSymbol.class);
  }

  /** Символы указанного типа из плоского списка — неизменяемым списком точного размера. */
  private <T> List<T> flatOfExactSize(Class<T> clazz) {
    return getChildrenFlat().stream()
      .filter(clazz::isInstance)
      .map(clazz::cast)
      .toList();
  }

  private Map<SourceDefinedSymbol, VariableSymbol[]> createVariablesByScope() {
    Map<SourceDefinedSymbol, List<VariableSymbol>> byScope = new HashMap<>();
    for (var variable : getVariables()) {
      byScope.computeIfAbsent(variable.getScope(), scope -> new ArrayList<>()).add(variable);
    }
    Map<SourceDefinedSymbol, VariableSymbol[]> result = HashMap.newHashMap(byScope.size());
    byScope.forEach((scope, inScope) -> result.put(scope, sortedUniqueByName(inScope, new VariableSymbol[0])));
    return result;
  }

  private MethodSymbol[] createMethodsByName() {
    return sortedUniqueByName(getMethods(), new MethodSymbol[0]);
  }

  /**
   * Символы, упорядоченные по имени без учёта регистра, по одному на имя: сортировка
   * устойчивая, поэтому из одноимённых остаётся первый по порядку {@code symbols}.
   */
  private static <T extends Symbol> T[] sortedUniqueByName(List<T> symbols, T[] empty) {
    var sorted = symbols.toArray(empty);
    Arrays.sort(sorted, (left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName()));
    var unique = 0;
    for (var symbol : sorted) {
      if (unique == 0 || String.CASE_INSENSITIVE_ORDER.compare(sorted[unique - 1].getName(), symbol.getName()) != 0) {
        sorted[unique] = symbol;
        unique++;
      }
    }
    return unique == sorted.length ? sorted : Arrays.copyOf(sorted, unique);
  }

  private SelectionLines createSymbolsBySelectionLine() {
    var flat = getChildrenFlat();
    // selectionRange имени обычно однострочен, но индексируем по всем строкам,
    // которые он покрывает, — чтобы поиск оставался точной заменой линейного
    // скана даже для (редкого) многострочного диапазона.
    var count = 0;
    for (var symbol : flat) {
      var selectionRange = symbol.getSelectionRange();
      if (selectionRange != null) {
        count += selectionRange.getEnd().getLine() - selectionRange.getStart().getLine() + 1;
      }
    }
    // Ключ — строка в старших 32 битах и место в getChildrenFlat() в младших: сортировка
    // ключей упорядочивает по строке, а внутри строки сохраняет порядок обхода.
    var keys = new long[count];
    var next = 0;
    for (var i = 0; i < flat.size(); i++) {
      var selectionRange = flat.get(i).getSelectionRange();
      if (selectionRange == null) {
        continue;
      }
      for (var line = selectionRange.getStart().getLine(); line <= selectionRange.getEnd().getLine(); line++) {
        keys[next] = ((long) line << Integer.SIZE) | i;
        next++;
      }
    }
    Arrays.sort(keys);
    var lines = new int[count];
    var symbols = new SourceDefinedSymbol[count];
    for (var k = 0; k < count; k++) {
      lines[k] = (int) (keys[k] >>> Integer.SIZE);
      symbols[k] = flat.get((int) keys[k]);
    }
    return new SelectionLines(lines, symbols);
  }

  private static void flatten(SourceDefinedSymbol symbol, List<SourceDefinedSymbol> symbols) {
    symbols.add(symbol);
    symbol.getChildren().forEach(child -> flatten(child, symbols));
  }

}
