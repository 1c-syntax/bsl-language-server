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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Проход по полю, названному путём вглубь таблицы ({@code Контрагент.ИНН}).
 * <p>
 * Путём поле называют и запрос ({@code Док.Контрагент.ИНН}), и состав полей
 * списка ({@code <dataPath>СубконтоДт.СубконтоДт1</dataPath>}), поэтому проход
 * по звеньям общий: первое звено — поле таблицы, каждое следующее — свойство
 * типа предыдущего.
 */
final class QueryFieldChain {

  private QueryFieldChain() {
  }

  /**
   * Итог прохода по звеньям.
   *
   * @param name  имя поля — звенья, склеенные подряд ({@code Ссылка.Код} даёт
   *              {@code СсылкаКод (RefCode)}). Звено, которого не нашлось,
   *              берётся так, как написано.
   * @param types типы поля; пусто, если какое-то звено не нашлось либо
   *              оказалось без типа.
   */
  record Chain(BilingualString name, TypeSet types) {
  }

  /**
   * Проход по звеньям пути.
   *
   * @param typeRegistry реестр типов — по нему разыменовываются звенья после первого.
   * @param tableFields  поля таблицы, от которых начинается путь.
   * @param segments     звенья пути.
   * @return имя и типы поля.
   */
  static Chain walk(TypeRegistry typeRegistry,
                    Collection<MemberDescriptor> tableFields,
                    List<String> segments) {
    var ru = new StringBuilder();
    var en = new StringBuilder();
    var types = TypeSet.EMPTY;
    var members = tableFields;
    for (var segment : segments) {
      var step = step(members, segment);
      ru.append(step.name().forLanguage(Language.RU));
      en.append(step.name().forLanguage(Language.EN));
      types = step.types();
      // Звено без типа разыменовать нечем: остаток пути только называет себя.
      members = types.isEmpty() ? List.of() : membersOf(typeRegistry, types);
    }
    return new Chain(name(ru.toString(), en.toString()), types);
  }

  /**
   * Типы поля, названного цепочкой звеньев.
   *
   * @param typeRegistry реестр типов — по нему разыменовываются звенья после первого.
   * @param tableFields  поля таблицы, от которых начинается путь.
   * @param segments     звенья пути.
   * @return типы поля; пусто, если какое-то звено не нашлось либо оказалось без типа.
   */
  static TypeSet types(TypeRegistry typeRegistry,
                       Collection<MemberDescriptor> tableFields,
                       List<String> segments) {
    return walk(typeRegistry, tableFields, segments).types();
  }

  /**
   * Типы одноимённого члена среди набора.
   *
   * @param members члены, среди которых ищется.
   * @param name    имя члена, в любом из написаний.
   * @return типы члена; пусто, если такого члена нет либо он без типа.
   */
  static TypeSet memberTypes(Collection<MemberDescriptor> members, String name) {
    return step(members, name).types();
  }

  /**
   * Одно звено: одноимённый член среди набора. Одноимённых бывает несколько —
   * когда звено разыменовано у составного типа, — и тогда типы объединяются, а
   * имя берётся у первого: написание у одного и того же поля одно.
   *
   * @return звено; если члена не нашлось, имя — как написано, типов нет.
   */
  private static Chain step(Collection<MemberDescriptor> members, String segment) {
    var matched = members.stream()
      .filter(member -> member.matches(segment))
      .toList();
    if (matched.isEmpty()) {
      return new Chain(BilingualString.of(segment), TypeSet.EMPTY);
    }
    var refs = new ArrayList<TypeRef>();
    matched.forEach(member -> refs.addAll(member.returnTypes().refs()));
    return new Chain(matched.get(0).bilingualName(), refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(refs));
  }

  /**
   * Члены всех типов-получателей: у составного типа звено ищется в каждом.
   */
  private static Collection<MemberDescriptor> membersOf(TypeRegistry typeRegistry, TypeSet receivers) {
    var result = new ArrayList<MemberDescriptor>();
    for (var receiver : receivers.refs()) {
      result.addAll(typeRegistry.getMembers(receiver, FileType.BSL));
    }
    return result;
  }

  /**
   * Имя из склеенных написаний: одно на оба языка, если написания совпали.
   */
  private static BilingualString name(String ru, String en) {
    return ru.equals(en) ? BilingualString.of(ru) : BilingualString.of(ru, en);
  }
}
