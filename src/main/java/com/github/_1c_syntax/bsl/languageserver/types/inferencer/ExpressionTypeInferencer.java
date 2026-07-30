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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredVariableTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ConstructorCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.TernaryOperatorNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.UnaryOperationNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.Lazy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Ленивый инференсер типов выражений.
 * <p>
 * Работает поверх {@link BslExpression}; устойчив к битым/неполным выражениям
 * (любая ошибка → {@link TypeSet#EMPTY} плюс {@code UNKNOWN}-семантика на
 * верхнем уровне). Защита от циклов — стек посещённых символов в
 * {@link InferenceContext}, ограниченный по глубине.
 * <p>
 * Резолв идентификаторов — через {@link ReferenceResolver}, который дёшев и
 * накапливает finder'ы из всего проекта (variable, method, module и т.д.).
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
@Slf4j
public class ExpressionTypeInferencer {

  private static final int MAX_DEPTH = 32;

  /** Методная форма индексатора: {@code Коллекция.Получить(Индекс)} — это {@code Коллекция[Индекс]}. */
  private static final String ELEMENT_GETTER = "Получить";

  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");
  private static final TypeRef BOOLEAN = new TypeRef(TypeKind.PRIMITIVE, "Булево");
  private static final TypeRef DATE = new TypeRef(TypeKind.PRIMITIVE, "Дата");
  private static final TypeRef UNDEFINED = new TypeRef(TypeKind.PRIMITIVE, "Неопределено");
  private static final TypeRef NULL = new TypeRef(TypeKind.PRIMITIVE, "Null");

  private final TypeRegistry typeRegistry;
  private final SymbolTypeIndex symbolTypeIndex;
  private final InferredVariableTypeIndex inferredVariableTypeIndex;
  private final InferredExpressionTypeIndex inferredExpressionTypeIndex;
  private final TableCollectionInference tableCollectionInference;
  private final OpenDataObjectInference openDataObjectInference;
  private final VariableCommentTypeResolver variableCommentTypeResolver;
  private final VariableFlowAnalyzer variableFlowAnalyzer;
  private final GuardConditionNarrowing guardConditionNarrowing;
  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;
  private final ScopeMemberTypeResolver scopeMemberTypeResolver;
  private final OScriptFrameworkTypeResolver oScriptFrameworkTypeResolver;

  /**
   * Источники типа, объявленного о переменной помимо кода её тела. Внедряются списком:
   * новый вид объявления — новый бин, а не правка этого класса.
   */
  private final List<VariableTypeSource> variableTypeSources;

  /**
   * Вывести типы выражения в контексте документа.
   */
  public TypeSet infer(BslExpression expression, DocumentContext documentContext) {
    var ctx = new InferenceContext(documentContext);
    try {
      return inferInternal(expression, ctx);
    } catch (StackOverflowError | RuntimeException e) {
      return TypeSet.EMPTY;
    }
  }

  /**
   * Вывести типы символа (метод, переменная, параметр).
   */
  public TypeSet inferSymbol(SourceDefinedSymbol symbol) {
    if (symbol instanceof MethodSymbol method) {
      return symbolTypeIndex.getDeclaredReturnTypes(method);
    }
    if (symbol instanceof VariableSymbol variable) {
      var ctx = new InferenceContext(variable.getOwner());
      return inferVariable(variable, ctx);
    }
    if (symbol instanceof ModuleSymbol module) {
      return scopeMemberTypeResolver.moduleType(module);
    }
    return TypeSet.EMPTY;
  }

  // ---------------------------------------------------------------------------
  // Core dispatch
  // ---------------------------------------------------------------------------

  private TypeSet inferInternal(BslExpression node, InferenceContext ctx) {
    if (node == null || ctx.depth >= MAX_DEPTH) {
      return TypeSet.EMPTY;
    }

    // Кэшируем результат узла только для «чистого корня» инференса — вне
    // рекурсии символов (visited/inProgress пусты). Это гарантирует и
    // контекст-независимость результата, и принадлежность узла текущему
    // документу (кросс-модульный спуск всегда идёт уже после резолва символа,
    // т.е. при непустом visited), поэтому ключ по URI корректен.
    var cacheKey = ctx.visited.isEmpty() && ctx.inProgress.isEmpty()
      ? node.getRepresentingAst()
      : null;
    var uri = ctx.documentContext.getUri();
    if (cacheKey != null) {
      var cached = inferredExpressionTypeIndex.get(uri, cacheKey);
      if (cached != null) {
        return cached;
      }
    }

    ctx.depth++;
    try {
      var result = switch (node.getNodeType()) {
        case LITERAL -> inferLiteral(node);
        case IDENTIFIER -> inferIdentifier(node, ctx);
        case CALL -> inferCall(node, ctx);
        case BINARY_OP -> inferBinary((BinaryOperationNode) node, ctx);
        case UNARY_OP -> inferUnary((UnaryOperationNode) node);
        case TERNARY_OP -> inferTernary((TernaryOperatorNode) node, ctx);
        case SKIPPED_CALL_ARG, ERROR -> TypeSet.EMPTY;
      };
      if (cacheKey != null) {
        inferredExpressionTypeIndex.put(uri, cacheKey, result);
      }
      return result;
    } finally {
      ctx.depth--;
    }
  }

  // ---------------------------------------------------------------------------
  // Literals & identifiers
  // ---------------------------------------------------------------------------

  private TypeSet inferLiteral(BslExpression node) {
    var ast = node.getRepresentingAst();
    if (ast instanceof BSLParser.ConstValueContext constValue) {
      return literalTypeOf(constValue);
    }
    if (ast instanceof TerminalNode terminal) {
      return literalTypeOf(terminal);
    }
    return TypeSet.EMPTY;
  }

  private TypeSet literalTypeOf(BSLParser.ConstValueContext ctx) {
    if (ctx.string() != null) return TypeSet.of(STRING);
    if (ctx.numeric() != null) return TypeSet.of(NUMBER);
    if (ctx.TRUE() != null || ctx.FALSE() != null) return TypeSet.of(BOOLEAN);
    if (ctx.DATETIME() != null) return TypeSet.of(DATE);
    if (ctx.UNDEFINED() != null) return TypeSet.of(UNDEFINED);
    if (ctx.NULL() != null) return TypeSet.of(NULL);
    return TypeSet.EMPTY;
  }

  private TypeSet literalTypeOf(TerminalNode terminal) {
    var type = terminal.getSymbol().getType();
    if (type == BSLParser.STRING || type == BSLParser.STRINGSTART
      || type == BSLParser.STRINGPART || type == BSLParser.STRINGTAIL) {
      return TypeSet.of(STRING);
    }
    if (type == BSLParser.TRUE || type == BSLParser.FALSE) return TypeSet.of(BOOLEAN);
    if (type == BSLParser.UNDEFINED) return TypeSet.of(UNDEFINED);
    if (type == BSLParser.NULL) return TypeSet.of(NULL);
    if (type == BSLParser.DATETIME) return TypeSet.of(DATE);
    if (type == BSLParser.FLOAT || type == BSLParser.DECIMAL) return TypeSet.of(NUMBER);
    return TypeSet.EMPTY;
  }

  private TypeSet inferIdentifier(BslExpression node, InferenceContext ctx) {
    var ast = node.getRepresentingAst();
    if (!(ast instanceof TerminalNode terminal)) {
      return TypeSet.EMPTY;
    }
    return identifierType(terminal, ctx);
  }

  /**
   * Тип голого идентификатора под терминалом — резолв и фоллбэк целиком внутри; вызывающий
   * ({@link #inferIdentifier}) про фоллбэки не знает.
   * <p>
   * Если ссылка резолвится в переменную/метод/self-член — берём её тип целиком, даже честно
   * пустой (локальная переменная без единого присваивания): подменять его self-свойством того
   * же имени нельзя, иначе вернётся self-member-затенение.
   * <p>
   * Если же ссылки нет ИЛИ она указывает на не-типизируемый здесь вид символа (например,
   * {@code ModuleSymbol} модуля-аксессора общего модуля) — тип выводит фоллбэк: неявное поле
   * extends-родителя → self-свойство self-типа модуля → глобальное свойство. Только
   * {@code PROPERTY}: голый идентификатор без вызова не может ссылаться на метод (вызов
   * резолвится в inferCall).
   */
  private TypeSet identifierType(TerminalNode terminal, InferenceContext ctx) {
    var maybeRef = referenceResolver.findReference(ctx.documentContext.getUri(), terminal);
    if (maybeRef.isPresent()) {
      var target = maybeRef.get().symbol();
      // Синтетический self-свойство/метод/глобал — тип напрямую из MemberDescriptor.
      if (target instanceof PlatformMemberSymbol platformMember) {
        return platformMember.getDescriptor().returnTypes();
      }
      // Source-defined переменная/метод — их тип (даже честно пустой), с защитой от цикла.
      if (target instanceof VariableSymbol variable) {
        var byFlow = flowTypeAt(variable, terminal, ctx);
        return byFlow != null ? byFlow : sourceSymbolType(variable, ctx);
      }
      if (target instanceof MethodSymbol method) {
        return sourceSymbolType(method, ctx);
      }
      // Иначе вид символа здесь не типизируем — падаем на фоллбэк ниже.
    }
    var text = terminal.getText();
    if (text.isBlank()) {
      return TypeSet.EMPTY;
    }
    var implicitParent = oScriptFrameworkTypeResolver.implicitParentFieldType(text, ctx.documentContext);
    if (!implicitParent.isEmpty()) {
      return implicitParent;
    }
    return scopeMemberTypeResolver.selfMemberType(ctx.documentContext, text, MemberKind.PROPERTY)
      .orElseGet(() -> scopeMemberTypeResolver.globalPropertyType(ctx.documentContext, text));
  }

  // ---------------------------------------------------------------------------
  // Calls (constructor / method)
  // ---------------------------------------------------------------------------

  private TypeSet inferCall(BslExpression node, InferenceContext ctx) {
    if (node instanceof ConstructorCallNode constructor) {
      return inferConstructor(constructor, ctx);
    }
    if (node instanceof MethodCallNode methodCall) {
      return inferMethodCall(methodCall, ctx);
    }
    return TypeSet.EMPTY;
  }

  private TypeSet inferConstructor(ConstructorCallNode constructor, InferenceContext ctx) {
    var typeName = extractTypeName(constructor);
    if (typeName == null || typeName.isBlank()) {
      return TypeSet.EMPTY;
    }
    var base = typeRegistry.resolve(typeName, ctx.documentContext.getFileType())
      .map(TypeSet::of)
      .orElseGet(() -> TypeSet.of(typeRegistry.intern(TypeKind.USER, typeName)));
    base = attachDefaultElementTypes(base);
    if (OpenDataObjectInference.isStructureLike(typeName)) {
      base = openDataObjectInference.applyConstructorKeys(base, constructor, node -> inferInternal(node, ctx));
    }
    if (OpenDataObjectInference.isTypeDescriptionType(typeName)) {
      base = openDataObjectInference.applyTypeDescriptionTypes(
        base, constructor, ctx.documentContext.getFileType());
    }
    return base;
  }

  /**
   * Прикрепить к каждому {@link TypeRef} в наборе элементы-по-умолчанию из
   * {@link TypeRegistry#getDefaultElementTypes(TypeRef)}. Это позволяет
   * {@code Для Каждого X Из Коллекция Цикл} увидеть тип X (например,
   * {@code КлючИЗначение} для {@code Соответствие}) без явных JsDoc-аннотаций.
   * <p>
   * Уточнение, добытое на месте, не перетирается: оно точнее реестрового умолчания.
   * Так {@code Массив из Число} не превращается в {@code Массив из Число, Произвольный}
   * (#4179), а {@code ТаблицаЗначений}, выгруженная из табличной части, сохраняет
   * строку с её колонками вместо обобщённой {@code СтрокаТаблицыЗначений}.
   */
  private TypeSet attachDefaultElementTypes(TypeSet base) {
    if (base.isEmpty()) {
      return base;
    }
    var result = base;
    for (var ref : base.refs()) {
      if (!base.getElementTypes(ref).isEmpty()) {
        continue;
      }
      var defaults = typeRegistry.getDefaultElementTypes(ref);
      if (!defaults.isEmpty()) {
        result = result.withElement(ref, defaults);
      }
    }
    return result;
  }

  /**
   * Уточнение типа члена, которое из объявления не выводится: члены табличных
   * коллекций (зависят от колонок получателя и аргументов вызова) и
   * {@code Получить(Индекс)} как методная форма индексатора.
   *
   * @param leftTypes  типы получателя.
   * @param memberName имя члена.
   * @param call       узел вызова, если член — метод; {@code null} для свойства.
   * @param ctx        контекст инференса.
   * @return уточнённый тип; {@code null}, если уточнять нечем и нужен общий путь.
   */
  @Nullable
  private TypeSet refinedMemberTypes(TypeSet leftTypes, String memberName,
                                     @Nullable MethodCallNode call, InferenceContext ctx) {
    var tableTypes = tableCollectionInference.infer(
      leftTypes, memberName, call, ctx.documentContext.getFileType());
    if (tableTypes != null) {
      return tableTypes;
    }
    if (call != null) {
      var adjusted = openDataObjectInference.adjustedValueTypes(leftTypes, memberName);
      if (adjusted != null) {
        return adjusted;
      }
    }
    if (call == null || !ELEMENT_GETTER.equalsIgnoreCase(memberName)) {
      return null;
    }
    var element = elementGetterTypes(leftTypes);
    return element.isEmpty() ? null : element;
  }

  /**
   * Тип элемента для {@code Получить(Индекс)}. Платформа объявляет возврат как
   * {@code Произвольный}, хотя это ровно то же, что даёт индексатор, — поэтому
   * цепочка {@code …ВыгрузитьКолонку("КТУ").Получить(0)} без уточнения обрывалась.
   * <p>
   * KV-коллекции исключены: у {@code Соответствие.Получить(Ключ)} результат — значение,
   * а элемент коллекции — {@code КлючИЗначение}, и подстановка элемента там была бы ошибкой.
   *
   * @param leftTypes типы получателя.
   * @return типы элемента; {@link TypeSet#EMPTY}, если правило неприменимо.
   */
  private static TypeSet elementGetterTypes(TypeSet leftTypes) {
    for (var ref : leftTypes.refs()) {
      if (OpenDataObjectInference.isStructureOrMapLike(ref.qualifiedName())) {
        return TypeSet.EMPTY;
      }
    }
    var result = TypeSet.EMPTY;
    for (var ref : leftTypes.refs()) {
      result = result.union(leftTypes.getElementTypes(ref));
    }
    return result;
  }

  @Nullable
  private static String extractTypeName(ConstructorCallNode constructor) {
    var ast = constructor.getTypeName().getRepresentingAst();
    if (ast == null) {
      return null;
    }
    return stripQuotes(ast.getText());
  }

  private static String stripQuotes(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    var first = s.charAt(0);
    var last = s.charAt(s.length() - 1);
    if ((first == '"' || first == '\'') && first == last) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  private TypeSet inferMethodCall(MethodCallNode call, InferenceContext ctx) {
    var name = call.getName();
    if (name == null) {
      return TypeSet.EMPTY;
    }
    // Терминал имени вызова уже под рукой — резолвим по нему, без спуска по AST
    // от корня к позиции в reference-finder'ах. Для имени, не попавшего в
    // индекс ссылок проекта (платформенная глобальная функция, неквалифицированный
    // вызов self-метода модуля), reference остаётся пустым Optional — шаг 1
    // ниже тогда пуст, и резолвинг продолжается шагами 2/3.
    var reference = referenceResolver.findReference(ctx.documentContext.getUri(), name);
    // 1. Источник-источник в проекте — это MethodSymbol. Если ссылка резолвится
    //    именно в него, доверяем результату целиком — даже честно пустому
    //    (процедура или функция без объявленного типа возврата) — и НЕ падаем
    //    дальше на глобальную функцию/self-член с тем же именем: совпадение
    //    имени не делает их одним и тем же символом (см. identifierType — тот же
    //    принцип для голых идентификаторов).
    var localMethod = reference
      .flatMap(Reference::getSourceDefinedSymbol)
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast);
    if (localMethod.isPresent()) {
      return symbolTypeIndex.getDeclaredReturnTypes(localMethod.get());
    }
    // 2. Платформенная глобальная функция (СтрНайти и т.п.) — через
    //    GlobalScopeProvider (полный MemberDescriptor с TypeSet, включая union).
    var globalReturn = scopeMemberTypeResolver.globalFunctionType(ctx.documentContext, name.getText());
    if (!globalReturn.isEmpty()) {
      return globalReturn;
    }
    // 3. Неквалифицированный вызов платформенного метода self-типа модуля.
    //    Тот же self-тип, что и в inferIdentifier для свойств, здесь —
    //    MemberKind.METHOD.
    return scopeMemberTypeResolver.selfMemberType(ctx.documentContext, name.getText(), MemberKind.METHOD)
      .orElse(TypeSet.EMPTY);
  }

  // ---------------------------------------------------------------------------
  // Binary, unary, ternary
  // ---------------------------------------------------------------------------

  private TypeSet inferBinary(BinaryOperationNode node, InferenceContext ctx) {
    var op = node.getOperator();
    if (op == BslOperator.DEREFERENCE) {
      return inferDereference(node, ctx);
    }
    if (op == BslOperator.INDEX_ACCESS) {
      return inferIndexAccess(node, ctx);
    }
    if (isLogical(op) || isComparison(op)) {
      return TypeSet.of(BOOLEAN);
    }
    if (op == BslOperator.ADD) {
      // Тип `+` определяется ЛЕВЫМ операндом — правый приводится к нему.
      var left = inferInternal(node.getLeft(), ctx);
      if (left.refs().contains(STRING)) {
        return TypeSet.of(STRING);
      }
      if (left.refs().contains(DATE)) {
        return TypeSet.of(DATE);
      }
      return TypeSet.of(NUMBER);
    }
    if (op == BslOperator.SUBTRACT || op == BslOperator.MULTIPLY
      || op == BslOperator.DIVIDE || op == BslOperator.MODULO) {
      return TypeSet.of(NUMBER);
    }
    return TypeSet.EMPTY;
  }

  /**
   * Индексатор {@code coll[i]}. Семантика зависит от того, KV-коллекция перед нами
   * или последовательностная:
   * <ul>
   *   <li>KV (Структура/Соответствие и Fixed-варианты): {@code coll[key]} — это
   *       значение по ключу. Если индекс — строковый литерал, резолвим точно через
   *       {@link TypeSet#getLocalFields(TypeRef)}; если индекс динамический —
   *       union по всем известным значениям; если ключа нет — empty.</li>
   *   <li>Sequence (Массив/ТЗ/СписокЗначений/коллекции колонок/etc.): возвращаем
   *       элементы через {@link TypeSet#getElementTypes(TypeRef)} — это сразу
   *       подхватывает динамические поля строки ТЗ и платформенные members
   *       элемента.</li>
   * </ul>
   * KV-приоритет включается, если у левого типа есть прямые {@code localFields}
   * (т.е. {@code .Вставить(...)} в скоупе уже наполнил карту ключей). Иначе —
   * sequence-путь по умолчанию.
   */
  private TypeSet inferIndexAccess(BinaryOperationNode node, InferenceContext ctx) {
    var leftTypes = inferInternal(node.getLeft(), ctx);
    if (leftTypes.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var kvFields = OpenDataObjectInference.fieldsOf(leftTypes);
    if (!kvFields.isEmpty()) {
      var keyName = OpenDataObjectInference.stringLiteralOf(node.getRight());
      if (keyName != null) {
        var trimmed = keyName.trim();
        TypeSet exact = TypeSet.EMPTY;
        for (var entry : kvFields.entrySet()) {
          if (entry.getKey().equalsIgnoreCase(trimmed)) {
            exact = exact.union(entry.getValue());
          }
        }
        return exact;
      }
      // Динамический индекс — union по всем известным value-типам.
      TypeSet union = TypeSet.EMPTY;
      for (var values : kvFields.values()) {
        union = union.union(values);
      }
      return union;
    }
    TypeSet result = TypeSet.EMPTY;
    for (var ref : leftTypes.refs()) {
      result = result.union(leftTypes.getElementTypes(ref));
    }
    return result;
  }

  private TypeSet inferDereference(BinaryOperationNode node, InferenceContext ctx) {
    var leftTypes = inferInternal(node.getLeft(), ctx);
    if (leftTypes.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var right = node.getRight();
    var methodCall = right instanceof MethodCallNode call ? call : null;
    String memberName;
    MemberKind expectedKind;
    if (methodCall != null) {
      var nameNode = methodCall.getName();
      memberName = nameNode == null ? null : nameNode.getText();
      expectedKind = MemberKind.METHOD;
    } else {
      var rightAst = right == null ? null : right.getRepresentingAst();
      memberName = memberNameOf(rightAst);
      expectedKind = MemberKind.PROPERTY;
    }
    if (memberName == null || memberName.isBlank()) {
      return TypeSet.EMPTY;
    }
    var refined = refinedMemberTypes(leftTypes, memberName, methodCall, ctx);
    if (refined != null) {
      return refined;
    }
    if (expectedKind == MemberKind.PROPERTY) {
      var fromLocalFields = OpenDataObjectInference.fieldTypes(leftTypes, memberName);
      if (!fromLocalFields.isEmpty()) {
        return fromLocalFields;
      }
    }
    TypeSet result = TypeSet.EMPTY;
    for (var leftType : leftTypes.refs()) {
      // Колонки/поля, накопленные на elementTypes левого типа (например, ТЗ с
      // Колонки.Добавить("X")) должны прокидываться на строку, возвращённую
      // методами вида .Добавить()/.Получить()/.Вставить(), у которых return-тип
      // совпадает с element-ref'ом коллекции.
      var elementSet = leftTypes.getElementTypes(leftType);
      for (var member : typeRegistry.getMembers(leftType, ctx.documentContext.getFileType())) {
        if (member.kind() != expectedKind) {
          continue;
        }
        if (!member.matches(memberName)) {
          continue;
        }
        // Для метода проектного модуля (в т.ч. вызванного межмодульно как
        // ОбщийМодуль.Метод()) берём полный тип возврата из индекса символов —
        // с localFields структуры/ТЗ, объявленными в JsDoc. MemberDescriptor
        // несёт лишь головной ref, поэтому без этого поля структуры терялись.
        if (expectedKind == MemberKind.METHOD) {
          var declaredReturn = member.getSourceSymbol()
            .filter(MethodSymbol.class::isInstance)
            .map(MethodSymbol.class::cast)
            .map(symbolTypeIndex::getDeclaredReturnTypes)
            .filter(declared -> !declared.isEmpty());
          if (declaredReturn.isPresent()) {
            result = result.union(declaredReturn.get());
            continue;
          }
        }
        // Возможные типы члена (union); UNKNOWN-ref'ы отбрасываем.
        for (var ref : member.returnTypes().refs()) {
          if (ref != null && ref.kind() != TypeKind.UNKNOWN) {
            var returned = enrichReturnRefWithElementFields(ref, elementSet);
            result = result.union(carryDeclaredDecorations(member.returnTypes(), ref, returned));
          }
        }
      }
    }
    return attachDefaultElementTypes(result);
  }

  /**
   * Если {@code ret} совпадает с одним из element-ref'ов коллекции на левом
   * типе — построить TypeSet с этим ref'ом и его {@code localFields} из
   * {@code elementSet} (то есть «передать» накопленные колонки/поля строки).
   * Иначе — обычный {@link TypeSet#of(TypeRef)}.
   */
  @Nullable
  private static TypeSet enrichReturnRefWithElementFields(TypeRef ret, TypeSet elementSet) {
    if (!elementSet.refs().contains(ret)) {
      return TypeSet.of(ret);
    }
    return TypeSet.of(ret).withFields(ret, elementSet.getLocalFields(ret));
  }

  /**
   * Переносит на выведенный тип уточнения, объявленные в самом
   * {@link com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor}:
   * тип элемента коллекции и поля «открытого» объекта. Голого набора ref'ов мало —
   * возврат вида «{@code Массив} строк вот этой табличной части» весь смысл держит
   * именно в уточнении, и без переноса оно терялось бы.
   *
   * @param declared объявленные типы возврата члена.
   * @param ref      ref, для которого собирается результат.
   * @param target   уже собранный результат по этому ref'у.
   * @return результат с перенесёнными уточнениями.
   */
  private static TypeSet carryDeclaredDecorations(TypeSet declared, TypeRef ref, TypeSet target) {
    var result = target;
    var elements = declared.getElementTypes(ref);
    if (!elements.isEmpty()) {
      result = result.withElement(ref, elements);
    }
    return result.withFields(ref, declared.getLocalFields(ref));
  }

  @Nullable
  private static String memberNameOf(ParseTree ast) {
    if (ast instanceof TerminalNode terminal) {
      return terminal.getText();
    }
    return ast.getText();
  }

  @SuppressWarnings("unused")
  private TypeSet inferUnary(UnaryOperationNode node) {
    var op = node.getOperator();
    if (op == BslOperator.NOT) {
      return TypeSet.of(BOOLEAN);
    }
    return TypeSet.of(NUMBER);
  }

  private TypeSet inferTernary(TernaryOperatorNode node, InferenceContext ctx) {
    var truthy = inferInternal(node.getTruePart(), ctx);
    var falsy = inferInternal(node.getFalsePart(), ctx);
    return truthy.union(falsy);
  }

  // ---------------------------------------------------------------------------
  // Reference resolution
  // ---------------------------------------------------------------------------

  /**
   * Тип source-defined символа-цели ссылки (переменной/метода) с защитой от цикла инференса.
   * Результат всегда присутствует, даже если сам тип — пустой {@link TypeSet#EMPTY}: честно
   * невыведенный тип нельзя подменять self-свойством того же имени.
   */
  private TypeSet sourceSymbolType(SourceDefinedSymbol symbol, InferenceContext ctx) {
    if (!ctx.visited.add(symbol)) {
      // Цикл: для переменной-аккумулятора возвращаем накопленный к этому моменту
      // тип (см. inProgress), для прочих символов — пусто, как и раньше.
      return ctx.inProgress.getOrDefault(symbol, TypeSet.EMPTY);
    }
    try {
      if (symbol instanceof MethodSymbol method) {
        return symbolTypeIndex.getDeclaredReturnTypes(method);
      }
      return inferVariable((VariableSymbol) symbol, ctx);
    } finally {
      ctx.visited.remove(symbol);
    }
  }

  /**
   * Тип переменной = union по позиции её декларации + всем DEFINITION-обращениям
   * из {@code ReferenceIndex}. Декларация нужна, т.к. {@code ReferenceIndexFiller}
   * фильтрует first-assignment (initialization) — она содержится в самом
   * {@link VariableSymbol#getSelectionRange()}.
   * <p>
   * Для параметра метода — добавляются типы, объявленные в JsDoc
   * (секция {@code // Параметры:}).
   */
  private TypeSet inferVariable(VariableSymbol variable, InferenceContext ctx) {
    // Кэш ключуется только по VariableSymbol, без fileType. Это корректно, потому что
    // fileType документа детерминирован самой переменной (variable.getOwner().getFileType()),
    // а inferSymbol всегда заводит ctx.documentContext == variable.getOwner() — то есть
    // одна и та же переменная не инферится в двух разных fileType-контекстах.
    var cached = inferredVariableTypeIndex.get(variable);
    if (cached != null) {
      return cached;
    }

    // Повторный вход в инференс той же переменной (self-reference в одном из её
    // присваиваний) — возвращаем накопленный к этому моменту тип, а не гоняем
    // тело второй раз (иначе частичный результат затёр бы себя и потерялся, #4205).
    var partial = ctx.inProgress.get(variable);
    if (partial != null) {
      return partial;
    }
    ctx.inProgress.put(variable, TypeSet.EMPTY);
    try {
      var acc = inferVariableInternal(variable, ctx);
      // Кэшируем только «чистый корень» инференса (visited содержит максимум саму
      // переменную). Вложенный вызов (внутри инференса другой переменной, visited
      // ≥ 2) мог быть усечён цикл-гардом и зависит от порядка обхода — его результат
      // некорректно переиспользовать как самостоятельный. Перф от этого не страдает:
      // горячий путь (ресивер member-доступа) — всегда корень, а вложенные выводы
      // и так покрыты кэшем своего корня.
      if (ctx.visited.size() <= 1) {
        inferredVariableTypeIndex.put(variable, acc);
      }
      return acc;
    } finally {
      ctx.inProgress.remove(variable);
    }
  }

  /**
   * Тело инференса переменной без кэш-обвязки: известное на входе в тело, объединённое со
   * всеми присваиваниями и всеми изменениями на месте.
   * <p>
   * Слагаемые те же, что у расчёта по потоку управления, и считаются теми же методами —
   * {@link #declaredTypes}, {@link #definitionPositions}, {@link #applyMutation}. Разница
   * в том, что здесь они объединяются без учёта порядка и достижимости, а значит ответ
   * один на всю область видимости. Это отступной путь для случаев, где расчёт по потоку
   * неприменим (см. {@link #flowTypeAt}).
   * <p>
   * По мере объединения присваиваний растущий тип публикуется в
   * {@link InferenceContext#inProgress}: так ссылка переменной на саму себя
   * ({@code Строка = Строка + "…"}) резолвится в тип из предыдущих присваиваний,
   * а не в {@link TypeSet#EMPTY}.
   *
   * @param variable переменная.
   * @param ctx      контекст текущего инференса.
   * @return тип переменной по всей области видимости.
   */
  private TypeSet inferVariableInternal(VariableSymbol variable, InferenceContext ctx) {
    var owner = variable.getOwner();
    var acc = declaredTypes(variable);
    ctx.inProgress.put(variable, acc);

    for (var position : definitionPositions(variable)) {
      acc = acc.union(inferFromDefinitionPosition(owner, position, ctx));
      ctx.inProgress.put(variable, acc);
    }
    acc = attachDefaultElementTypes(acc);
    acc = openDataObjectInference.applyAll(variable, acc, node -> inferInternal(node, ctx));
    return acc;
  }

  /**
   * Тип переменной в точке использования, рассчитанный по потоку управления тела:
   * присваивание перекрывает прежний тип, в точках слияния путей типы объединяются.
   * Точнее объединения по всем присваиваниям, которое даёт {@link #inferVariable}.
   *
   * @param variable переменная.
   * @param terminal терминал использования.
   * @param ctx      контекст текущего инференса.
   * @return тип в этой точке; {@code null}, если расчёт по потоку неприменим и нужен
   *     общий путь: использование в другом документе либо неразмещаемое в графе
   *     присваивание. Отсутствие присваиваний расчёту не мешает — тип такой переменной
   *     есть входной факт по всему телу.
   */
  @Nullable
  private TypeSet flowTypeAt(VariableSymbol variable, TerminalNode terminal, InferenceContext ctx) {
    var owner = variable.getOwner();
    if (!owner.getUri().equals(ctx.documentContext.getUri())) {
      return null;
    }
    if (!(terminal.getParent() instanceof ParserRuleContext use)) {
      return null;
    }
    // Повторный вход по той же переменной здесь не отсекается: у `Х = Х + 1` правая часть
    // спрашивает тип посреди расчёта того же тела, и ответ у расчёта есть — окружение перед
    // текущим оператором. Отказ отдал бы объединение по всей области видимости, то есть
    // примешал бы типы из присваиваний ниже по коду. Зацикливания не будет: строящееся
    // окружение отвечает чтением из карты, не запуская расчёт заново.
    try {
      return variableFlowAnalyzer.typeAt(owner, use, variable, flowInputs(variable, ctx));
    } catch (StackOverflowError | RuntimeException e) {
      // Расчёт по потоку — уточнение, а не единственный источник типа. Если он сорвался
      // (например, граф не построился на неожиданной форме кода либо рекурсия ушла слишком
      // глубоко), тип должен дать прежний путь, а не общий перехват в infer(), который
      // обнулил бы всё выражение целиком.
      LOGGER.debug("Расчёт типа по потоку не удался для переменной {}", variable.getName(), e);
      return null;
    }
  }

  /**
   * Тип переменной в точке, на которую указывает ссылка, — с учётом того, какие
   * присваивания и изменения на месте уже случились на путях к ней.
   * <p>
   * Отличается от {@link #inferSymbol(SourceDefinedSymbol)}, который отвечает про
   * переменную в целом, объединяя всё по её области видимости. Узел дерева разбора
   * не нужен: расчёт находит тело и оператор по позиции.
   *
   * @param reference ссылка на переменную — несёт и документ, и позицию.
   * @return тип в этой точке; {@code null}, если ссылка не на переменную текущего
   *     документа либо расчёт по потоку неприменим.
   */
  @Nullable
  public TypeSet inferVariableAt(Reference reference) {
    if (!(reference.getSourceDefinedSymbol().orElse(null) instanceof VariableSymbol variable)
      || !variable.getOwner().getUri().equals(reference.uri())) {
      return null;
    }
    return inferVariableAt(
      variable,
      reference.selectionRange().getStart(),
      // Ссылка на само присваивание спрашивает про тип до него, а не после.
      reference.occurrenceType() == OccurrenceType.DEFINITION
    );
  }

  /**
   * Тип переменной в указанной точке документа, которому она принадлежит.
   * <p>
   * Точка не обязана быть обращением к переменной: расчёт отвечает на вопрос, что
   * переменная содержит в этом месте кода.
   *
   * @param variable переменная.
   * @param position точка в теле, для которой нужен тип.
   * @return тип в этой точке; {@code null}, если расчёт по потоку неприменим.
   */
  @Nullable
  public TypeSet inferVariableAt(VariableSymbol variable, Position position) {
    return inferVariableAt(variable, position, false);
  }

  /**
   * Тип переменной в точке с уточнением, стоит ли точка на самом присваивании.
   *
   * @param variable     переменная.
   * @param position     точка в теле, для которой нужен тип.
   * @param atDefinition стоит ли точка на присваивании: тогда берётся тип до него.
   * @return тип в этой точке; {@code null}, если расчёт по потоку неприменим.
   */
  @Nullable
  private TypeSet inferVariableAt(VariableSymbol variable, Position position, boolean atDefinition) {
    var owner = variable.getOwner();
    var ctx = new InferenceContext(owner);
    try {
      return variableFlowAnalyzer.typeAt(owner, position, atDefinition, variable, flowInputs(variable, ctx));
    } catch (StackOverflowError | RuntimeException e) {
      return null;
    }
  }

  /**
   * Исходные данные расчёта по потоку для переменной: что известно на входе в тело,
   * где она меняется и как считать вклад каждого изменения.
   *
   * @param variable переменная.
   * @param ctx      контекст текущего инференса.
   * @return данные для {@link VariableFlowAnalyzer}.
   */
  private VariableFlowAnalyzer.FlowInputs flowInputs(VariableSymbol variable, InferenceContext ctx) {
    var owner = variable.getOwner();
    // Операторы-мутаторы разбираются лениво и по одному разу на переменную: за ними стоит
    // обход индекса вызовов, а при готовом окружении в кэше они не нужны вовсе.
    Map<VariableSymbol, Lazy<Map<Position, BSLParser.CallStatementContext>>> callsByVariable = new HashMap<>();
    Function<VariableSymbol, Map<Position, BSLParser.CallStatementContext>> callsOf = target ->
      callsByVariable
        .computeIfAbsent(target, key -> new Lazy<>(() -> openDataObjectInference.mutatorsOf(key)))
        .getOrCompute();
    return new VariableFlowAnalyzer.FlowInputs(
      ctx.flowSession,
      // Тот же критерий, что у кэша выведенных типов переменных: вложенный расчёт
      // (внутри инференса другой переменной) мог быть усечён защитой от циклов,
      // и переиспользовать такой результат как самостоятельный нельзя.
      ctx.visited.size() <= 1,
      body -> variablesOfBody(owner, body),
      target -> target.getKind() == VariableKind.MODULE,
      this::declaredTypes,
      this::definitionPositions,
      target -> callsOf.apply(target).keySet(),
      (target, statement, position) ->
        attachDefaultElementTypes(inferFromDefinition(owner, statement, position, ctx)),
      (target, position, incoming) -> openDataObjectInference.apply(
        target, callsOf.apply(target).get(position), incoming, node -> inferInternal(node, ctx)),
      narrowingCallback(owner)
    );
  }

  /**
   * Переменные, видимые в теле: расчёт по потоку считает их все разом, одним поиском
   * неподвижной точки.
   * <p>
   * Это переменные области видимости самого тела (метода либо тела модуля) плюс переменные
   * модуля, объявленные {@code Перем}, — они видны из любого метода. Набор зависит только
   * от тела: окружение считается на всё тело сразу и переиспользуется всеми запросами,
   * поэтому от того, про какую переменную спросили первой, он зависеть не может.
   *
   * @param owner документ с телом.
   * @param body  тело, для которого идёт расчёт.
   * @return переменные, видимые в этом теле.
   */
  private static List<VariableSymbol> variablesOfBody(DocumentContext owner, BSLParser.CodeBlockContext body) {
    // Раскладку по областям видимости дерево символов уже держит готовой и ленивой —
    // своего перебора всех переменных модуля на каждый расчёт тела не нужно.
    var symbolTree = owner.getSymbolTree();
    var byScope = symbolTree.getVariablesByName();
    SourceDefinedSymbol module = symbolTree.getModule();
    SourceDefinedSymbol scope = scopeOfBody(symbolTree, body)
      .map(SourceDefinedSymbol.class::cast)
      .orElse(module);
    var visible = new ArrayList<VariableSymbol>();
    var inScope = byScope.get(scope);
    if (inScope != null) {
      visible.addAll(inScope.values());
    }
    if (scope != module) {
      addModuleVariables(byScope.get(module), visible);
    }
    return visible;
  }

  /**
   * Добавить к набору переменные, объявленные {@code Перем} на уровне модуля: они видны из
   * любого метода, а созданные присваиванием в теле модуля — нет.
   *
   * @param atModuleLevel переменные области видимости модуля; {@code null}, если их нет.
   * @param target        набор, куда добавлять.
   */
  private static void addModuleVariables(
    @Nullable Map<String, VariableSymbol> atModuleLevel,
    List<VariableSymbol> target
  ) {
    if (atModuleLevel == null) {
      return;
    }
    for (var candidate : atModuleLevel.values()) {
      if (candidate.getKind() == VariableKind.MODULE) {
        target.add(candidate);
      }
    }
  }

  /**
   * Область видимости тела — метод, которому оно принадлежит.
   *
   * @param symbolTree дерево символов документа.
   * @param body       тело.
   * @return символ метода; пусто, если это тело модуля, а не метода.
   */
  private static Optional<MethodSymbol> scopeOfBody(SymbolTree symbolTree, BSLParser.CodeBlockContext body) {
    BSLParser.SubContext sub = Trees.getAncestorByRuleIndex(body, BSLParser.RULE_sub);
    return sub == null ? Optional.empty() : symbolTree.getMethodSymbol(sub);
  }

  /**
   * Колбэк сужения по охраняющим условиям с запоминанием разбора: расчёт идёт проходами и
   * спрашивает одни и те же условия многократно, а разбор тянет резолв переменной через
   * индекс — самую дорогую часть шага.
   *
   * @param owner документ с условиями.
   * @return колбэк для {@link VariableFlowAnalyzer}.
   */
  private VariableFlowAnalyzer.GuardNarrowing narrowingCallback(DocumentContext owner) {
    return new VariableFlowAnalyzer.GuardNarrowing() {
      @Override
      public TypeSet narrow(
        VariableSymbol variable,
        BSLParser.ExpressionContext condition,
        boolean whenTrue,
        TypeSet incoming
      ) {
        return guardConditionNarrowing.compile(condition, owner).apply(variable, whenTrue, incoming);
      }

      @Override
      public Set<? extends SourceDefinedSymbol> variablesOf(BSLParser.ExpressionContext condition) {
        return guardConditionNarrowing.compile(condition, owner).variables();
      }

      @Override
      public TypeSet narrowBefore(
        VariableSymbol variable,
        BSLParser.ExpressionContext condition,
        Position position,
        TypeSet incoming
      ) {
        return guardConditionNarrowing.compile(condition, owner).narrowBefore(variable, position, incoming);
      }
    };
  }

  /**
   * Тип переменной до первого присваивания: то, что известно из объявления, а не из кода.
   *
   * @param variable переменная.
   * @return типы из объявления; пустой набор, если ничего не объявлено.
   */
  private TypeSet declaredTypes(VariableSymbol variable) {
    var entry = TypeSet.EMPTY;
    for (var source : variableTypeSources) {
      entry = entry.union(source.typesOf(variable));
    }
    return entry;
  }

  /**
   * Позиции всех присваиваний переменной: {@code DEFINITION}-вхождения из индекса ссылок
   * плюс позиция самого символа — но только у переменной, созданной первым присваиванием,
   * где объявления как отдельной записи нет (см. {@link #declarationIsAssignment}).
   *
   * @param variable переменная.
   * @return позиции присваиваний без повторов.
   */
  private Collection<Position> definitionPositions(VariableSymbol variable) {
    // Множество, а не список с проверкой contains: у переменной в длинном методе
    // присваиваний бывают десятки, и отсев повторов перебором давал квадрат.
    Set<Position> positions = new LinkedHashSet<>();
    if (declarationIsAssignment(variable)) {
      positions.add(variable.getSelectionRange().getStart());
    }
    for (var occurrence : referenceIndex.getReferencesTo(variable)) {
      if (occurrence.occurrenceType() == OccurrenceType.DEFINITION) {
        positions.add(occurrence.selectionRange().getStart());
      }
    }
    return positions;
  }

  /**
   * Совпадает ли объявление переменной с присваиванием.
   * <p>
   * У переменной, созданной первым присваиванием, объявления как отдельной записи нет —
   * её позиция и есть позиция присваивания. У параметра это имя в подписи метода, у
   * объявленной через {@code Перем} — сама эта запись; ни то, ни другое оператором графа
   * не является, и выдавать их за присваивания нельзя: расчёт счёл бы, что присваивание
   * потерялось, и отказался бы от переменной целиком. Что известно на входе в тело, и так
   * даёт входной факт.
   *
   * @param variable переменная.
   * @return {@code true}, если позиция символа указывает на присваивание.
   */
  private static boolean declarationIsAssignment(VariableSymbol variable) {
    var kind = variable.getKind();
    return kind != VariableKind.PARAMETER
      && kind != VariableKind.LOCAL
      && kind != VariableKind.MODULE;
  }


  /**
   * Тип, присваиваемый переменной, когда оператор присваивания уже известен вызывающему.
   * <p>
   * Поиск присваивания по позиции — рекурсивный спуск по дереву разбора от корня файла,
   * и на больших модулях он заметен в профиле. Расчёт по потоку знает оператор графа, в
   * котором стоит присваивание, поэтому спуск ему не нужен.
   *
   * @param owner     документ с присваиванием.
   * @param statement оператор графа, в котором стоит присваивание.
   * @param position  позиция присваивания — на случай, если оператор не присваивание
   *                  (тогда работает поиск по позиции, как раньше).
   * @param ctx       контекст текущего инференса.
   * @return присваиваемые типы; пустой набор, если вывести их не удалось.
   */
  private TypeSet inferFromDefinition(
    DocumentContext owner,
    ParserRuleContext statement,
    Position position,
    InferenceContext ctx
  ) {
    if (statement instanceof BSLParser.AssignmentContext assignment) {
      var expression = ExpressionTreeBuildingVisitor.buildExpressionTree(assignment.expression());
      var types = expression == null ? TypeSet.EMPTY : inferInternal(expression, ctx);
      return types.union(variableCommentTypeResolver.ofAssignment(owner, assignment));
    }
    if (statement instanceof BSLParser.ForStatementContext) {
      // Счётчик «Для Сч = 1 По Граница» — всегда число: язык другого не допускает.
      return TypeSet.of(NUMBER);
    }
    if (statement instanceof BSLParser.ForEachStatementContext forEach) {
      // Связывание «Для Каждого Х Из Коллекция»: тип Х — тип элемента коллекции.
      // Выражение коллекции лежит в самом заголовке, поэтому искать его по позиции
      // спуском по дереву не нужно.
      return elementTypesOfCollection(forEach.expression(), ctx);
    }
    return inferFromDefinitionPosition(owner, position, ctx);
  }

  /**
   * Типы элементов коллекции, по которой идёт обход.
   *
   * @param collection выражение коллекции; {@code null}, если его в заголовке нет.
   * @param ctx        контекст текущего инференса.
   * @return типы элементов; пустой набор, если коллекция не выводится.
   */
  private TypeSet elementTypesOfCollection(BSLParser.@Nullable ExpressionContext collection, InferenceContext ctx) {
    if (collection == null) {
      return TypeSet.EMPTY;
    }
    var collectionExpr = ExpressionTreeBuildingVisitor.buildExpressionTree(collection);
    return collectionExpr == null ? TypeSet.EMPTY : inferInternal(collectionExpr, ctx).getElementTypes();
  }

  /**
   * Тип, присваиваемый переменной в указанной позиции, когда оператор вызывающему неизвестен.
   * <p>
   * Оператор ищется спуском по дереву разбора от корня файла — этим путь и отличается от
   * {@link #inferFromDefinition}, которому оператор известен заранее.
   *
   * @param owner    документ с присваиванием.
   * @param position позиция присваивания либо связывания в цикле обхода.
   * @param ctx      контекст текущего инференса.
   * @return присваиваемые типы; пустой набор, если вывести их не удалось.
   */
  private TypeSet inferFromDefinitionPosition(
    DocumentContext owner,
    Position position,
    InferenceContext ctx
  ) {
    var assignment = ExpressionAtPosition.findAssignment(owner, position);
    TypeSet result = assignment.map(BSLParser.AssignmentContext::expression)
      .map(ExpressionTreeBuildingVisitor::buildExpressionTree)
      .map(expr -> inferInternal(expr, ctx))
      .orElse(TypeSet.EMPTY);
    if (assignment.isPresent()) {
      result = result.union(variableCommentTypeResolver.ofAssignment(owner, assignment.get()));
      return result;
    }
    // Декларация переменной через «Для Каждого X Из Коллекция Цикл»:
    // тип X — это объединение typeSets, объявленных как elementTypes
    // коллекции.
    var forEach = ExpressionAtPosition.findForEachBindingAt(owner, position);
    if (forEach.isPresent()) {
      result = result.union(elementTypesOfCollection(forEach.get().expression(), ctx));
    }
    return result;
  }


  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static boolean isComparison(BslOperator op) {
    return op == BslOperator.EQUAL || op == BslOperator.NOT_EQUAL
      || op == BslOperator.LESS || op == BslOperator.LESS_OR_EQUAL
      || op == BslOperator.GREATER || op == BslOperator.GREATER_OR_EQUAL;
  }

  private static boolean isLogical(BslOperator op) {
    return op == BslOperator.AND || op == BslOperator.OR || op == BslOperator.NOT;
  }

  /**
   * Контекст одной операции infer: стек посещённых символов для защиты от циклов
   * и глубина рекурсии.
   */
  static final class InferenceContext {
    final DocumentContext documentContext;
    final Set<SourceDefinedSymbol> visited = new HashSet<>();
    /**
     * Тип, накопленный к текущему моменту для символа, инференс которого ещё не
     * завершён. Self-reference (например, {@code Строка = Строка + "..."}) резолвится
     * в это частичное значение вместо {@link TypeSet#EMPTY}, что даёт one-pass
     * фикс-точку по присваиваниям вместо потери типа на guard'е циклов (#4205).
     */
    final Map<SourceDefinedSymbol, TypeSet> inProgress = new HashMap<>();
    /**
     * Расчёты по потоку, идущие прямо сейчас в рамках этого вывода. Вывод типа
     * присваивания просит типы переменных из правой части, и если они из того же тела,
     * запрос приходит посреди его же расчёта — тогда он читает строящееся окружение,
     * а не запускает расчёт тела заново.
     */
    final VariableFlowAnalyzer.FlowSession flowSession = new VariableFlowAnalyzer.FlowSession();
    int depth;

    InferenceContext(DocumentContext documentContext) {
      this.documentContext = documentContext;
    }
  }
}
