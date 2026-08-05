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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.DereferenceLocator;
import com.github._1c_syntax.bsl.languageserver.types.index.PropertyMethodCallIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Типизация переменной, отданной методу {@code Свойство(Ключ, Приёмник)} вторым аргументом.
 * <p>
 * У структуроподобных объектов 1С чтение ключа записано не выражением, а выходным
 * параметром: {@code Если Параметры.Свойство("Отбор", Отбор) Тогда} кладёт в {@code Отбор}
 * значение ключа, а если ключа нет — {@code Неопределено}. Возвращает же метод признак
 * «нашлось ли», поэтому обычным выводом типа выражения такое присваивание не видно вовсе:
 * справа от знака равенства переменная не стоит ни разу.
 * <p>
 * Отсюда и место в системе: это <b>изменение типа на месте</b>, наравне с
 * операторами-мутаторами ({@code Х.Вставить(…)}) и присваиванием вида элементу формы, —
 * действует с точки вызова, а не по всей области видимости. Отличие в том, что переменная
 * здесь не получатель вызова, а его аргумент: типизирует её чужой объект.
 * <p>
 * Тип берётся тот же, что дало бы обращение к ключу через точку: сначала поля, накопленные
 * по коду ({@code Вставить}, литеральный конструктор), затем объявленные члены самого типа —
 * так {@code РеквизитФормыВЗначение}-структуры и данные формы отдают свои реквизиты.
 * <p>
 * {@code Неопределено} сюда <b>не</b> подмешивается. Раз тип значения известен, известно и то,
 * что ключ есть: состав складывается из вставок и объявлений, а не из догадок. Случай
 * «ключа нет» даёт ложная ветка условия, и приходит он оттуда — из
 * {@link GuardConditionNarrowing}; на слиянии веток обе возможности объединяются сами.
 */
@Component
@Slf4j
@WorkspaceScope
@RequiredArgsConstructor
public class PropertyMethodInference {

  /**
   * Типы, у которых {@code Свойство} читает <b>именованный ключ</b>. Сверяется семейство
   * (часть имени до первой точки): у данных формы типы специализированы прикладным
   * объектом ({@code ДанныеФормыСтруктура.ДокументОбъект.Заказ}).
   */
  private static final Set<String> PROPERTY_BEARING_FAMILIES = Set.of(
    "структура", "structure",
    "фиксированнаяструктура", "fixedstructure",
    "данныеформыструктура", "formdatastructure",
    "данныеформыструктурасколлекцией", "formdatastructurewithcollection",
    "данныеформыэлементколлекции", "formdatacollectionitem");

  /** Позиция ключа среди аргументов. */
  private static final int KEY_ARGUMENT_INDEX = 0;

  private final TypeRegistry typeRegistry;
  private final PropertyMethodCallIndex propertyMethodCallIndex;

  /**
   * Вызовы {@code Свойство}, типизирующие эту переменную, по позиции их начала.
   *
   * @param variable переменная-приёмник.
   * @return вызовы по позициям, в порядке следования в документе.
   */
  public Map<Position, BSLParser.MethodCallContext> outParameterCallsOf(SourceDefinedSymbol variable) {
    var owner = variable.getOwner();
    var ast = safeGetAst(owner);
    if (ast == null) {
      return Map.of();
    }
    Map<Position, BSLParser.MethodCallContext> calls = new LinkedHashMap<>();
    for (var call : propertyMethodCallIndex.byOutParameter(owner.getUri(), ast, variable.getName())) {
      calls.put(Ranges.create(call).getStart(), call);
    }
    return calls;
  }

