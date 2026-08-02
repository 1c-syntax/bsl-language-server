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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.registry.XdtoTypesProvider;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Тип объекта XDTO, создаваемого фабрикой в коде:
 * <pre>
 * ТипАдрес = ФабрикаXDTO.Тип("http://www.example.org/адреса", "Адрес");
 * Адрес = ФабрикаXDTO.Создать(ТипАдрес);
 * Адрес.Улица = "...";
 * </pre>
 * Ссылок в комментариях здесь нет: пакет адресуется пространством имён, а тип — именем,
 * оба строковыми литералами. {@code Тип(URI, Имя)} отдаёт {@code ТипОбъектаXDTO},
 * помеченный найденным типом объекта (как коллекция помечается типом элемента), а
 * {@code Создать(Тип)} эту пометку снимает и отдаёт сам тип объекта.
 * <p>
 * Когда пространство имён вычисляется в коде, литерала нет и пометки не возникает —
 * тогда тип задаётся строчной ссылкой на объект пакета, и работает общий механизм
 * типизирующих комментариев.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class XdtoFactoryInference {

  /** Тип-получатель, у которого эти методы имеют смысл. */
  private static final String FACTORY_TYPE = "фабрикаxdto";

  /** Дескриптор типа объекта: результат {@code ФабрикаXDTO.Тип(…)}. */
  private static final String OBJECT_TYPE_DESCRIPTOR = "ТипОбъектаXDTO";

  private static final Set<String> TYPE_METHODS = Set.of("тип", "type");

  private static final Set<String> CREATE_METHODS = Set.of("создать", "create");

  /** Аргументы адресации типа: пространство имён и имя. */
  private static final int ADDRESSING_ARGUMENTS = 2;

  private final TypeRegistry typeRegistry;
  private final XdtoTypesProvider xdtoTypesProvider;

  /**
   * Уточнить тип вызова у фабрики XDTO.
   *
   * @param receiver   типы получателя вызова.
   * @param memberName имя вызванного метода.
   * @param call       узел вызова.
   * @param inferrer   вывод типов выражения-аргумента.
   * @param fileType   язык, на котором резолвятся имена типов.
   * @return уточнённый тип; {@code null}, если правило неприменимо.
   */
  public @Nullable TypeSet refinedCallTypes(TypeSet receiver, String memberName, MethodCallNode call,
                                            Function<BslExpression, TypeSet> inferrer, FileType fileType) {
    if (!isFactory(receiver)) {
      return null;
    }
    var lowerName = memberName.toLowerCase(Locale.ROOT);
    if (TYPE_METHODS.contains(lowerName)) {
      return objectTypeDescriptor(call, fileType);
    }
    if (CREATE_METHODS.contains(lowerName)) {
      return createdObjectType(call, inferrer);
    }
    return null;
  }

  private static boolean isFactory(TypeSet receiver) {
    return receiver.refs().stream()
      .anyMatch(ref -> FACTORY_TYPE.equals(ref.qualifiedName().toLowerCase(Locale.ROOT)));
  }

  /**
   * {@code ФабрикаXDTO.Тип(URI, Имя)} — дескриптор типа объекта, помеченный самим типом.
   *
   * @param call     узел вызова.
   * @param fileType язык, на котором резолвится имя дескриптора.
   * @return дескриптор с пометкой; {@code null}, если аргументы не литералы либо тип не найден.
   */
  private @Nullable TypeSet objectTypeDescriptor(MethodCallNode call, FileType fileType) {
    var arguments = call.arguments();
    if (arguments.size() < ADDRESSING_ARGUMENTS) {
      return null;
    }
    var namespaceUri = OpenDataObjectInference.stringLiteralOf(arguments.get(0));
    var typeName = OpenDataObjectInference.stringLiteralOf(arguments.get(1));
    if (namespaceUri == null || typeName == null) {
      return null;
    }
    var objectType = xdtoTypesProvider.resolveObjectType(namespaceUri, typeName).orElse(null);
    if (objectType == null) {
      return null;
    }
    var descriptor = typeRegistry.resolve(OBJECT_TYPE_DESCRIPTOR, fileType).orElse(null);
    if (descriptor == null) {
      return TypeSet.of(objectType);
    }
    return TypeSet.of(descriptor).withDescribed(descriptor, TypeSet.of(objectType));
  }

  /**
   * {@code ФабрикаXDTO.Создать(Тип)} — тип объекта, которым помечен дескриптор в аргументе.
   *
   * @param call     узел вызова.
   * @param inferrer вывод типов выражения-аргумента.
   * @return тип создаваемого объекта; {@code null}, если дескриптор не помечен.
   */
  private static @Nullable TypeSet createdObjectType(MethodCallNode call,
                                                     Function<BslExpression, TypeSet> inferrer) {
    var arguments = call.arguments();
    if (arguments.isEmpty()) {
      return null;
    }
    var described = inferrer.apply(arguments.get(0)).getDescribedTypes();
    return described.isEmpty() ? null : described;
  }
}
