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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;

import java.util.Collection;
import java.util.List;

/**
 * Источник типов для {@link TypeRegistry}.
 * <p>
 * Реализации могут предоставлять платформенные типы (1С / OneScript),
 * конфигурационные типы (MDClasses) или пользовательские типы (модули,
 * объявленные в исходниках). Несколько провайдеров могут работать
 * одновременно; реестр опросит каждого при инициализации (для bootstrap-фаз)
 * или будет обновляться от событий (для динамических источников).
 */
public interface TypePackProvider {

  /**
   * Объявление одного типа для регистрации.
   *
   * @param kind                категория типа
   * @param name                двуязычное имя типа (ru + en); primary-форма
   *                            используется как канонический ключ в реестре,
   *                            альтернативная сторона регистрируется как alias
   * @param members             свойства и методы типа
   * @param description         описание типа (для hover'а класса)
   * @param constructors        сигнатуры конструкторов {@code Новый Тип(...)}; пустой список — без конструкторов
   * @param defaultElementTypes для типов-коллекций — типы элементов, доступных
   *                            через {@code Для Каждого X Из Коллекция Цикл}.
   *                            Пустой список — либо не коллекция, либо элементы
   *                            гетерогенные (как у {@code Массив}). Семантика —
   *                            пара с {@link #supportsForEach}: если форы доступен,
   *                            но {@code defaultElementTypes} пуст, итератор не имеет
   *                            определённого типа.
   * @param supportsForEach     поддержка обхода {@code Для Каждого}
   * @param supportsIndexAccess поддержка индексатора {@code coll[…]}
   * @param forEachDescription  текстовое описание из синтакс-помощника, как
   *                            устроен обход коллекции ({@code Для Каждого}).
   *                            Пустая строка, если описания нет.
   * @param indexAccessDescription текстовое описание из синтакс-помощника, как
   *                            устроен индексатор ({@code coll[…]}). Пустая
   *                            строка, если описания нет.
   * @param typeParameters     имена generic-плейсхолдеров (без угловых
   *                            скобок) в порядке появления в имени типа.
   *                            Например, для {@code "СправочникСсылка.<Имя справочника>"}
   *                            — {@code ["Имя справочника"]}. Заполняется
   *                            платформенным провайдером из
   *                            {@code Context.typeParameters()} bsl-context'а.
   *                            Не-generic типы — пустой список.
   * @param isEnum             {@code true}, если декларация описывает системное
   *                            перечисление платформы (источник —
   *                            {@code ContextEnum} из bsl-context или {@code "kind": "ENUM"}
   *                            в JSON-паке). Используется при регистрации
   *                            в global scope, чтобы поставить
   *                            {@code SyntheticKind.PLATFORM_GLOBAL_ENUM}.
   */
  record TypeDecl(
    TypeKind kind,
    BilingualString name,
    Collection<MemberDescriptor> members,
    BilingualString description,
    List<SignatureDescriptor> constructors,
    List<TypeRef> defaultElementTypes,
    boolean supportsForEach,
    boolean supportsIndexAccess,
    BilingualString forEachDescription,
    BilingualString indexAccessDescription,
    List<String> typeParameters,
    boolean isEnum
  ) {

    public TypeDecl {
      if (description == null) description = BilingualString.EMPTY;
      if (forEachDescription == null) forEachDescription = BilingualString.EMPTY;
      if (indexAccessDescription == null) indexAccessDescription = BilingualString.EMPTY;
      typeParameters = typeParameters == null ? List.of() : List.copyOf(typeParameters);
      if (name == null) {
        name = BilingualString.EMPTY;
      }
    }

    /**
     * Compat-конструктор: одноязычные {@code description}/{@code forEachDescription}/
     * {@code indexAccessDescription} строками.
     */
    public TypeDecl(TypeKind kind, BilingualString name, Collection<MemberDescriptor> members,
                    String description,
                    List<SignatureDescriptor> constructors, List<TypeRef> defaultElementTypes,
                    boolean supportsForEach, boolean supportsIndexAccess,
                    String forEachDescription, String indexAccessDescription,
                    List<String> typeParameters, boolean isEnum) {
      this(kind, name, members,
        BilingualString.of(description), constructors, defaultElementTypes,
        supportsForEach, supportsIndexAccess,
        BilingualString.of(forEachDescription), BilingualString.of(indexAccessDescription),
        typeParameters, isEnum);
    }

    /** Каноничное имя типа (ru-сторона; для legacy-источников — primary). */
    public String qualifiedName() {
      return name.primary();
    }

    public TypeRef toRef() {
      return new TypeRef(kind, qualifiedName());
    }
  }

  /**
   * @return типы, регистрируемые этим provider'ом
   */
  Collection<TypeDecl> getTypes();
}