  /**
   * Применить к типу переменной один вызов {@code Свойство}.
   *
   * @param variable переменная-приёмник.
   * @param call     вызов; {@code null}, если по позиции ничего не нашлось.
   * @param incoming тип переменной перед вызовом.
   * @param inferrer вывод типов выражения — им берётся тип получателя вызова.
   * @return тип значения ключа; исходный тип, если ключ не литерал, получатель не
   *     структуроподобен либо про такой ключ ничего не известно.
   */
  public TypeSet apply(SourceDefinedSymbol variable, BSLParser.@Nullable MethodCallContext call, TypeSet incoming,
                       Function<BslExpression, TypeSet> inferrer) {
    if (call == null) {
      return incoming;
    }
    var key = literalKey(call);
    if (key == null) {
      return incoming;
    }
    var receiver = receiverTypes(call, inferrer);
    if (receiver == null || !readsNamedProperties(receiver)) {
      return incoming;
    }
    var value = propertyTypes(receiver, key, variable.getOwner().getFileType());
    return value.isEmpty() ? incoming : value;
  }

  /**
   * Типы получателя вызова — того, у кого читают ключ.
   *
   * @return типы получателя; {@code null}, если вызов не является разыменованием
   *     ({@code Свойство} без получателя структурой не читается).
   */
  private static @Nullable TypeSet receiverTypes(BSLParser.MethodCallContext call,
                                                 Function<BslExpression, TypeSet> inferrer) {
    var methodName = call.methodName();
    var terminal = methodName == null ? null : methodName.IDENTIFIER();
    if (terminal == null) {
      return null;
    }
    var dereference = DereferenceLocator.locate(terminal);
    return dereference == null ? null : inferrer.apply(dereference.getLeft());
  }

  /** Читает ли {@code Свойство} у такого получателя именованный ключ. */
  private static boolean readsNamedProperties(TypeSet receiver) {
    for (var ref : receiver.refs()) {
      var qualifiedName = ref.qualifiedName();
      var dot = qualifiedName.indexOf('.');
      var family = (dot < 0 ? qualifiedName : qualifiedName.substring(0, dot)).toLowerCase(Locale.ROOT);
      if (PROPERTY_BEARING_FAMILIES.contains(family)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Типы значения по имени ключа — ровно то, что дало бы обращение через точку.
   * Поля, накопленные по коду, точнее объявленных членов и смотрятся первыми:
   * ключ мог быть дописан {@code Вставить} поверх того, что объявлено типом.
   */
  private TypeSet propertyTypes(TypeSet receiver, String key, FileType fileType) {
    var fields = OpenDataObjectInference.fieldTypes(receiver, key);
    if (!fields.isEmpty()) {
      return fields;
    }
    var result = TypeSet.EMPTY;
    for (var ref : receiver.refs()) {
      for (var member : typeRegistry.getMembers(ref, fileType)) {
        if (member.kind() == MemberKind.PROPERTY && !member.generic() && member.matches(key)) {
          result = result.union(member.returnTypes());
        }
      }
    }
    return result;
  }

  /** Имя ключа из первого аргумента — только строковым литералом. */
  private static @Nullable String literalKey(BSLParser.MethodCallContext call) {
    var paramList = call.doCall() == null ? null : call.doCall().callParamList();
    if (paramList == null) {
      return null;
    }
    var params = paramList.callParam();
    if (params.size() <= KEY_ARGUMENT_INDEX) {
      return null;
    }
    var expression = params.get(KEY_ARGUMENT_INDEX).expression();
    if (expression == null) {
      return null;
    }
    var tree = ExpressionTreeBuildingVisitor.buildExpressionTree(expression);
    var key = tree == null ? null : OpenDataObjectInference.stringLiteralOf(tree);
    return key == null || key.isBlank() ? null : key.trim();
  }

  /** AST документа; {@code null}, если разбор недоступен. */
  private static BSLParser.@Nullable FileContext safeGetAst(DocumentContext documentContext) {
    try {
      return documentContext.getAst();
    } catch (RuntimeException e) {
      // Вывод типа — не то место, где падать из-за нечитаемого документа: без AST
      // типизации по выходному параметру просто не будет.
      LOGGER.debug("AST is unavailable for {}", documentContext.getUri(), e);
      return null;
    }
  }
}
