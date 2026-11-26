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

import com.github._1c_syntax.bsl.context.api.Placeholder;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Подстановка конкретных имён в generic-плейсхолдер типов члена.
 * <p>
 * Платформа объявляет часть членов через плейсхолдер, который не покрывается
 * специализацией по имени объекта: {@code Регистратор} записи регистра и первый параметр
 * {@code ВыбратьПоРегистратору()} объявлены как {@code ДокументСсылка.<Имя документа>},
 * а конкретных документов может быть несколько. Подставляются все имена, тип получается
 * объединением.
 * <p>
 * Плейсхолдер ищется и правится <b>во всех позициях</b> члена — тип возврата, типы
 * параметров и тип возврата сигнатуры: в объявлении он может стоять в любой из них.
 */
final class PlaceholderBinder {

  private PlaceholderBinder() {
  }

  /**
   * Подставляет имена в единственный плейсхолдер типов члена.
   * <p>
   * Подстановка принимается, только если каждый получившийся тип есть в реестре: тот же
   * плейсхолдер {@code <Имя документа>} стоит и там, где подставляемое имя к документам
   * отношения не имеет, и правдоподобный несуществующий тип хуже обобщённого.
   *
   * @param typeRegistry реестр, по которому проверяется существование типа.
   * @param member       член с плейсхолдером в типах.
   * @param names        имена для подстановки.
   * @return член с подставленными типами; {@code null}, если плейсхолдер не один либо
   *   ни одна подстановка не дала существующих типов.
   */
  static @Nullable MemberDescriptor bind(TypeRegistry typeRegistry, MemberDescriptor member, List<String> names) {
    var placeholder = singlePlaceholder(member);
    if (placeholder == null) {
      return null;
    }
    MemberDescriptor bound = null;
    for (var name : names) {
      var bindings = Map.of(placeholder, name);
      if (!substitutionExists(typeRegistry, member, bindings)) {
        continue;
      }
      var specialized = member.specialize(bindings, typeRegistry::canonicalRef);
      bound = bound == null ? specialized : merge(bound, specialized);
    }
    return bound;
  }

  /**
   * Единственный generic-плейсхолдер в типах члена. Их либо ровно один
   * ({@code <Имя документа>}), либо — у таблиц внешних источников данных — два,
   * и однозначной подстановки тогда нет.
   *
   * @return имя плейсхолдера; {@code null}, если их не ровно один.
   */
  static @Nullable String singlePlaceholder(MemberDescriptor member) {
    var placeholders = typePositions(member).stream()
      .flatMap(types -> types.refs().stream())
      .flatMap(ref -> ref.placeholders().stream())
      .map(Placeholder::name)
      .distinct()
      .toList();
    return placeholders.size() == 1 ? placeholders.get(0) : null;
  }

  /** Все позиции члена, где стоят типы: возврат, возврат сигнатуры и её параметры. */
  private static List<TypeSet> typePositions(MemberDescriptor member) {
    var positions = new ArrayList<TypeSet>();
    positions.add(member.returnTypes());
    for (var signature : member.signatures()) {
      positions.add(signature.returnTypes());
      for (var parameter : signature.parameters()) {
        positions.add(parameter.types());
      }
    }
    return positions;
  }

  /**
   * Существуют ли все типы, которые получатся из плейсхолдерных после подстановки.
   * Проверяются только они: остальные типы члена к подстановке отношения не имеют, и
   * отсутствие какого-нибудь редкого платформенного типа не должно её отменять.
   */
  private static boolean substitutionExists(TypeRegistry typeRegistry, MemberDescriptor member,
                                            Map<String, String> bindings) {
    for (var types : typePositions(member)) {
      for (var ref : types.refs()) {
        if (ref.placeholders().isEmpty()) {
          continue;
        }
        if (typeRegistry.resolve(TypeRef.specialize(ref, bindings).qualifiedName()).isEmpty()) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Объединяет две подстановки одного и того же члена: типы в каждой позиции
   * складываются. Оба члена получены из одного объявления, поэтому число сигнатур и
   * параметров у них совпадает.
   */
  private static MemberDescriptor merge(MemberDescriptor first, MemberDescriptor second) {
    var merged = first.withReturnTypes(first.returnTypes().union(second.returnTypes()));
    if (first.signatures().isEmpty()) {
      return merged;
    }
    var signatures = new ArrayList<SignatureDescriptor>(first.signatures().size());
    for (var index = 0; index < first.signatures().size(); index++) {
      signatures.add(mergeSignatures(first.signatures().get(index), second.signatures().get(index)));
    }
    return merged.withSignatures(signatures);
  }

  private static SignatureDescriptor mergeSignatures(SignatureDescriptor first, SignatureDescriptor second) {
    var parameters = new ArrayList<ParameterDescriptor>(first.parameters().size());
    for (var index = 0; index < first.parameters().size(); index++) {
      var parameter = first.parameters().get(index);
      var other = second.parameters().get(index);
      parameters.add(new ParameterDescriptor(parameter.bilingualName(),
        parameter.types().union(other.types()), parameter.optional(),
        parameter.bilingualDescription(), parameter.defaultValue(), parameter.variadic()));
    }
    return new SignatureDescriptor(parameters, first.returnTypes().union(second.returnTypes()),
      first.bilingualDescription(), first.metadata());
  }
}
