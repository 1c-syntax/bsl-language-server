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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.index.CallStatementByReceiverIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.LocalField;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ConstructorCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Поля «открытых» объектов данных — тех, у кого состав членов складывается в коде, а не
 * объявлен заранее: {@code Структура}, {@code ФиксированнаяСтруктура}, {@code Соответствие},
 * {@code ФиксированноеСоответствие} и {@code ТаблицаЗначений}.
 * <p>
 * Знает два источника таких полей и больше ничего:
 * <ul>
 *   <li><b>конструктор</b> — {@code Новый Структура("К1, К2", З1, З2)} задаёт ключи с
 *   типами значений, {@code Новый ОписаниеТипов("Число, Строка")} задаёт содержимое
 *   описания типов;</li>
 *   <li><b>оператор-мутатор</b> — {@code Х.Вставить("Имя", Значение)} добавляет поле,
 *   {@code Х.Колонки.Добавить("Имя", Тип)} добавляет колонку.</li>
 * </ul>
 * Соответствует стандарту типизации кода 1С: значения, присвоенные ключам, задают тип
 * объекта. Про вывод типов не знает — типы выражений отдаёт колбэк {@link ExpressionTypes}.
 */
@Component
@RequiredArgsConstructor
public class OpenDataObjectInference {

  private static final String INSERT_RU = "Вставить";
  private static final String INSERT_EN = "Insert";
  private static final String ADD_RU = "Добавить";
  private static final String ADD_EN = "Add";
  private static final String COLUMNS_RU = "Колонки";
  private static final String COLUMNS_EN = "Columns";
  private static final String ADJUST_VALUE_RU = "ПривестиЗначение";
  private static final String ADJUST_VALUE_EN = "AdjustValue";

  /** Тип ключа без значения: {@code Новый Структура("Ключ")} и {@code Вставить("Ключ")}. */
  private static final TypeSet UNDEFINED = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Неопределено"));

  private final TypeRegistry typeRegistry;
  private final CallStatementByReceiverIndex callStatementByReceiverIndex;

  /**
   * Типы выражения — то, чего этот компонент сам не умеет.
   */
  @FunctionalInterface
  public interface ExpressionTypes {

    /**
     * Типы выражения.
     *
     * @param expression узел выражения.
     * @return типы; пустой набор, если вывести их не удалось.
     */
    TypeSet of(BslExpression expression);
  }

  /**
   * Имя и типы поля либо колонки, добавляемых одним оператором.
   *
   * @param name  имя ключа или колонки.
   * @param types типы значения или колонки.
   */
  private record KeyedTypes(String name, TypeSet types) {
  }

  /**
   * Складывается ли состав полей у этого типа в коде: Структура и ФиксированнаяСтруктура.
   *
   * @param typeName имя типа.
   * @return {@code true} для структуроподобных типов.
   */
  public static boolean isStructureLike(String typeName) {
    var lower = typeName.toLowerCase(Locale.ROOT);
    return lower.equals("структура") || lower.equals("structure")
      || lower.equals("фиксированнаяструктура") || lower.equals("fixedstructure");
  }

  /**
   * Платформенные коллекции «ключ-значение», у которых {@code .Вставить("Имя", Значение)}
   * даёт строковый ключ с типом значения. Сюда же входят структуроподобные типы.
   *
   * @param typeName имя типа.
   * @return {@code true} для структур и соответствий.
   */
  public static boolean isStructureOrMapLike(String typeName) {
    if (isStructureLike(typeName)) {
      return true;
    }
    var lower = typeName.toLowerCase(Locale.ROOT);
    return lower.equals("соответствие") || lower.equals("map")
      || lower.equals("фиксированноесоответствие") || lower.equals("fixedmap");
  }

  /**
   * Таблица значений — объект, у которого состав колонок складывается в коде.
   *
   * @param typeName имя типа.
   * @return {@code true} для таблицы значений.
   */
  public static boolean isValueTableLike(String typeName) {
    var lower = typeName.toLowerCase(Locale.ROOT);
    return lower.equals("таблицазначений") || lower.equals("valuetable");
  }

  /**
   * Описание типов — объект, чьё содержимое задаётся строкой в конструкторе.
   *
   * @param typeName имя типа.
   * @return {@code true} для описания типов.
   */
  public static boolean isTypeDescriptionType(String typeName) {
    return "ОписаниеТипов".equalsIgnoreCase(typeName) || "TypeDescription".equalsIgnoreCase(typeName);
  }

