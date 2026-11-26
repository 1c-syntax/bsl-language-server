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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.MD;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Типизирует стандартный реквизит {@code Владелец} подчинённого справочника.
 * <p>
 * В синтакс-помощнике он объявлен типом {@code Неопределено} — и для неподчинённого
 * справочника это правда. У подчинённого владельцы известны из метаданных, и тип
 * берётся оттуда.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
class CatalogOwnerTypesRegistrar {

  /** Стандартный реквизит подчинённого объекта, ведущий на владельца. */
  private static final String OWNER_PROPERTY = "Владелец";

  private final TypeRegistry typeRegistry;

  /**
   * Типизирует стандартный реквизит {@code Владелец} у подчинённого справочника.
   * <p>
   * В синтакс-помощнике он объявлен типом {@code Неопределено} — и для неподчинённого
   * справочника это правда («Неопределено — для неподчиненного справочника»), поэтому
   * там ничего не меняется. У подчинённого владельцы известны из метаданных
   * ({@code Catalog.getOwners()}), и тип берётся оттуда: владельцев может быть несколько,
   * тогда получается объединение.
   * <p>
   * Здесь не подстановка в плейсхолдер, а прямая замена типа члена: подставлять некуда.
   * Члены перебираются у дженерика, поэтому реквизит уточняется сразу во всех типах
   * семейства, где он объявлен ({@code СправочникСсылка}, {@code СправочникОбъект} …),
   * без перечисления их в коде.
   * <p>
   * Типы владельцев резолвятся <b>внутри источника членов</b>, а не здесь: справочник
   * может обрабатываться раньше своего владельца, и тогда типа владельца ещё нет.
   * Порядок обхода конфигурации на это не намекает и полагаться на него нельзя:
   * {@code getChildrenByMdoRef()} отдаёт неизменяемую хеш-карту, а её обход JDK
   * рандомизирует от запуска к запуску. К моменту, когда членов спросят,
   * зарегистрированы все типы.
   */
  void registerOwnerMembers(MD md, String familyCore, String mdName) {
    if (!(md instanceof Catalog catalog)) {
      return;
    }
    var ownerNames = catalog.getOwners().stream()
      .map(owner -> refTypeName(owner.getMdoRefRu()))
      .toList();
    if (ownerNames.isEmpty()) {
      return;
    }
    for (var generic : typeRegistry.findAllGenericsByFamilyCore(familyCore)) {
      registerOwnerMembersOn(generic, mdName, ownerNames);
    }
  }

  /** Регистрирует уточнённый {@code Владелец} на специализации одного дженерика семейства. */
  private void registerOwnerMembersOn(TypeRef generic, String mdName, List<String> ownerNames) {
    var parameters = typeRegistry.getTypeParameters(generic);
    if (parameters.size() != 1) {
      return;
    }
    var bindings = Map.of(parameters.get(0), mdName);
    var specialized = typeRegistry.resolve(TypeRef.specialize(generic, bindings).qualifiedName())
      .orElse(null);
    if (specialized == null || specialized.equals(generic)) {
      return;
    }
    typeRegistry.registerMemberOverride(specialized, () -> ownerMembers(generic, ownerNames), FileType.BSL);
  }

  /**
   * Реквизит {@code Владелец} дженерика с типом владельцев; пусто — у типа его нет
   * либо ни один владелец не зарегистрирован (тогда обобщённое объявление платформы
   * остаётся как есть — врать про тип хуже, чем не уточнить).
   */
  private List<MemberDescriptor> ownerMembers(TypeRef generic, List<String> ownerNames) {
    var ownerRefs = ownerNames.stream()
      .map(typeRegistry::resolve)
      .flatMap(Optional::stream)
      .toList();
    if (ownerRefs.isEmpty()) {
      return List.of();
    }
    var ownerTypes = TypeSet.of(ownerRefs);
    for (var member : typeRegistry.getMembers(generic, FileType.BSL)) {
      if (member.kind() == MemberKind.PROPERTY && member.matches(OWNER_PROPERTY)) {
        return List.of(member.withReturnTypes(ownerTypes));
      }
    }
    return List.of();
  }

  /** {@code Справочник.Владелец1} → {@code СправочникСсылка.Владелец1}. */
  private static String refTypeName(String mdoRef) {
    var dot = mdoRef.indexOf('.');
    return dot < 0 ? mdoRef : (mdoRef.substring(0, dot) + "Ссылка" + mdoRef.substring(dot));
  }

}
