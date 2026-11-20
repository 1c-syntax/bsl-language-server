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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.description.CollectionTypeDescription;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Утилиты для работы с типами, упомянутыми в описаниях символов (doc-комментариях).
 * <p>
 * Используются семантические аксессоры парсера ({@link MethodDescription#getParameters()},
 * {@link MethodDescription#getReturnedValue()}, {@link VariableDescription#getTypes()}), которые
 * дают идентичность типа ({@link TypeDescription#name()} / {@link CollectionTypeDescription#collectionName()})
 * без восстановления текста по координатам.
 */
@UtilityClass
public class DescriptionTypes {

  /**
   * Все типы, упомянутые в описании (типы параметров и возвращаемого значения метода либо
   * типы переменной), включая вложенные типы полей. Описание висячего комментария переменной
   * сюда не включается — его обходят отдельно через {@link VariableDescription#getTrailingDescription()}.
   *
   * @param description описание символа.
   *
   * @return поток описаний типов.
   */
  public Stream<TypeDescription> typesOf(SourceDefinedSymbolDescription description) {
    Stream<TypeDescription> topLevel;
    if (description instanceof MethodDescription methodDescription) {
      topLevel = Stream.concat(
        methodDescription.getParameters().stream().flatMap(parameter -> parameter.types().stream()),
        methodDescription.getReturnedValue().stream()
      );
    } else if (description instanceof VariableDescription variableDescription) {
      topLevel = variableDescription.getTypes().stream();
    } else {
      topLevel = Stream.empty();
    }
    return topLevel.flatMap(DescriptionTypes::flatten);
  }

  /**
   * Имя типа для резолва в реестре типов: для простого типа — само имя, для коллекции — имя
   * типа-головы ({@code Массив} из {@code Массив<Число>}), для гиперссылки — пустая строка
   * (ссылки {@code См.} обрабатываются отдельно).
   *
   * @param type описание типа.
   *
   * @return имя типа для резолва либо пустая строка, если тип резолвить не нужно.
   */
  public String resolveName(TypeDescription type) {
    return switch (type.variant()) {
      case SIMPLE -> type.name();
      case COLLECTION -> type instanceof CollectionTypeDescription collection ? collection.collectionName() : type.name();
      case HYPERLINK -> "";
    };
  }

  /**
   * Ссылки {@code См.} в описаниях типов, в том числе уточнения простого типа и вложенные ссылки.
   * Сначала идёт ссылка самого типа, затем ссылки типов элементов коллекции и полей.
   *
   * @param types описания типов; {@code null} означает, что описаний нет.
   * @return поток целей ссылок в порядке обхода описаний.
   */
  public Stream<String> linksOf(@Nullable List<? extends TypeDescription> types) {
    return Stream.ofNullable(types).flatMap(List::stream)
      .flatMap(DescriptionTypes::flatten)
      .map(DescriptionTypes::linkOf)
      .filter(Objects::nonNull);
  }

  private @Nullable String linkOf(TypeDescription type) {
    if (type.variant() == TypeDescription.Variant.HYPERLINK) {
      return type.name();
    }
    var hyperlink = type.hyperlink();
    return hyperlink == null ? null : hyperlink.link();
  }

  private Stream<TypeDescription> flatten(TypeDescription type) {
    // Вложенные типы: типы-значения коллекции (Массив из Число → Число) и типы полей структуры.
    var valueTypes = type instanceof CollectionTypeDescription collection
      ? collection.valueTypes().stream()
      : Stream.<TypeDescription>empty();
    var fieldTypes = type.fields().stream()
      .flatMap(field -> field.types().stream());
    return Stream.concat(
      Stream.of(type),
      Stream.concat(valueTypes, fieldTypes).flatMap(DescriptionTypes::flatten)
    );
  }
}