  /**
   * Текст строкового литерала выражения без кавычек.
   *
   * @param node узел выражения.
   * @return текст литерала; {@code null}, если выражение литералом не является.
   */
  @Nullable
  public static String stringLiteralOf(BslExpression node) {
    var ast = node.getRepresentingAst();
    if (ast == null) {
      return null;
    }
    var trimmed = ast.getText().trim();
    if (trimmed.length() >= 2
      && (trimmed.charAt(0) == '"' || trimmed.charAt(0) == '\'')
      && trimmed.charAt(0) == trimmed.charAt(trimmed.length() - 1)) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return null;
  }

  /**
   * Поля структуры из конструктора {@code Новый Структура("К1, К2", З1, З2)}: каждому ключу
   * достаётся тип соответствующего по порядку значения, а ключам без значения —
   * {@code Неопределено}.
   *
   * @param base        тип, к которому крепятся поля.
   * @param constructor узел конструктора.
   * @param types       колбэк вывода типов значений.
   * @return тип с полями; исходный, если ключи не заданы литералом.
   */
  public TypeSet applyConstructorKeys(TypeSet base, ConstructorCallNode constructor, ExpressionTypes types) {
    var args = constructor.arguments();
    if (args.isEmpty() || base.refs().isEmpty()) {
      return base;
    }
    var keyLiteral = stringLiteralOf(args.get(0));
    if (keyLiteral == null) {
      return base;
    }
    var keys = keyLiteral.split(",");
    // Поля собираются и добавляются разом: набор типов неизменяем, и добавление по одному
    // копировало бы накопленную карту полей на каждый ключ, а ключей в конструкторе бывает
    // много.
    Map<String, LocalField> fields = new LinkedHashMap<>();
    for (var i = 0; i < keys.length; i++) {
      var keyName = keys[i].trim();
      if (keyName.isEmpty()) {
        continue;
      }
      var valueArgIndex = i + 1;
      var valueTypes = valueArgIndex < args.size()
        ? types.of(args.get(valueArgIndex))
        : UNDEFINED;
      if (!valueTypes.isEmpty()) {
        fields.merge(keyName, LocalField.of(valueTypes), LocalField::merge);
      }
    }
    return base.withFields(base.refs().iterator().next(), fields);
  }

  /**
   * Содержимое описания типов из конструктора {@code Новый ОписаниеТипов("Число, Строка")}:
   * имена типов разбираются и подвешиваются к описанию как типы элементов. Оттуда их берёт
   * добавление колонки, не разбирая дерево заново.
   *
   * @param base        тип описания типов.
   * @param constructor узел конструктора.
   * @param fileType    язык, на котором резолвятся имена типов.
   * @return тип с содержимым; исходный, если имена не заданы литералом.
   */
  public TypeSet applyTypeDescriptionTypes(TypeSet base, ConstructorCallNode constructor, FileType fileType) {
    var args = constructor.arguments();
    if (args.isEmpty() || base.refs().isEmpty()) {
      return base;
    }
    var literal = stringLiteralOf(args.get(0));
    if (literal == null) {
      return base;
    }
    var refs = new ArrayList<TypeRef>();
    for (var raw : literal.split(",")) {
      var name = raw.trim();
      if (!name.isEmpty()) {
        typeRegistry.resolve(name, fileType).ifPresent(refs::add);
      }
    }
    if (refs.isEmpty()) {
      return base;
    }
    return base.withElement(base.refs().iterator().next(), TypeSet.of(refs));
  }

  /**
   * Типы значения, приведённого описанием типов: {@code ОписаниеЧисла.ПривестиЗначение(Х)}
   * даёт значение ровно тех типов, что описаны.
   * <p>
   * Платформа объявляет возврат как {@code Произвольный}, потому что в общем случае состав
   * описания неизвестен. Но если описание собрано литеральным конструктором, состав уже
   * разобран и лежит в типах элементов — оттуда и берём. Так после
   * {@code ТипЧисло = Новый ОписаниеТипов("Число")} обращение
   * {@code ТипЧисло.ПривестиЗначение(Строка).Округлить(2)} видит число, а не «произвольный».
   *
   * @param receiver   типы получателя.
   * @param memberName имя члена.
   * @return типы приведённого значения; {@code null}, если правило неприменимо и нужен
   *     общий путь.
   */
  @Nullable
  public TypeSet adjustedValueTypes(TypeSet receiver, String memberName) {
    if (!ADJUST_VALUE_RU.equalsIgnoreCase(memberName) && !ADJUST_VALUE_EN.equalsIgnoreCase(memberName)) {
      return null;
    }
    for (var ref : receiver.refs()) {
      if (isTypeDescriptionType(ref.qualifiedName())) {
        var described = receiver.getElementTypes(ref);
        if (!described.isEmpty()) {
          return described;
        }
      }
    }
    return null;
  }

