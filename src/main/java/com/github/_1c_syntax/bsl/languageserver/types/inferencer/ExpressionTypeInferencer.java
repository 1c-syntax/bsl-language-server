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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.index.CallStatementByReceiverIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredVariableTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnComponentInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.ExtendsAnnotations;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.OScriptExtends;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.DescriptionTypes;
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
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import com.github._1c_syntax.utils.Lazy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

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
  private final CallStatementByReceiverIndex callStatementByReceiverIndex;
  private final TableCollectionInference tableCollectionInference;
  private final VariableFlowAnalyzer variableFlowAnalyzer;
  private final GuardConditionNarrowing guardConditionNarrowing;
  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;
  private final GlobalScopeProvider globalScopeProvider;
  private final AutumnComponentInferencer autumnComponentInferencer;
  private final EventContractsIndex eventContractsIndex;
  private final OScriptExtends oScriptExtends;

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
      return inferModuleAsType(module);
    }
    return TypeSet.EMPTY;
  }

  /**
   * Имя модуля в выражении ссылается на тип-namespace с экспортами как членами
   * (общий модуль {@code ОбщегоНазначения}, модуль менеджера/объекта, библиотечный
   * OneScript-модуль). Тип берётся из единого обратного индекса URI→тип в
   * {@link GlobalScopeProvider#moduleTypeRefByUri(java.net.URI)}, который наполняют
   * провайдеры регистрации модулей. Инференсер больше не обращается к
   * подсистемным индексам (oscript/configuration) напрямую.
   */
  private TypeSet inferModuleAsType(ModuleSymbol module) {
    return globalScopeProvider.moduleTypeRefByUri(module.getOwner().getUri())
      .map(TypeSet::of)
      .orElse(TypeSet.EMPTY);
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
    return inferImplicitExtendsParentField(text, ctx)
      .or(() -> selfMemberReturnTypes(ctx, text, MemberKind.PROPERTY))
      .orElseGet(() -> globalPropertyReturnTypes(text, ctx));
  }

  /**
   * Неявное поле родителя библиотеки extends: фреймворк создаёт _ОбъектРодитель
   * в собранном объекте, в исходниках наследника оно не объявлено — типизируем
   * его родительским классом, чтобы _ОбъектРодитель.МетодБазы() резолвился.
   *
   * @return тип родителя; {@code Optional.empty()}, если имя — не это неявное
   *     поле, файл не OScript-класс, либо тип родителя не выводится (тогда
   *     резолв продолжается self-свойством/глобальным свойством).
   */
  private Optional<TypeSet> inferImplicitExtendsParentField(String text, InferenceContext ctx) {
    if (!ExtendsAnnotations.IMPLICIT_PARENT_FIELD.equalsIgnoreCase(text)
      || ctx.documentContext.getFileType() != FileType.OS) {
      return Optional.empty();
    }
    var parent = parentClassType(ctx.documentContext);
    return parent.isEmpty() ? Optional.empty() : Optional.of(parent);
  }

  /**
   * Глобальная область: платформенные глобалы, library-модули, common-модули —
   * все приходят как глобальные свойства. Только {@code PROPERTY}: голое имя
   * глобальной функции ({@code METHOD}) — не значение, а имена типов для
   * {@code Новый} (Структура) вообще не члены контекста.
   */
  private TypeSet globalPropertyReturnTypes(String text, InferenceContext ctx) {
    return globalScopeProvider.globalProperty(text, ctx.documentContext.getFileType())
      .map(MemberDescriptor::returnTypes)
      .filter(types -> types.refs().stream().anyMatch(ref -> !ref.equals(TypeRef.UNKNOWN)))
      .orElse(TypeSet.EMPTY);
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
    if (isStructureLike(typeName)) {
      base = applyStructureConstructorKeys(base, constructor, ctx);
    }
    if (isTypeDescriptionType(typeName)) {
      base = applyTypeDescriptionConstructorTypes(base, constructor, ctx);
    }
    return base;
  }

  /**
   * Для записи {@code Новый ОписаниеТипов("Число[,Строка,...]")}: распарсить
   * первый строковый аргумент в имена типов, зарезолвить через
   * {@link TypeRegistry} и подвесить набор к {@link TypeRef} «ОписаниеТипов»
   * через {@link TypeSet#withElement}. Это позволяет потребителям
   * (например, {@link #accumulateValueTableColumnFields}) забрать «содержимое»
   * описания типов прямо из TypeSet без повторного парсинга AST.
   */
  private TypeSet applyTypeDescriptionConstructorTypes(
    TypeSet base,
    ConstructorCallNode constructor,
    InferenceContext ctx
  ) {
    var args = constructor.arguments();
    if (args.isEmpty() || base.refs().isEmpty()) {
      return base;
    }
    var literal = extractStringLiteral(args.get(0));
    if (literal == null) {
      return base;
    }
    var fileType = ctx.documentContext.getFileType();
    var refs = new ArrayList<TypeRef>();
    for (var raw : literal.split(",")) {
      var name = raw.trim();
      if (name.isEmpty()) {
        continue;
      }
      typeRegistry.resolve(name, fileType).ifPresent(refs::add);
    }
    if (refs.isEmpty()) {
      return base;
    }
    var headRef = base.refs().iterator().next();
    return base.withElement(headRef, TypeSet.of(refs));
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
   * Для записи {@code Новый Структура("К1, К2", v1, v2)}: распарсить первый
   * строковый аргумент в имена ключей и подвесить к каждому ключу TypeSet
   * соответствующего value-аргумента через {@link TypeSet#withField(TypeRef, String, TypeSet)}.
   */
  private TypeSet applyStructureConstructorKeys(
    TypeSet base,
    ConstructorCallNode constructor,
    InferenceContext ctx
  ) {
    var args = constructor.arguments();
    if (args.isEmpty() || base.refs().isEmpty()) {
      return base;
    }
    var keyLiteral = extractStringLiteral(args.get(0));
    if (keyLiteral == null) {
      return base;
    }
    var keys = keyLiteral.split(",");
    var headRef = base.refs().iterator().next();
    var result = base;
    for (int i = 0; i < keys.length; i++) {
      var keyName = keys[i].trim();
      if (keyName.isEmpty()) {
        continue;
      }
      int valueArgIndex = i + 1;
      TypeSet valueTypes;
      if (valueArgIndex < args.size()) {
        valueTypes = inferInternal(args.get(valueArgIndex), ctx);
      } else {
        valueTypes = TypeSet.of(UNDEFINED);
      }
      if (!valueTypes.isEmpty()) {
        result = result.withField(headRef, keyName, valueTypes);
      }
    }
    return result;
  }

  @Nullable
  static String extractStringLiteral(BslExpression node) {
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

  private static boolean isStructureLike(String typeName) {
    var lower = typeName.toLowerCase(Locale.ROOT);
    return lower.equals("структура") || lower.equals("structure")
      || lower.equals("фиксированнаяструктура") || lower.equals("fixedstructure");
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
      if (isStructureOrMapLike(ref.qualifiedName())) {
        return TypeSet.EMPTY;
      }
    }
    var result = TypeSet.EMPTY;
    for (var ref : leftTypes.refs()) {
      result = result.union(leftTypes.getElementTypes(ref));
    }
    return result;
  }

  /**
   * Платформенные KV-коллекции, у которых {@code .Вставить("Имя", значение)} /
   * {@code .Insert(...)} даёт строковый ключ → значение. Сюда же подмешивается
   * {@link #isStructureLike} (Структура и ФиксированнаяСтруктура).
   */
  private static boolean isStructureOrMapLike(String typeName) {
    if (isStructureLike(typeName)) {
      return true;
    }
    var lower = typeName.toLowerCase(Locale.ROOT);
    return lower.equals("соответствие") || lower.equals("map")
      || lower.equals("фиксированноесоответствие") || lower.equals("fixedmap");
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
    var globalReturn = globalFunctionReturnTypes(name.getText(), ctx);
    if (!globalReturn.isEmpty()) {
      return globalReturn;
    }
    // 3. Неквалифицированный вызов платформенного метода self-типа модуля.
    //    Тот же self-тип, что и в inferIdentifier для свойств, здесь —
    //    MemberKind.METHOD.
    return selfMemberReturnTypes(ctx, name.getText(), MemberKind.METHOD)
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Возвращаемые типы self-члена текущего модуля с заданными видом и именем.
   * Продублирован через реестр напрямую, потому что инференсер не может зависеть от
   * фасада {@code TypeService} (тот сам делегирует инференсеру). В отличие от
   * {@code TypeService#findSelfMember} берёт self-тип только из кэша
   * {@code moduleTypeRefByUri}, без fallback на метаданные: инференс идёт уже после
   * наполнения кэша и при построении дерева символов не вызывается.
   *
   * @return типы значения self-члена; empty, если self-типа у документа нет
   *     или член с таким видом/именем не найден.
   */
  private Optional<TypeSet> selfMemberReturnTypes(InferenceContext ctx, String name, MemberKind kind) {
    return globalScopeProvider.moduleTypeRefByUri(ctx.documentContext.getUri())
      .flatMap(ref -> typeRegistry.findMember(ref, kind, name, ctx.documentContext.getFileType()))
      .map(MemberDescriptor::returnTypes);
  }

  /**
   * Резолв возвращаемых типов глобальной функции по имени. Используется как
   * fallback, когда {@code ReferenceResolver} не дал ссылку или дал ссылку
   * без типа.
   */
  private TypeSet globalFunctionReturnTypes(String methodName, InferenceContext ctx) {
    if (methodName == null || methodName.isBlank()) {
      return TypeSet.EMPTY;
    }
    return globalScopeProvider.globalFunction(methodName, ctx.documentContext.getFileType())
      .map(MemberDescriptor::returnTypes)
      .filter(types -> !types.isEmpty())
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
    var kvFields = collectKeyValueFields(leftTypes);
    if (!kvFields.isEmpty()) {
      var keyName = extractStringLiteral(node.getRight());
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

  /**
   * Собрать union localFields по всем ref'ам набора. Источник —
   * {@link #accumulateStructureInsertFields} (Структура/Соответствие)
   * и {@link #applyStructureConstructorKeys} (Структура с key-list-конструктором).
   */
  private static Map<String, TypeSet> collectKeyValueFields(TypeSet leftTypes) {
    var merged = new LinkedHashMap<String, TypeSet>();
    for (var ref : leftTypes.refs()) {
      var fields = leftTypes.getLocalFields(ref);
      for (var entry : fields.entrySet()) {
        merged.merge(entry.getKey(), entry.getValue().types(), TypeSet::union);
      }
    }
    return merged;
  }

  /**
   * Типы поля «открытого» объекта данных (Структура, ТаблицаЗначений с описанными
   * ключами), объявленного на самом наборе типов. Смотрится раньше членов типа:
   * задокументированное поле точнее одноимённого дефолтного члена платформы.
   *
   * @param leftTypes  типы получателя.
   * @param memberName имя поля.
   * @return типы поля; {@link TypeSet#EMPTY}, если такого поля не объявлено.
   */
  private static TypeSet declaredFieldTypes(TypeSet leftTypes, String memberName) {
    var result = TypeSet.EMPTY;
    for (var leftType : leftTypes.refs()) {
      for (var entry : leftTypes.getLocalFields(leftType).entrySet()) {
        if (entry.getKey().equalsIgnoreCase(memberName)) {
          result = result.union(entry.getValue().types());
        }
      }
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
      var fromLocalFields = declaredFieldTypes(leftTypes, memberName);
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
   * Тело инференса переменной без кэш-обвязки. По мере объединения присваиваний
   * публикует растущий {@code acc} в {@link InferenceContext#inProgress}, чтобы
   * self-reference (например, {@code Строка = Строка + "..."}) резолвился в уже
   * известный тип из предыдущих присваиваний, а не в {@link TypeSet#EMPTY}.
   */
  private TypeSet inferVariableInternal(VariableSymbol variable, InferenceContext ctx) {
    var owner = variable.getOwner();
    TypeSet acc = TypeSet.EMPTY;
    Set<Position> visitedPositions = new HashSet<>();

    if (variable.getKind() == VariableKind.PARAMETER) {
      acc = acc.union(declaredParameterTypes(variable));
    }

    acc = acc.union(typesFromVariableTrailingComment(variable));
    ctx.inProgress.put(variable, acc);

    var declarationStart = variable.getSelectionRange().getStart();
    if (visitedPositions.add(declarationStart)) {
      acc = acc.union(inferFromDefinitionPosition(owner, declarationStart, ctx));
      ctx.inProgress.put(variable, acc);
    }
    for (var occurrence : referenceIndex.getReferencesTo(variable)) {
      if (occurrence.occurrenceType() != OccurrenceType.DEFINITION) {
        continue;
      }
      var start = occurrence.selectionRange().getStart();
      if (visitedPositions.add(start)) {
        acc = acc.union(inferFromDefinitionPosition(owner, start, ctx));
        ctx.inProgress.put(variable, acc);
      }
    }
    acc = acc.union(autumnInjectedType(variable));
    acc = acc.union(extendsParentFieldType(variable));
    acc = attachDefaultElementTypes(acc);
    acc = accumulateStructureInsertFields(variable, acc, ctx);
    acc = accumulateValueTableColumnFields(variable, acc, ctx);
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
   *     общий путь: переменная модуля (её меняют из разных методов), использование в
   *     другом документе, повторный вход по той же переменной, отсутствие присваиваний
   *     либо неразмещаемое в графе присваивание.
   */
  @Nullable
  private TypeSet flowTypeAt(VariableSymbol variable, TerminalNode terminal, InferenceContext ctx) {
    if (variable.getKind() == VariableKind.MODULE || ctx.flowInProgress.contains(variable)) {
      return null;
    }
    var owner = variable.getOwner();
    if (!owner.getUri().equals(ctx.documentContext.getUri())) {
      return null;
    }
    if (!(terminal.getParent() instanceof ParserRuleContext use)) {
      return null;
    }
    ctx.flowInProgress.add(variable);
    try {
      return variableFlowAnalyzer.typeAt(owner, use, flowInputs(variable, ctx));
    } catch (RuntimeException e) {
      // Расчёт по потоку — уточнение, а не единственный источник типа. Если он сорвался
      // (например, граф не построился на неожиданной форме кода), тип должен дать прежний
      // путь, а не общий перехват в infer(), который обнулил бы всё выражение целиком.
      LOGGER.debug("Расчёт типа по потоку не удался для переменной {}", variable.getName(), e);
      return null;
    } finally {
      ctx.flowInProgress.remove(variable);
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
    if (!(reference.getSourceDefinedSymbol().orElse(null) instanceof VariableSymbol variable)) {
      return null;
    }
    var owner = variable.getOwner();
    if (variable.getKind() == VariableKind.MODULE || !owner.getUri().equals(reference.uri())) {
      return null;
    }
    var ctx = new InferenceContext(owner);
    ctx.flowInProgress.add(variable);
    try {
      return variableFlowAnalyzer.typeAt(
        owner,
        reference.selectionRange().getStart(),
        reference.occurrenceType() == OccurrenceType.DEFINITION,
        flowInputs(variable, ctx)
      );
    } catch (StackOverflowError | RuntimeException e) {
      return null;
    } finally {
      ctx.flowInProgress.remove(variable);
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
    // Оба набора считаются лениво: за местами присваиваний стоит обход индекса ссылок с
    // поиском символа по позиции на каждое обращение к переменной, и это самая дорогая
    // часть подготовки. При готовом ответе в кэше она не нужна вовсе.
    var mutationCalls = new Lazy<>(() -> mutationCalls(variable));
    return new VariableFlowAnalyzer.FlowInputs(
      variable,
      // Тот же критерий, что у кэша выведенных типов переменных: вложенный расчёт
      // (внутри инференса другой переменной) мог быть усечён защитой от циклов,
      // и переиспользовать такой результат как самостоятельный нельзя.
      ctx.visited.size() <= 1,
      () -> flowEntryFact(variable),
      () -> definitionPositions(variable),
      () -> mutationCalls.getOrCompute().keySet(),
      (statement, position) ->
        attachDefaultElementTypes(inferFromDefinition(owner, statement, position, ctx)),
      (position, incoming) -> applyMutation(variable, mutationCalls.getOrCompute().get(position), incoming, ctx),
      narrowingCallback(variable, owner)
    );
  }

  /**
   * Колбэк сужения по охраняющим условиям с запоминанием разбора: расчёт идёт проходами и
   * спрашивает одни и те же условия многократно, а разбор тянет резолв переменной через
   * индекс — самую дорогую часть шага.
   *
   * @param variable переменная, тип которой сужается.
   * @param owner    документ с условиями.
   * @return колбэк для {@link VariableFlowAnalyzer}.
   */
  private VariableFlowAnalyzer.GuardNarrowing narrowingCallback(VariableSymbol variable, DocumentContext owner) {
    return (condition, whenTrue, incoming) ->
      guardConditionNarrowing.compile(condition, owner).apply(variable, whenTrue, incoming);
  }

  /**
   * Операторы, меняющие тип переменной на месте, по позиции их начала: добавление поля
   * структуры или соответствия и добавление колонки таблицы значений.
   *
   * @param variable переменная.
   * @return операторы-мутаторы по позициям, в порядке следования в документе.
   */
  private Map<Position, BSLParser.CallStatementContext> mutationCalls(VariableSymbol variable) {
    var owner = variable.getOwner();
    var ast = safeGetOwnerAst(owner);
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
   * Применить к типу переменной одно изменение на месте: добавленное поле структуры
   * либо добавленную колонку таблицы значений.
   *
   * @param variable переменная.
   * @param call     оператор-мутатор; {@code null}, если по позиции ничего не нашлось.
   * @param incoming тип переменной перед оператором.
   * @param ctx      контекст текущего инференса.
   * @return изменённый тип; исходный, если оператор к этому типу неприменим.
   */
  private TypeSet applyMutation(
    VariableSymbol variable,
    BSLParser.@Nullable CallStatementContext call,
    TypeSet incoming,
    InferenceContext ctx
  ) {
    if (call == null || incoming.isEmpty()) {
      return incoming;
    }
    var scope = variable.getScope();
    var scopeRange = scope == null ? null : scope.getRange();
    var variableName = variable.getName();

    var structureRef = headRefOf(incoming, ExpressionTypeInferencer::isStructureOrMapLike);
    if (structureRef != null) {
      var field = insertedStructureField(call, variableName, scopeRange, ctx);
      if (field != null && !field.types().isEmpty()) {
        return incoming.withField(structureRef, field.name(), field.types());
      }
    }
    var tableRef = headRefOf(incoming, ExpressionTypeInferencer::isValueTableLike);
    if (tableRef != null) {
      var column = addedColumn(call, variableName, scopeRange, ctx);
      if (column != null) {
        var rowRef = valueTableRowRef(variable.getOwner());
        return incoming.withElement(tableRef, TypeSet.of(rowRef).withField(rowRef, column.name(), column.types()));
      }
    }
    return incoming;
  }

  /**
   * Первый тип набора, подходящий под условие.
   *
   * @param types     набор типов.
   * @param predicate условие отбора по полному имени типа.
   * @return подходящий тип либо {@code null}.
   */
  @Nullable
  private static TypeRef headRefOf(TypeSet types, Predicate<String> predicate) {
    for (var ref : types.refs()) {
      if (predicate.test(ref.qualifiedName())) {
        return ref;
      }
    }
    return null;
  }

  /**
   * Тип строки таблицы значений — на нём моделируются колонки.
   *
   * @param owner документ, для языка которого резолвится тип.
   * @return ссылка на тип строки таблицы значений.
   */
  private TypeRef valueTableRowRef(DocumentContext owner) {
    return typeRegistry.resolve(TableCollectionInference.VALUE_TABLE_ROW, owner.getFileType())
      .orElseGet(() -> typeRegistry.intern(TypeKind.PLATFORM, TableCollectionInference.VALUE_TABLE_ROW));
  }

  /**
   * Тип переменной до первого присваивания: то, что известно из объявления, а не из кода.
   *
   * @param variable переменная.
   * @return типы на входе в тело; пустой набор, если ничего не объявлено.
   */
  private TypeSet flowEntryFact(VariableSymbol variable) {
    var entry = TypeSet.EMPTY;
    if (variable.getKind() == VariableKind.PARAMETER) {
      entry = entry.union(declaredParameterTypes(variable));
    }
    return entry
      .union(typesFromVariableTrailingComment(variable))
      .union(autumnInjectedType(variable))
      .union(extendsParentFieldType(variable));
  }

  /**
   * Позиции всех присваиваний переменной: объявление (первое присваивание содержится в
   * самом символе) плюс {@code DEFINITION}-вхождения из индекса ссылок.
   *
   * @param variable переменная.
   * @return позиции присваиваний без повторов.
   */
  private List<Position> definitionPositions(VariableSymbol variable) {
    var positions = new ArrayList<Position>();
    positions.add(variable.getSelectionRange().getStart());
    for (var occurrence : referenceIndex.getReferencesTo(variable)) {
      if (occurrence.occurrenceType() == OccurrenceType.DEFINITION) {
        var start = occurrence.selectionRange().getStart();
        if (!positions.contains(start)) {
          positions.add(start);
        }
      }
    }
    return positions;
  }

  /**
   * Тип внедряемой через {@code &Пластилин} зависимости фреймворка «ОСень».
   * Аннотации несёт сам символ — и поле модуля, и параметр конструктора/завязи
   * (см. {@code VariableSymbolComputer}).
   */
  private TypeSet autumnInjectedType(VariableSymbol variable) {
    var kind = variable.getKind();
    if (kind != VariableKind.MODULE && kind != VariableKind.PARAMETER) {
      return TypeSet.EMPTY;
    }
    return autumnComponentInferencer.inferInjectedType(
      variable.getAnnotations(), variable.getName(), variable.getOwner().getFileType());
  }

  /**
   * Тип поля-держателя родителя библиотеки {@code extends}: поле, помеченное
   * {@code &Родитель} (явный держатель), либо неявное поле
   * {@code _ОбъектРодитель}. Типом становится родительский класс, объявленный
   * через {@code &Расширяет} (в т.ч. через мета-аннотации). Так
   * {@code Родитель.МетодБазы()} даёт автодополнение/hover по членам родителя.
   */
  private TypeSet extendsParentFieldType(VariableSymbol variable) {
    if (variable.getKind() != VariableKind.MODULE) {
      return TypeSet.EMPTY;
    }
    var owner = variable.getOwner();
    if (owner.getFileType() != FileType.OS || !oScriptExtends.isParentHolder(variable)) {
      return TypeSet.EMPTY;
    }
    return parentClassType(owner);
  }

  /**
   * Тип родительского класса {@code .os}-документа (через {@code &Расширяет} /
   * мета-аннотации), либо {@link TypeSet#EMPTY}, если наследование не объявлено
   * или родитель не разрешается в зарегистрированный тип.
   */
  private TypeSet parentClassType(DocumentContext documentContext) {
    return oScriptExtends.parentClassName(documentContext)
      .flatMap(name -> typeRegistry.resolve(name, FileType.OS))
      .map(TypeSet::of)
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Накопить поля «открытой» структуры/соответствия по mutation-вызовам
   * {@code X.Вставить("Имя", значение)} / {@code X.Insert(...)} в области видимости
   * переменной. Соответствует EDT-стандарту code typification: значения,
   * присваиваемые ключам, сужают тип объекта. Работает для Структуры,
   * ФиксированнойСтруктуры, Соответствия и ФиксированногоСоответствия —
   * у всех у них {@code .Вставить(...)} даёт строковый ключ → значение.
   */
  private TypeSet accumulateStructureInsertFields(
    VariableSymbol variable,
    TypeSet base,
    InferenceContext ctx
  ) {
    if (base.refs().isEmpty()) {
      return base;
    }
    TypeRef headRef = null;
    for (var ref : base.refs()) {
      if (isStructureOrMapLike(ref.qualifiedName())) {
        headRef = ref;
        break;
      }
    }
    if (headRef == null) {
      return base;
    }
    var owner = variable.getOwner();
    var ast = safeGetOwnerAst(owner);
    if (ast == null) {
      return base;
    }
    var scope = variable.getScope();
    var scopeRange = scope == null ? null : scope.getRange();
    var variableName = variable.getName();

    var result = base;
    for (var call : callStatementByReceiverIndex.byReceiver(owner.getUri(), ast, variableName)) {
      var field = insertedStructureField(call, variableName, scopeRange, ctx);
      if (field != null && !field.types().isEmpty()) {
        result = result.withField(headRef, field.name(), field.types());
      }
    }
    return result;
  }

  /**
   * Поле структуры/соответствия, добавляемое вызовом {@code X.Вставить("Ключ", Значение)}
   * для нужного ресивера в области видимости, либо {@code null}, если вызов не подходит.
   *
   * @param call         разбираемый callStatement.
   * @param variableName имя переменной-ресивера.
   * @param scopeRange   диапазон области видимости переменной (или {@code null}).
   * @param ctx          контекст инференса для вывода типа значения.
   * @return добавляемое поле (имя + типы значения) либо {@code null}.
   */
  @Nullable
  private KeyedTypes insertedStructureField(
    BSLParser.CallStatementContext call,
    String variableName,
    @Nullable Range scopeRange,
    InferenceContext ctx
  ) {
    var params = mutationCallParams(
      call, variableName, scopeRange, extractInsertReceiverName(call), ExpressionTypeInferencer::isInsertMethodName);
    if (params == null) {
      return null;
    }
    var keyName = Optional.ofNullable(params.get(0).expression())
      .map(ExpressionTypeInferencer::extractStringLiteralText)
      .orElse(null);
    if (keyName == null || keyName.isBlank()) {
      return null;
    }
    TypeSet valueTypes;
    if (params.size() >= 2 && params.get(1).expression() != null) {
      var valueExpr = ExpressionTreeBuildingVisitor.buildExpressionTree(params.get(1).expression());
      valueTypes = valueExpr == null ? TypeSet.EMPTY : inferInternal(valueExpr, ctx);
    } else {
      valueTypes = TypeSet.of(UNDEFINED);
    }
    return new KeyedTypes(keyName.trim(), valueTypes);
  }

  /**
   * Накопить «колонки» открытой {@code ТаблицаЗначений} по mutation-вызовам
   * {@code X.Колонки.Добавить("Имя", Тип)} / {@code X.Columns.Add(...)}
   * в области видимости переменной. Колонки моделируются как
   * {@code localFields} на типе строки ({@code СтрокаТаблицыЗначений}),
   * который привязывается к ТЗ через {@link TypeSet#withElement} — поэтому
   * после {@code Для Каждого Строка Из ТЗ} или {@code ТЗ[0]} hover и
   * автокомплит будут видеть {@code Строка.Имя} как поле известного типа.
   */
  private TypeSet accumulateValueTableColumnFields(
    VariableSymbol variable,
    TypeSet base,
    InferenceContext ctx
  ) {
    if (base.refs().isEmpty()) {
      return base;
    }
    TypeRef headRef = null;
    for (var ref : base.refs()) {
      if (isValueTableLike(ref.qualifiedName())) {
        headRef = ref;
        break;
      }
    }
    if (headRef == null) {
      return base;
    }
    var owner = variable.getOwner();
    var ast = safeGetOwnerAst(owner);
    if (ast == null) {
      return base;
    }
    var scope = variable.getScope();
    var scopeRange = scope == null ? null : scope.getRange();
    var variableName = variable.getName();

    var rowRef = typeRegistry.resolve(TableCollectionInference.VALUE_TABLE_ROW, owner.getFileType())
      .orElseGet(() -> typeRegistry.intern(TypeKind.PLATFORM, TableCollectionInference.VALUE_TABLE_ROW));
    TypeSet rowSet = TypeSet.of(rowRef);
    boolean hasColumns = false;

    for (var call : callStatementByReceiverIndex.byReceiver(owner.getUri(), ast, variableName)) {
      var column = addedColumn(call, variableName, scopeRange, ctx);
      if (column != null) {
        rowSet = rowSet.withField(rowRef, column.name(), column.types());
        hasColumns = true;
      }
    }
    if (!hasColumns) {
      return base;
    }
    return base.withElement(headRef, rowSet);
  }

  /**
   * Колонка таблицы значений, добавляемая вызовом {@code X.Колонки.Добавить("Имя", Тип)}
   * для нужного ресивера в области видимости, либо {@code null}, если вызов не подходит.
   *
   * @param call         разбираемый callStatement.
   * @param variableName имя переменной-ресивера.
   * @param scopeRange   диапазон области видимости переменной (или {@code null}).
   * @param ctx          контекст инференса для вывода типов колонки.
   * @return добавляемая колонка (имя + типы) либо {@code null}.
   */
  @Nullable
  private KeyedTypes addedColumn(
    BSLParser.CallStatementContext call,
    String variableName,
    @Nullable Range scopeRange,
    InferenceContext ctx
  ) {
    var params = mutationCallParams(
      call, variableName, scopeRange, extractColumnsAddReceiverName(call), ExpressionTypeInferencer::isAddMethodName);
    if (params == null) {
      return null;
    }
    var keyName = Optional.ofNullable(params.get(0).expression())
      .map(ExpressionTypeInferencer::extractStringLiteralText)
      .orElse(null);
    if (keyName == null || keyName.isBlank()) {
      return null;
    }
    // Второй аргумент по сигнатуре платформы — объект ОписаниеТипов. Выводим тип выражения
    // через инференсер; если в нём есть ОписаниеТипов-ref, забираем его elementTypes
    // (туда applyTypeDescriptionConstructorTypes складывает имена типов из первого аргумента
    // конструктора). Любое другое выражение даст пустой набор — колонка останется Неопределено.
    var valueExpr = params.size() >= 2 ? params.get(1).expression() : null;
    var columnTypes = valueExpr == null ? TypeSet.EMPTY : extractColumnTypes(valueExpr, ctx);
    return new KeyedTypes(keyName.trim(), columnTypes.isEmpty() ? TypeSet.of(UNDEFINED) : columnTypes);
  }

  /**
   * Параметры mutation-вызова {@code X.Метод(...)}, если его базовый идентификатор совпадает
   * с {@code receiverName}, вызов попадает в область видимости и его метод проходит предикат.
   * Общий guard-префикс для {@link #insertedStructureField} и {@link #addedColumn}.
   *
   * @param call           разбираемый callStatement.
   * @param receiverName   имя переменной-ресивера.
   * @param scopeRange     диапазон области видимости (или {@code null} — без проверки).
   * @param actualReceiver фактический базовый идентификатор вызова (или {@code null}).
   * @param methodMatches  предикат на имя вызываемого метода.
   * @return непустой список параметров вызова либо {@code null}, если вызов не подходит.
   */
  @Nullable
  private static List<? extends BSLParser.CallParamContext> mutationCallParams(
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

  /**
   * Имя и типы поля/колонки, накапливаемых из mutation-вызова.
   *
   * @param name  имя ключа/колонки.
   * @param types типы значения/колонки.
   */
  private record KeyedTypes(String name, TypeSet types) {
  }

  private static boolean isValueTableLike(String typeName) {
    var lower = typeName.toLowerCase(Locale.ROOT);
    return lower.equals("таблицазначений") || lower.equals("valuetable");
  }

  /**
   * Извлечь типы колонки из второго аргумента {@code Колонки.Добавить("X", typesArg, ...)}.
   * <p>
   * Подход: строим {@link BslExpression} из AST второго аргумента и просим
   * инференсер вывести его тип. Если в результирующем {@link TypeSet} есть
   * {@link TypeRef}, идентифицируемый как {@code ОписаниеТипов} — берём у него
   * {@link TypeSet#getElementTypes(TypeRef) elementTypes}, куда
   * {@link #applyTypeDescriptionConstructorTypes} складывает типы из конструктора
   * {@code Новый ОписаниеТипов("Число,Строка")}.
   * <p>
   * Это даёт корректное поведение для всех альтернатив:
   * <ul>
   *   <li>{@code Новый ОписаниеТипов("Число")} → {@code Число};</li>
   *   <li>{@code Тип("Число")} → инференсер вернёт {@code Тип} (не ОписаниеТипов) → пусто;</li>
   *   <li>строковый литерал → инференсер вернёт {@code Строка} → пусто;</li>
   *   <li>переменная с типом ОписаниеТипов без литерального конструктора —
   *       inferred-ref совпадает, но elementTypes пуст → пусто.</li>
   * </ul>
   */
  private TypeSet extractColumnTypes(BSLParser.ExpressionContext expr, InferenceContext ctx) {
    var bslExpr = ExpressionTreeBuildingVisitor.buildExpressionTree(expr);
    if (bslExpr == null) {
      return TypeSet.EMPTY;
    }
    var inferred = inferInternal(bslExpr, ctx);
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

  private static boolean isTypeDescriptionType(String name) {
    return "ОписаниеТипов".equalsIgnoreCase(name) || "TypeDescription".equalsIgnoreCase(name);
  }

  private static boolean isAddMethodName(BSLParser.MethodCallContext methodCall) {
    var nameCtx = methodCall.methodName();
    if (nameCtx == null) {
      return false;
    }
    var text = nameCtx.getText();
    return "Добавить".equalsIgnoreCase(text) || "Add".equalsIgnoreCase(text);
  }

  /**
   * Для конструкции {@code X.Колонки.Добавить(...)}: вернуть {@code "X"},
   * если у callStatement ровно один accessProperty-модификатор с именем
   * {@code Колонки}/{@code Columns} и далее идёт accessCall.
   */
  @Nullable
  private static String extractColumnsAddReceiverName(BSLParser.CallStatementContext ctx) {
    var identifier = ctx.IDENTIFIER();
    if (identifier == null) {
      return null;
    }
    var modifiers = ctx.modifier();
    if (modifiers.size() != 1) {
      return null;
    }
    var prop = modifiers.get(0).accessProperty();
    if (prop == null || prop.IDENTIFIER() == null) {
      return null;
    }
    var propName = prop.IDENTIFIER().getText();
    if (!"Колонки".equalsIgnoreCase(propName) && !"Columns".equalsIgnoreCase(propName)) {
      return null;
    }
    if (ctx.accessCall() == null) {
      return null;
    }
    return identifier.getText();
  }

  @Nullable
  private static String extractInsertReceiverName(BSLParser.CallStatementContext ctx) {
    var identifier = ctx.IDENTIFIER();
    if (identifier == null) {
      return null;
    }
    // X.Вставить(...) — ровно один accessCall-модификатор и никаких других access*.
    if (!ctx.modifier().isEmpty()) {
      return null;
    }
    if (ctx.accessCall() == null) {
      return null;
    }
    return identifier.getText();
  }

  private static boolean isInsertMethodName(BSLParser.MethodCallContext methodCall) {
    var nameCtx = methodCall.methodName();
    if (nameCtx == null) {
      return false;
    }
    var text = nameCtx.getText();
    return "Вставить".equalsIgnoreCase(text) || "Insert".equalsIgnoreCase(text);
  }

  @Nullable
  private static String extractStringLiteralText(BSLParser.ExpressionContext expr) {
    var text = expr.getText();
    if (text == null || text.length() < 2) {
      return null;
    }
    if (text.charAt(0) != '"' || text.charAt(text.length() - 1) != '"') {
      return null;
    }
    return text.substring(1, text.length() - 1);
  }

  private static BSLParser.@Nullable FileContext safeGetOwnerAst(DocumentContext owner) {
    try {
      return owner.getAst();
    } catch (NullPointerException e) {
      return null;
    }
  }

  /**
   * Извлечь типы из висячего комментария декларации переменной:
   * {@code Перем X; // Тип -}. Источник — структурно разобранные парсером типы
   * {@code VariableDescription.trailingDescription.getTypes()}, который парсер уже
   * привязал к декларации.
   */
  private TypeSet typesFromVariableTrailingComment(VariableSymbol variable) {
    var description = variable.getDescription().orElse(null);
    if (description == null) {
      return TypeSet.EMPTY;
    }
    var trailing = description.getTrailingDescription().orElse(null);
    if (trailing == null) {
      return TypeSet.EMPTY;
    }
    return resolveCommentTypes(trailing.getTypes(), variable.getOwner().getFileType());
  }

  /**
   * Найти {@link ParameterDefinition} в скоупе-методе по имени переменной и
   * вернуть его декларированные типы из JsDoc. Если у параметра нет
   * собственного описания, но у метода есть docblock-ссылка
   * {@code // См. ДругойМетод} — типы наследуются от одноимённого
   * параметра целевого метода (только в пределах того же модуля).
   */
  private TypeSet declaredParameterTypes(VariableSymbol variable) {
    var scope = variable.getScope();
    if (!(scope instanceof MethodSymbol method)) {
      return TypeSet.EMPTY;
    }
    var name = variable.getName();
    var parameters = method.getParameters();
    for (var i = 0; i < parameters.size(); i++) {
      var parameter = parameters.get(i);
      if (parameter.getName().equalsIgnoreCase(name)) {
        return resolveParameterTypes(method, parameter, name, i);
      }
    }
    return TypeSet.EMPTY;
  }

  /**
   * Источники типа параметра в порядке убывания приоритета: doc-комментарий
   * (включая {@code См.}-ссылки, в т.ч. вложенные в коллекции/структуры),
   * контракт платформенного события (для обработчиков), наследование от
   * родительского метода в иерархии.
   */
  private TypeSet resolveParameterTypes(MethodSymbol method, ParameterDefinition parameter,
                                        String name, int paramIndex) {
    // getDeclaredParameterTypes разворачивает и См.-ссылки в описании параметра
    // (включая вложенные) — отдельный проход по hyperlink-ам больше не нужен.
    var direct = symbolTypeIndex.getDeclaredParameterTypes(parameter, method.getOwner());
    if (!direct.isEmpty()) {
      return direct;
    }
    var fromContract = eventHandlerParameterTypes(method, paramIndex);
    if (!fromContract.isEmpty()) {
      return fromContract;
    }
    return inheritedParameterTypes(method, name);
  }

  /**
   * Тип параметра обработчика платформенного события из контракта (bsl-context).
   * Сопоставление строго <b>по позиции</b>: имена параметров обработчика задаёт
   * пользователь — они не обязаны совпадать с именами в контракте. Если последний
   * параметр контракта помечен {@code variadic}, все параметры метода с индексом
   * за ним наследуют его тип (хвост переменной арности — например, конструктор
   * OneScript-класса {@code ПриСозданииОбъекта(а, б, в, ...)}).
   */
  private TypeSet eventHandlerParameterTypes(MethodSymbol method, int paramIndex) {
    var contractOpt = eventContractsIndex.getContract(method.getOwner(), method.getName());
    if (contractOpt.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var signatures = contractOpt.get().signatures();
    if (signatures.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var params = signatures.get(0).parameters();
    if (params.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var idx = paramIndex < params.size() ? paramIndex : (params.size() - 1);
    var param = params.get(idx);
    if (paramIndex >= params.size() && !param.variadic()) {
      return TypeSet.EMPTY;
    }
    return param.types();
  }

  /**
   * Найти типы параметра {@code name} в методе-источнике, на который
   * ссылается текущий метод через {@code // См. Метод} в docblock'е.
   * Сейчас работает только для ссылок на методы в том же модуле.
   */
  private TypeSet inheritedParameterTypes(MethodSymbol method, String paramName) {
    var description = method.getDescription().orElse(null);
    if (description == null) {
      return TypeSet.EMPTY;
    }
    var links = description.getLinks();
    if (links == null || links.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var owner = method.getOwner();
    for (var link : links) {
      var target = findLocalMethod(owner, link.link());
      if (target == null) {
        continue;
      }
      for (var targetParam : target.getParameters()) {
        if (targetParam.getName().equalsIgnoreCase(paramName)) {
          var types = symbolTypeIndex.getDeclaredParameterTypes(targetParam, owner);
          if (!types.isEmpty()) {
            return types;
          }
        }
      }
    }
    return TypeSet.EMPTY;
  }

  /**
   * @return метод с именем {@code methodName} из текущего модуля,
   *         либо {@code null} если такого метода нет (или ссылка
   *         указывает на cross-module — пока не поддерживается).
   */
  @Nullable
  private static MethodSymbol findLocalMethod(DocumentContext documentContext, String methodName) {
    if (methodName == null || methodName.contains(".")) {
      return null;
    }
    return documentContext.getSymbolTree().getMethods().stream()
      .filter(m -> m.getName().equalsIgnoreCase(methodName))
      .findFirst()
      .orElse(null);
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
      return types.union(inlineCommentTypes(owner, assignment));
    }
    return inferFromDefinitionPosition(owner, position, ctx);
  }

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
      result = result.union(inlineCommentTypes(owner, assignment.get()));
      return result;
    }
    // Декларация переменной через «Для Каждого X Из Коллекция Цикл»:
    // тип X — это объединение typeSets, объявленных как elementTypes
    // коллекции.
    var forEach = ExpressionAtPosition.findForEachBindingAt(owner, position);
    if (forEach.isPresent() && forEach.get().expression() != null) {
      var collectionExpr = ExpressionTreeBuildingVisitor.buildExpressionTree(forEach.get().expression());
      if (collectionExpr != null) {
        var collectionTypes = inferInternal(collectionExpr, ctx);
        result = result.union(collectionTypes.getElementTypes());
      }
    }
    return result;
  }

  /**
   * Подхватить типы из висячего комментария в строке присваивания:
   * {@code X = F(); // Тип -}. Соответствует «inline-typing локальной
   * переменной» из стандарта 1С:EDT. Комментарий разбирается тем же парсером
   * описаний, что и висячий комментарий декларации: из токена строится
   * {@link VariableDescription}, а типы берутся структурно из её
   * {@code trailingDescription.getTypes()}.
   */
  private TypeSet inlineCommentTypes(
    DocumentContext owner,
    BSLParser.AssignmentContext assignment
  ) {
    var trailingComment = Trees.getTrailingComment(owner.getTokens(), assignment.getStop());
    if (trailingComment.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var trailing = VariableDescription.create(Collections.emptyList(), trailingComment)
      .getTrailingDescription()
      .orElse(null);
    if (trailing == null) {
      return TypeSet.EMPTY;
    }
    return resolveCommentTypes(trailing.getTypes(), owner.getFileType());
  }

  /**
   * Резолвит структурно разобранные парсером типы комментария в {@link TypeSet}
   * по их {@link TypeDescription#name()}. Для коллекционной нотации
   * {@code Массив из Число} парсер возвращает один тип-голову {@code Массив}.
   */
  private TypeSet resolveCommentTypes(List<TypeDescription> types, FileType fileType) {
    if (types == null || types.isEmpty()) {
      return TypeSet.EMPTY;
    }
    Set<TypeRef> refs = new LinkedHashSet<>();
    for (var td : types) {
      var typeName = DescriptionTypes.resolveName(td);
      if (!typeName.isBlank()) {
        typeRegistry.resolve(typeName, fileType).ifPresent(refs::add);
      }
    }
    return refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(refs);
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
     * Переменные, для которых прямо сейчас идёт расчёт по потоку. Держится отдельно от
     * {@link #inProgress}: тот отдаёт частичный тип символа, а здесь нужен именно отказ
     * от повторного расчёта по потоку — вложенный запрос должен уйти на символьный путь
     * с его собственной защитой от циклов.
     */
    final Set<SourceDefinedSymbol> flowInProgress = new HashSet<>();
    int depth;

    InferenceContext(DocumentContext documentContext) {
      this.documentContext = documentContext;
    }
  }
}
