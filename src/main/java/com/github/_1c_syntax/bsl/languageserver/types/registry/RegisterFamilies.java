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

import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Set;

/**
 * Семейства типов регистров и починка ссылок на чужое семейство.
 * <p>
 * Синтакс-помощник местами объявляет член одного семейства регистров типом из другого:
 * у {@code РегистрБухгалтерииНаборЗаписей} метод {@code Вставить} возвращает
 * {@code РегистрНакопленияЗапись.<Имя регистра накопления>}, хотя соседние
 * {@code Добавить} и {@code Получить} — {@code РегистрБухгалтерииЗапись}. Плейсхолдер
 * чужого семейства именем своего регистра не подставляется, и обобщённое объявление
 * доезжает до пользователя необработанным.
 * <p>
 * Чинится не таблицей исключений, а правилом: у объекта семейства F ссылка на
 * {@code Регистр<другое>Суффикс.<…>} читается как {@code F + Суффикс}. Правка
 * принимается, только если такой тип есть в реестре, поэтому ошибиться она не даёт.
 * <p>
 * Переписывается <b>тип возврата</b> — члена и его сигнатур. Типы параметров не трогаются
 * намеренно: обход всех объявлений четырёх семейств регистров в синтакс-помощнике 8.3.27
 * даёт ровно одну ссылку на чужое семейство, и та в позиции возврата
 * ({@code РегистрБухгалтерииНаборЗаписей.Вставить}); в параметрах — ни одной. Само правило
 * от позиции не зависит, поэтому распространить его на параметры — механическая правка
 * (как в {@link PlaceholderBinder}, который перебирает все позиции), но пока она покрывала
 * бы не встречающийся случай.
 */
final class RegisterFamilies {

  /**
   * Ядра имён семейств регистров. Внутри семейства типы отличаются только суффиксом
   * ({@code Запись}, {@code КлючЗаписи}, {@code НаборЗаписей} …), поэтому по этому
   * списку опознаётся и своё семейство, и ссылка на чужое.
   */
  private static final Set<String> CORES = Set.of(
    "РегистрСведений", "РегистрНакопления", "РегистрБухгалтерии", "РегистрРасчета");

  private RegisterFamilies() {
    // утилитный класс
  }

  /**
   * Является ли имя ядром семейства регистров.
   *
   * @param familyCore ru-часть имени семейства.
   * @return {@code true}, если это регистр.
   */
  static boolean isRegisterFamily(String familyCore) {
    return CORES.contains(familyCore);
  }

  /**
   * Член с типами, переписанными на своё семейство регистров, — когда владелец известен
   * своим mdoRef'ом ({@code РегистрРасчета.Начисления}), а не разобранным на части.
   *
   * @param typeRegistry реестр, по которому проверяется существование типа.
   * @param member       член с типом чужого семейства.
   * @param ownerMdoRef  mdoRef регистра-владельца.
   * @return член со своим типом; {@code null}, если владелец — не регистр либо
   *     переписывать нечего.
   */
  static @Nullable MemberDescriptor ownFamilyMember(TypeRegistry typeRegistry, MemberDescriptor member,
                                                    String ownerMdoRef) {
    var dot = ownerMdoRef.indexOf('.');
    if (dot <= 0 || dot == ownerMdoRef.length() - 1) {
      return null;
    }
    var familyCore = ownerMdoRef.substring(0, dot);
    if (!isRegisterFamily(familyCore)) {
      return null;
    }
    return ownFamilyMember(typeRegistry, member, familyCore, ownerMdoRef.substring(dot + 1));
  }

  /**
   * Член с типами, переписанными на своё семейство регистров.
   *
   * @param typeRegistry реестр, по которому проверяется существование типа.
   * @param member       член с типом чужого семейства.
   * @param familyCore   ядро своего семейства ({@code РегистрБухгалтерии}).
   * @param mdName       имя регистра в конфигурации.
   * @return член со своим типом; {@code null}, если переписывать нечего.
   */
  static @Nullable MemberDescriptor ownFamilyMember(TypeRegistry typeRegistry, MemberDescriptor member,
                                                    String familyCore, String mdName) {
    var returnTypes = ownFamilyTypes(typeRegistry, member.returnTypes(), familyCore, mdName);
    if (returnTypes == null) {
      return null;
    }
    var signatures = member.signatures().stream()
      .map(signature -> ownFamilySignature(typeRegistry, signature, familyCore, mdName))
      .toList();
    return member.withReturnTypes(returnTypes).withSignatures(signatures);
  }

  private static SignatureDescriptor ownFamilySignature(TypeRegistry typeRegistry, SignatureDescriptor signature,
                                                        String familyCore, String mdName) {
    var returnTypes = ownFamilyTypes(typeRegistry, signature.returnTypes(), familyCore, mdName);
    if (returnTypes == null) {
      return signature;
    }
    return new SignatureDescriptor(signature.parameters(), returnTypes,
      signature.bilingualDescription(), signature.metadata());
  }

  /** @return набор с заменённым семейством; {@code null}, если ни один тип не поменялся. */
  private static @Nullable TypeSet ownFamilyTypes(TypeRegistry typeRegistry, TypeSet types,
                                                  String familyCore, String mdName) {
    var refs = new ArrayList<TypeRef>(types.refs().size());
    var changed = false;
    for (var ref : types.refs()) {
      var own = ownFamilyRef(typeRegistry, ref, familyCore, mdName);
      changed |= own != null;
      refs.add(own == null ? ref : own);
    }
    return changed ? TypeSet.of(refs) : null;
  }

  /**
   * Тип того же вида, но своего семейства регистров.
   *
   * @param typeRegistry реестр, по которому проверяется существование типа.
   * @param ref          ссылка из объявления платформы.
   * @param familyCore   ядро своего семейства ({@code РегистрБухгалтерии}).
   * @param mdName       имя регистра в конфигурации.
   * @return тип своего семейства; {@code null}, если ссылка не на чужое семейство
   *   либо такого типа не существует.
   */
  static @Nullable TypeRef ownFamilyRef(TypeRegistry typeRegistry, TypeRef ref, String familyCore, String mdName) {
    var qualifiedName = ref.qualifiedName();
    if (ref.placeholders().isEmpty() || qualifiedName.startsWith(familyCore)) {
      return null;
    }
    var dot = qualifiedName.indexOf('.');
    if (dot < 0) {
      return null;
    }
    for (var foreignCore : CORES) {
      if (!qualifiedName.startsWith(foreignCore)) {
        continue;
      }
      var kindSuffix = qualifiedName.substring(foreignCore.length(), dot);
      var own = typeRegistry.resolve(familyCore + kindSuffix + "." + mdName).orElse(null);
      if (own != null) {
        return own;
      }
    }
    return null;
  }
}