  /**
   * Операторы, меняющие состав полей переменной на месте, по позиции их начала.
   *
   * @param variable переменная-получатель.
   * @return операторы по позициям, в порядке следования в документе.
   */
  public Map<Position, BSLParser.CallStatementContext> mutatorsOf(VariableSymbol variable) {
    var owner = variable.getOwner();
    var ast = safeGetAst(owner);
    if (ast == null) {
      return Map.of();
    }
    Map<Position, BSLParser.CallStatementContext> calls = new LinkedHashMap<>();
    for (var call : callStatementByReceiverIndex.byReceiver(owner.getUri(), ast, variable.getName())) {
      calls.put(Ranges.create(call).getStart(), call);
    }
    return calls;
  }

  /**
   * Применить к типу переменной одно изменение на месте.
   * <p>
   * Поля структуры и соответствия кладутся прямо на тип-получатель: значение, присвоенное
   * ключу, задаёт тип поля.
   * <p>
   * Колонки таблицы значений кладутся не на саму таблицу, а на тип её строки
   * ({@code СтрокаТаблицыЗначений}), привязанный к таблице через {@link TypeSet#withElement}.
   * Поэтому после {@code Для Каждого Строка Из ТЗ} или {@code ТЗ[0]} обращение
   * {@code Строка.Имя} видно как поле известного типа.
   *
   * @param variable переменная-получатель.
   * @param call     оператор-мутатор; {@code null}, если по позиции ничего не нашлось.
   * @param incoming тип переменной перед оператором.
   * @param types    колбэк вывода типов значений.
   * @return изменённый тип; исходный, если оператор к этому типу неприменим.
   */
  public TypeSet apply(
    VariableSymbol variable,
    BSLParser.@Nullable CallStatementContext call,
    TypeSet incoming,
    ExpressionTypes types
  ) {
    if (call == null || incoming.isEmpty()) {
      return incoming;
    }
    var scope = variable.getScope();
    var scopeRange = scope == null ? null : scope.getRange();
    var variableName = variable.getName();

    var structureRef = headRefOf(incoming, OpenDataObjectInference::isStructureOrMapLike);
    if (structureRef != null) {
      var field = insertedField(call, variableName, scopeRange, types);
      if (field != null && !field.types().isEmpty()) {
        return incoming.withField(structureRef, field.name(), field.types());
      }
    }
    var tableRef = headRefOf(incoming, OpenDataObjectInference::isValueTableLike);
    if (tableRef != null) {
      var column = addedColumn(call, variableName, scopeRange, types);
      if (column != null) {
        var rowRef = valueTableRowRef(variable.getOwner());
        return incoming.withElement(tableRef, TypeSet.of(rowRef).withField(rowRef, column.name(), column.types()));
      }
    }
    return incoming;
  }

  /**
   * Применить все изменения на месте по всей области видимости переменной — без учёта
   * порядка, поэтому поле видно по всей области видимости, а не с места своей вставки.
   *
   * @param variable переменная-получатель.
   * @param base     тип, накопленный по присваиваниям.
   * @param types    колбэк вывода типов значений.
   * @return тип с полями и колонками; исходный, если изменений на месте нет.
   */
  public TypeSet applyAll(VariableSymbol variable, TypeSet base, ExpressionTypes types) {
    var result = base;
    for (var call : mutatorsOf(variable).values()) {
      result = apply(variable, call, result, types);
    }
    return result;
  }

