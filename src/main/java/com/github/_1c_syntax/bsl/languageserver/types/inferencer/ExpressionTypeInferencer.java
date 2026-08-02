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

import com.github._1c_syntax.bsl.languageserver.cfg.BasicBlockVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildOptions;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
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
import com.github._1c_syntax.bsl.languageserver.types.CommentTypeResolver;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Methods;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
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
  private final InferredExpressionTypeIndex inferredExpressionTypeIndex;
  private final TableCollectionInference tableCollectionInference;
  private final OpenDataObjectInference openDataObjectInference;
  private final FormExpressionInference formExpressionInference;
  private final XdtoFactoryInference xdtoFactoryInference;
  private final CommentTypeResolver commentTypeResolver;
  private final VariableFlowAnalyzer variableFlowAnalyzer;
  private final ControlFlowGraphIndex controlFlowGraphIndex;
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
      // Переменная — её тип в этой точке кода, даже честно пустой: невыведенный тип
      // нельзя подменять self-свойством того же имени.
      if (target instanceof VariableSymbol variable) {
        return flowTypeAt(variable, terminal, ctx);
      }
      if (target instanceof MethodSymbol method) {
        return methodReturnType(method, ctx);
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
      var formTypes = formExpressionInference.refinedCallTypes(
        ctx.documentContext, leftTypes, memberName, call);
      if (formTypes != null) {
        return formTypes;
      }
      var xdtoTypes = xdtoFactoryInference.refinedCallTypes(leftTypes, memberName, call,
        node -> inferInternal(node, ctx), ctx.documentContext.getFileType());
      if (xdtoTypes != null) {
        return xdtoTypes;
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
    // 2. Открытие формы по имени: тип конкретной формы точнее, чем обобщённый
    //    возвращаемый тип платформенной функции, поэтому проверяется до шага 3.
    var formType = formExpressionInference.openedFormType(ctx.documentContext, name.getText(), call);
    if (formType != null) {
      return formType;
    }
    // 2а. Обратное преобразование данных формы — та же причина: у обеих функций
    //     объявленный возврат обобщённый (`Произвольный`), а прикладной тип известен
    //     из аргументов вызова либо из объявления реквизита.
    var convertedValue = formExpressionInference.convertedValueType(
      ctx.documentContext, name.getText(), call, null);
    if (convertedValue != null) {
      return convertedValue;
    }
    // 3. Платформенная глобальная функция (СтрНайти и т.п.) — через
    //    GlobalScopeProvider (полный MemberDescriptor с TypeSet, включая union).
    var globalReturn = scopeMemberTypeResolver.globalFunctionType(ctx.documentContext, name.getText());
    if (!globalReturn.isEmpty()) {
      return globalReturn;
    }
    // 4. Неквалифицированный вызов платформенного метода self-типа модуля.
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
    var byName = formExpressionInference.memberByLiteralName(
      ctx.documentContext, leftTypes, OpenDataObjectInference.stringLiteralOf(node.getRight()));
    if (byName != null) {
      return byName;
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
   * Если же {@code ret} — коллекция, элемент которой и есть такая строка
   * ({@code Дерево.Строки}), уточнение переезжает внутрь этой коллекции.
   * Иначе — обычный {@link TypeSet#of(TypeRef)}.
   */
  private TypeSet enrichReturnRefWithElementFields(TypeRef ret, TypeSet elementSet) {
    if (elementSet.refs().contains(ret)) {
      return TypeSet.of(ret).withFields(ret, elementSet.getLocalFields(ret));
    }
    var carried = TypeSet.EMPTY;
    for (var elementRef : typeRegistry.getDefaultElementTypes(ret).refs()) {
      if (elementSet.refs().contains(elementRef)) {
        carried = carried.union(
          TypeSet.of(elementRef).withFields(elementRef, elementSet.getLocalFields(elementRef)));
      }
    }
    return carried.isEmpty() ? TypeSet.of(ret) : TypeSet.of(ret).withElement(ret, carried);
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
   * Возвращаемые типы вызванного метода с защитой от цикла инференса. Результат всегда
   * присутствует, даже если сам тип — пустой {@link TypeSet#EMPTY}: честно невыведенный
   * тип нельзя подменять self-свойством того же имени.
   */
  private TypeSet methodReturnType(MethodSymbol method, InferenceContext ctx) {
    if (!ctx.visited.add(method)) {
      return TypeSet.EMPTY;
    }
    try {
      return symbolTypeIndex.getDeclaredReturnTypes(method);
    } finally {
      ctx.visited.remove(method);
    }
  }

  /**
   * Тип переменной в точке использования, рассчитанный по потоку управления тела:
   * присваивание перекрывает прежний тип, в точках слияния путей типы объединяются.
   *
   * @param variable переменная.
   * @param terminal терминал использования.
   * @param ctx      контекст текущего инференса.
   * @return тип в этой точке; пустой набор, если переменная не из этого документа либо
   *     расчёт сорвался. Отсутствие присваиваний расчёту не мешает — тип такой переменной
   *     есть входной факт по всему телу.
   */
  private TypeSet flowTypeAt(VariableSymbol variable, TerminalNode terminal, InferenceContext ctx) {
    var owner = variable.getOwner();
    if (!owner.getUri().equals(ctx.documentContext.getUri())) {
      // Переменная из другого документа: чужое дерево разбора не читаем, берём
      // объявленное о ней — оно есть в самом символе.
      return declaredTypes(variable);
    }
    if (!(terminal.getParent() instanceof ParserRuleContext use)) {
      return TypeSet.EMPTY;
    }
    // Повторный вход по той же переменной здесь не отсекается: у `Х = Х + 1` правая часть
    // спрашивает тип посреди расчёта того же тела, и ответ у расчёта есть — окружение перед
    // текущим оператором. Зацикливания не будет: строящееся окружение отвечает чтением из
    // карты, не запуская расчёт заново.
    try {
      var byFlow = variableFlowAnalyzer.typeAt(owner, use, variable, flowInputs(variable, ctx));
      if (byFlow == null) {
        // Обращение к переменной есть, а расчёт его не разместил — это дефект расчёта, а не
        // особенность кода 1С. Подменять ответ нечем: любая подмена скрыла бы дефект.
        LOGGER.error("Обращение к переменной {} (объявлена {}) не размещено в расчёте по потоку: {} {}",
          variable.getName(), at(variable.getSelectionRange().getStart()),
          owner.getUri(), at(Ranges.create(use).getStart()));
        return TypeSet.EMPTY;
      }
      return byFlow;
    } catch (StackOverflowError | RuntimeException e) {
      LOGGER.error("Расчёт типа по потоку сорвался на переменной {} (объявлена {}): {} {}",
        variable.getName(), at(variable.getSelectionRange().getStart()),
        owner.getUri(), at(Ranges.create(use).getStart()), e);
      return TypeSet.EMPTY;
    }
  }

  /**
   * Тип переменной в точке, на которую указывает ссылка, — с учётом того, какие
   * присваивания и изменения на месте уже случились на путях к ней.
   *
   * @param reference ссылка на переменную — несёт и документ, и позицию.
   * @return тип в этой точке; пустой набор, если ссылка не на переменную своего документа
   *     либо расчёт по потоку сорвался — тогда срыв пишется в журнал ошибкой.
   */
  public TypeSet inferVariableAt(Reference reference) {
    if (!(reference.getSourceDefinedSymbol().orElse(null) instanceof VariableSymbol variable)) {
      return TypeSet.EMPTY;
    }
    if (!variable.getOwner().getUri().equals(reference.uri())) {
      // Переменная объявлена в другом документе: считать её по коду означало бы читать
      // чужое дерево разбора, а этого мы не делаем. Остаётся объявленное о ней — оно
      // берётся из самого символа.
      return declaredTypes(variable);
    }
    return inferVariableAt(
      variable,
      reference.selectionRange().getStart(),
      // Ссылка на само присваивание спрашивает про тип после него, а не до.
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
   * @return тип в этой точке; пустой набор, если расчёт по потоку сорвался — тогда срыв
   *     пишется в журнал ошибкой.
   */
  public TypeSet inferVariableAt(VariableSymbol variable, Position position) {
    return inferVariableAt(variable, position, false);
  }

  /**
   * Тип переменной в точке с уточнением, стоит ли точка на самом присваивании.
   * <p>
   * Точки исполнения в позиции может и не быть — так спрашивают про объявление
   * ({@code Перем Кэш;} оператором не является). Тогда ответ даётся по всей области
   * видимости переменной: то, с чем она эту область покидает.
   *
   * @param variable     переменная.
   * @param position     точка в теле, для которой нужен тип.
   * @param atDefinition стоит ли точка на присваивании: тогда берётся тип после него.
   * @return тип в этой точке; пустой набор, если расчёт по потоку сорвался — тогда срыв
   *     пишется в журнал ошибкой.
   */
  private TypeSet inferVariableAt(VariableSymbol variable, Position position, boolean atDefinition) {
    var owner = variable.getOwner();
    var ctx = new InferenceContext(owner);
    try {
      var inputs = flowInputs(variable, ctx);
      var atPoint = variableFlowAnalyzer.typeAt(owner, position, atDefinition, variable, inputs);
      return atPoint == null
        ? variableFlowAnalyzer.typesAcrossScope(owner, position, variable, inputs)
        : atPoint;
    } catch (StackOverflowError | RuntimeException e) {
      LOGGER.error("Расчёт типа по потоку сорвался на переменной {} (объявлена {}): {} {}",
        variable.getName(), at(variable.getSelectionRange().getStart()),
        owner.getUri(), at(position), e);
      return TypeSet.EMPTY;
    }
  }

  /**
   * Позиция в записи «строка:колонка», считая от единицы, — как её показывает редактор.
   *
   * @param position позиция, считающая от нуля.
   * @return запись позиции для журнала.
   */
  private static String at(Position position) {
    return (position.getLine() + 1) + ":" + (position.getCharacter() + 1);
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
    // Объявленное о переменной расчёт спрашивает многократно — на входе в тело, в точках
    // слияния и при возврате к объединению по области видимости. У переменной модуля за
    // ответом стоит обход индекса ссылок, поэтому он запоминается на время запроса.
    Map<VariableSymbol, TypeSet> declaredByVariable = new HashMap<>();
    Function<VariableSymbol, TypeSet> declaredOf = (VariableSymbol target) -> {
      var cached = declaredByVariable.get(target);
      if (cached != null) {
        return cached;
      }
      var computed = declaredTypes(target);
      declaredByVariable.put(target, computed);
      return computed;
    };
    // Операторы-мутаторы разбираются лениво и по одному разу на переменную: за ними стоит
    // обход индекса вызовов, а при готовом окружении в кэше они не нужны вовсе.
    Map<VariableSymbol, Lazy<Map<Position, BSLParser.CallStatementContext>>> callsByVariable = new HashMap<>();
    Function<VariableSymbol, Map<Position, BSLParser.CallStatementContext>> callsOf = target ->
      callsByVariable
        .computeIfAbsent(target, key -> new Lazy<>(() -> openDataObjectInference.mutatorsOf(key)))
        .getOrCompute();
    // Присваивание вида элементу формы — тоже изменение типа на месте, только записанное
    // не вызовом, а присваиванием свойству; разбирается так же лениво.
    Map<VariableSymbol, Lazy<Map<Position, BSLParser.AssignmentContext>>> kindsByVariable = new HashMap<>();
    Function<VariableSymbol, Map<Position, BSLParser.AssignmentContext>> kindsOf = target ->
      kindsByVariable
        .computeIfAbsent(target, key -> new Lazy<>(() -> formExpressionInference.kindAssignmentsOf(key)))
        .getOrCompute();
    var owner = variable.getOwner();
    return new VariableFlowAnalyzer.FlowInputs(
      ctx.flowSession,
      // Тот же критерий, что у кэша выведенных типов переменных: вложенный расчёт
      // (внутри инференса другой переменной) мог быть усечён защитой от циклов,
      // и переиспользовать такой результат как самостоятельный нельзя.
      ctx.visited.size() <= 1,
      body -> variablesOfBody(owner, body),
      target -> target.getKind() == VariableKind.MODULE,
      declaredOf,
      this::definitionPositions,
      target -> mutationPositions(callsOf.apply(target), kindsOf.apply(target)),
      (target, statement, position) ->
        attachDefaultElementTypes(inferFromDefinition(owner, statement, position, ctx)),
      (target, position, incoming) -> applyMutation(target, position, incoming, ctx,
        callsOf.apply(target), kindsOf.apply(target)),
      narrowingCallback(owner)
    );
  }

  /**
   * Позиции всех изменений типа на месте — операторов-мутаторов и присваиваний вида.
   *
   * @param calls мутаторы по позициям.
   * @param kinds присваивания вида по позициям.
   * @return объединение позиций без повторов.
   */
  private static Collection<Position> mutationPositions(Map<Position, ?> calls, Map<Position, ?> kinds) {
    if (kinds.isEmpty()) {
      return calls.keySet();
    }
    Collection<Position> positions = new LinkedHashSet<>(calls.keySet());
    positions.addAll(kinds.keySet());
    return positions;
  }

  /**
   * Вклад одного изменения на месте: по позиции определяется, какого оно вида.
   *
   * @param variable переменная-получатель.
   * @param position позиция изменения.
   * @param incoming тип переменной перед ним.
   * @param ctx      контекст текущего инференса.
   * @param calls    мутаторы этой переменной по позициям.
   * @param kinds    присваивания вида этой переменной по позициям.
   * @return изменённый тип; исходный, если по позиции ничего не нашлось.
   */
  private TypeSet applyMutation(VariableSymbol variable, Position position, TypeSet incoming, InferenceContext ctx,
                                Map<Position, BSLParser.CallStatementContext> calls,
                                Map<Position, BSLParser.AssignmentContext> kinds) {
    var call = calls.get(position);
    if (call != null) {
      return openDataObjectInference.apply(variable, call, incoming, node -> inferInternal(node, ctx));
    }
    return formExpressionInference.applyKindAssignment(variable, kinds.get(position), incoming);
  }

  /**
   * Переменные, живущие в том же теле, что и заданная: расчёт по потоку считает их все
   * разом, одним поиском неподвижной точки.
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
    if (entry.isEmpty() && declaredByVar(variable) && !assignedBeforeAnyUse(variable)) {
      // Переменная, объявленная записью «Перем», до первого присваивания содержит
      // «Неопределено» — это её значение, а не отсутствие сведений о типе. Дальше по телу
      // присваивания его перекрывают, а в точке слияния путей он остаётся, если хотя бы
      // один путь до присваивания не дошёл.
      return TypeSet.of(UNDEFINED);
    }
    // Объявленному типу-коллекции нужен тип её элемента: «Для Каждого» по параметру,
    // чей тип объявлен комментарием, иначе не знает, что за строку он перебирает.
    return attachDefaultElementTypes(entry);
  }

  /**
   * Объявлена ли переменная записью {@code Перем} — в отличие от переменной, созданной
   * первым присваиванием, и от параметра метода.
   *
   * @param variable переменная.
   * @return {@code true}, если переменная объявлена записью {@code Перем}.
   */
  private static boolean declaredByVar(VariableSymbol variable) {
    var kind = variable.getKind();
    return kind == VariableKind.LOCAL || kind == VariableKind.MODULE || kind == VariableKind.GLOBAL;
  }

  /**
   * Есть ли у переменной модуля присваивание, которое заведомо выполняется раньше любого
   * обращения к ней.
   * <p>
   * Таким может быть тело, которое отрабатывает до всех прочих: тело модуля — раньше всех
   * его процедур, конструктор — при создании объекта, до того как к его полям кто-то
   * обратится. Но самого присваивания мало: оно должно случиться на любом пути через это
   * тело. Присваивание в одной ветке условия может не выполниться, а в обеих — выполнится
   * непременно, поэтому вопрос решается по графу потока управления, а не по вложенности
   * оператора в тексте.
   *
   * @param variable переменная.
   * @return {@code true}, если такое присваивание есть.
   */
  private boolean assignedBeforeAnyUse(VariableSymbol variable) {
    if (variable.getKind() != VariableKind.MODULE) {
      return false;
    }
    var owner = variable.getOwner();
    var symbolTree = owner.getSymbolTree();
    // Тела сравниваются по ссылке: узлы дерева разбора равенства по содержимому не имеют.
    Map<BSLParser.CodeBlockContext, List<Position>> positionsByBody = new IdentityHashMap<>();
    for (var position : definitionPositions(variable)) {
      var enclosingMethod = enclosingMethod(symbolTree, position);
      var runsBeforeAnyUse = enclosingMethod.isEmpty()
        || Methods.isOscriptClassConstructorName(enclosingMethod.get().getName());
      var body = runsBeforeAnyUse ? VariableFlowAnalyzer.bodyAt(owner, position) : null;
      if (body != null) {
        positionsByBody.computeIfAbsent(body, key -> new ArrayList<>()).add(position);
      }
    }
    return positionsByBody.entrySet().stream()
      .anyMatch(entry -> assignedOnEveryPath(owner, entry.getKey(), entry.getValue()));
  }

  /**
   * Метод, в теле которого стоит позиция.
   *
   * @param symbolTree дерево символов документа.
   * @param position   позиция в документе.
   * @return метод; пусто, если позиция вне методов — то есть в теле модуля.
   */
  private static Optional<MethodSymbol> enclosingMethod(SymbolTree symbolTree, Position position) {
    var symbol = symbolTree.getSymbolAtPosition(position);
    if (symbol instanceof MethodSymbol method) {
      return Optional.of(method);
    }
    return symbol.getRootParent(MethodSymbol.class).map(MethodSymbol.class::cast);
  }

  /**
   * Присваивается ли переменная на любом пути через тело.
   * <p>
   * Считается обходом графа потока управления от выхода назад: вершины с присваиванием
   * обход не проходит. Если так до входа добраться не удалось, значит всякий путь от входа
   * к выходу присваивание задевает.
   *
   * @param owner     документ с телом.
   * @param body      тело.
   * @param positions позиции присваиваний в этом теле.
   * @return {@code true}, если пути в обход присваиваний нет.
   */
  private boolean assignedOnEveryPath(
    DocumentContext owner,
    BSLParser.CodeBlockContext body,
    List<Position> positions
  ) {
    var graph = controlFlowGraphIndex.graphOf(owner, body, CfgBuildOptions.defaults());
    var entry = graph.getEntryPoint();
    if (assigns(entry, positions)) {
      return true;
    }
    Set<CfgVertex> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<CfgVertex> queue = new ArrayDeque<>();
    queue.add(graph.getExitPoint());
    visited.add(graph.getExitPoint());
    while (!queue.isEmpty()) {
      var vertex = queue.poll();
      for (var edge : graph.incomingEdgesOf(vertex)) {
        var previous = graph.getEdgeSource(edge);
        if (assigns(previous, positions) || !visited.add(previous)) {
          continue;
        }
        if (previous == entry) {
          return false;
        }
        queue.add(previous);
      }
    }
    return true;
  }

  /**
   * Присваивается ли переменная в этой вершине графа.
   *
   * @param vertex    вершина.
   * @param positions позиции присваиваний.
   * @return {@code true}, если хотя бы одно присваивание попадает в операторы вершины.
   */
  private static boolean assigns(@Nullable CfgVertex vertex, List<Position> positions) {
    if (!(vertex instanceof BasicBlockVertex block)) {
      return false;
    }
    return block.statements().stream()
      .anyMatch(statement -> positions.stream()
        .anyMatch(position -> Ranges.containsPosition(Ranges.create(statement), position)));
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
      return types.union(commentTypeResolver.ofAssignment(owner, assignment));
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
      result = result.union(commentTypeResolver.ofAssignment(owner, assignment.get()));
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