  /**
   * Типы поля, записанного на наборе, — по имени.
   * <p>
   * Смотрится раньше членов самого типа: поле, объявленное автором в коде, точнее
   * одноимённого члена платформы.
   *
   * @param types      набор типов получателя.
   * @param fieldName  имя поля; регистр не важен.
   * @return типы поля; пустой набор, если такого поля не записано.
   */
  public static TypeSet fieldTypes(TypeSet types, String fieldName) {
    var result = TypeSet.EMPTY;
    for (var ref : types.refs()) {
      for (var field : types.getLocalFields(ref).entrySet()) {
        if (field.getKey().equalsIgnoreCase(fieldName)) {
          result = result.union(field.getValue().types());
        }
      }
    }
    return result;
  }

  /**
   * Все поля, записанные на наборе, — по всем его типам разом.
   * <p>
   * Нужно обращению по ключу ({@code Структура["Ключ"]}): по литеральному ключу берётся
   * своё поле, по вычисляемому — объединение всех.
   *
   * @param types набор типов получателя.
   * @return типы по именам полей; пустая карта, если полей не записано.
   */
  public static Map<String, TypeSet> fieldsOf(TypeSet types) {
    var merged = new LinkedHashMap<String, TypeSet>();
    for (var ref : types.refs()) {
      for (var field : types.getLocalFields(ref).entrySet()) {
        merged.merge(field.getKey(), field.getValue().types(), TypeSet::union);
      }
    }
    return merged;
  }

  /** Первый тип набора, подходящий под условие. */
  @Nullable
  private static TypeRef headRefOf(TypeSet types, Predicate<String> predicate) {
    for (var ref : types.refs()) {
      if (predicate.test(ref.qualifiedName())) {
        return ref;
      }
    }
    return null;
  }

  /** Тип строки таблицы значений — на нём моделируются колонки. */
  private TypeRef valueTableRowRef(DocumentContext owner) {
    return typeRegistry.resolve(TableCollectionInference.VALUE_TABLE_ROW, owner.getFileType())
      .orElseGet(() -> typeRegistry.intern(TypeKind.PLATFORM, TableCollectionInference.VALUE_TABLE_ROW));
  }

  /** Поле, добавляемое вызовом {@code Х.Вставить("Ключ", Значение)}. */
  @Nullable
  private KeyedTypes insertedField(
    BSLParser.CallStatementContext call,
    String variableName,
    @Nullable Range scopeRange,
    ExpressionTypes types
  ) {
    var params = mutatorParams(
      call, variableName, scopeRange, insertReceiverName(call), OpenDataObjectInference::isInsertMethod);
    if (params == null) {
      return null;
    }
    var keyName = literalKeyOf(params);
    if (keyName == null) {
      return null;
    }
    TypeSet valueTypes;
    if (params.size() >= 2 && params.get(1).expression() != null) {
      var valueExpr = ExpressionTreeBuildingVisitor.buildExpressionTree(params.get(1).expression());
      valueTypes = valueExpr == null ? TypeSet.EMPTY : types.of(valueExpr);
    } else {
      valueTypes = UNDEFINED;
    }
    return new KeyedTypes(keyName, valueTypes);
  }

  /** Колонка, добавляемая вызовом {@code Х.Колонки.Добавить("Имя", Тип)}. */
  @Nullable
  private KeyedTypes addedColumn(
    BSLParser.CallStatementContext call,
    String variableName,
    @Nullable Range scopeRange,
    ExpressionTypes types
  ) {
    var params = mutatorParams(
      call, variableName, scopeRange, columnsAddReceiverName(call), OpenDataObjectInference::isAddMethod);
    if (params == null) {
      return null;
    }
    var keyName = literalKeyOf(params);
    if (keyName == null) {
      return null;
    }
    // Второй аргумент по сигнатуре платформы — объект ОписаниеТипов. Его содержимое уже
    // разобрано конструктором и лежит в типах элементов; любое другое выражение содержимого
    // не даст, и колонка останется Неопределено.
    var typeArg = params.size() >= 2 ? params.get(1).expression() : null;
    var columnTypes = typeArg == null ? TypeSet.EMPTY : describedTypes(typeArg, types);
    return new KeyedTypes(keyName, columnTypes.isEmpty() ? UNDEFINED : columnTypes);
  }

  /** Имя ключа или колонки из первого аргумента — только строковым литералом. */
  @Nullable
  private static String literalKeyOf(List<? extends BSLParser.CallParamContext> params) {
    var keyName = Optional.ofNullable(params.get(0).expression())
      .map(ExpressionTreeBuildingVisitor::buildExpressionTree)
      .map(OpenDataObjectInference::stringLiteralOf)
      .orElse(null);
    return keyName == null || keyName.isBlank() ? null : keyName.trim();
  }

  /** Содержимое описания типов, если выражение к нему сводится. */
  private TypeSet describedTypes(BSLParser.ExpressionContext expression, ExpressionTypes types) {
    var expressionTree = ExpressionTreeBuildingVisitor.buildExpressionTree(expression);
    if (expressionTree == null) {
      return TypeSet.EMPTY;
    }
    var inferred = types.of(expressionTree);
    for (var ref : inferred.refs()) {
      if (isTypeDescriptionType(ref.qualifiedName())) {
        var elementTypes = inferred.getElementTypes(ref);
        if (!elementTypes.isEmpty()) {
          return elementTypes;
        }
      }
    }
    return TypeSet.EMPTY;
  }

  /**
   * Аргументы вызова, если он и правда меняет состав полей нужной переменной: получатель
   * совпадает по имени, вызов стоит в области её видимости, метод — тот самый.
   */
  @Nullable
  private static List<? extends BSLParser.CallParamContext> mutatorParams(
    BSLParser.CallStatementContext call,
    String receiverName,
    @Nullable Range scopeRange,
    @Nullable String actualReceiver,
    Predicate<BSLParser.MethodCallContext> methodMatches
  ) {
    if (actualReceiver == null || !actualReceiver.equalsIgnoreCase(receiverName)) {
      return null;
    }
    if (scopeRange != null && !Ranges.containsRange(scopeRange, Ranges.create(call))) {
      return null;
    }
    var methodCall = call.accessCall() == null ? null : call.accessCall().methodCall();
    if (methodCall == null || !methodMatches.test(methodCall)) {
      return null;
    }
    var paramList = methodCall.doCall() == null ? null : methodCall.doCall().callParamList();
    if (paramList == null) {
      return null;
    }
    var params = paramList.callParam();
    return params.isEmpty() ? null : params;
  }

  /** Получатель записи {@code Х.Вставить(…)} — без промежуточных обращений к свойствам. */
  @Nullable
  private static String insertReceiverName(BSLParser.CallStatementContext call) {
    var identifier = call.IDENTIFIER();
    if (identifier == null || !call.modifier().isEmpty() || call.accessCall() == null) {
      return null;
    }
    return identifier.getText();
  }

  /** Получатель записи {@code Х.Колонки.Добавить(…)} — ровно через свойство «Колонки». */
  @Nullable
  private static String columnsAddReceiverName(BSLParser.CallStatementContext call) {
    var identifier = call.IDENTIFIER();
    if (identifier == null) {
      return null;
    }
    var modifiers = call.modifier();
    if (modifiers.size() != 1) {
      return null;
    }
    var property = modifiers.get(0).accessProperty();
    if (property == null || property.IDENTIFIER() == null) {
      return null;
    }
    var propertyName = property.IDENTIFIER().getText();
    if (!COLUMNS_RU.equalsIgnoreCase(propertyName) && !COLUMNS_EN.equalsIgnoreCase(propertyName)) {
      return null;
    }
    return call.accessCall() == null ? null : identifier.getText();
  }

  private static boolean isInsertMethod(BSLParser.MethodCallContext methodCall) {
    return isMethodNamed(methodCall, INSERT_RU, INSERT_EN);
  }

  private static boolean isAddMethod(BSLParser.MethodCallContext methodCall) {
    return isMethodNamed(methodCall, ADD_RU, ADD_EN);
  }

  private static boolean isMethodNamed(BSLParser.MethodCallContext methodCall, String russian, String english) {
    var nameCtx = methodCall.methodName();
    if (nameCtx == null) {
      return false;
    }
    var text = nameCtx.getText();
    return russian.equalsIgnoreCase(text) || english.equalsIgnoreCase(text);
  }

  /**
   * Дерево разбора документа; {@code null}, если оно ещё не построено. Обращение к
   * недостроенному дереву падает, а состав полей — уточнение, без которого можно обойтись.
   */
  private static BSLParser.@Nullable FileContext safeGetAst(DocumentContext owner) {
    try {
      return owner.getAst();
    } catch (NullPointerException e) {
      return null;
    }
  }
}
