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

import com.github._1c_syntax.bsl.context.api.ContextQueryTable;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.MD;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Поля таблицы, объявленные в метаданных её объекта: реквизиты, измерения,
 * ресурсы, графы журнала, реквизиты адресации и стандартные реквизиты.
 * <p>
 * Какие из них таблица показывает, решает не объект, а сама таблица: у оборотов
 * регистра накопления полями служат измерения и ресурсы, а ни собственных
 * реквизитов регистра, ни {@code Активность} там нет вовсе. Поэтому источник
 * отдаёт только то, что таблица объявила, — плейсхолдеры материализует по виду
 * детей объекта, а конкретно названные поля сопоставляет по имени.
 * <p>
 * Спрашивается раньше платформенного источника: тип стандартного реквизита
 * платформа объявляет обобщённо ({@code Владелец} справочника — объединение
 * пяти ссылочных семейств, из которых конкретному справочнику подходит не
 * всякое), а в метаданных он конкретный.
 */
@Component
@WorkspaceScope
@Order(MetadataFieldSource.ORDER)
@RequiredArgsConstructor
class MetadataFieldSource implements QueryTableFieldSource {

  static final int ORDER = 20;

  /** Написаний у имени поля два — русское и английское. */
  private static final int NAME_SPELLINGS = 2;

  private final MdoMemberFactory mdoMembers;

  @Override
  public List<MemberDescriptor> fields(QueryTableRequest request) {
    var md = request.mdo();
    if (md == null) {
      return List.of();
    }
    var attributes = declaredAttributes(request, md);
    if (attributes.isEmpty()) {
      return List.of();
    }
    var fullRu = md.getMdoReference().getType().fullName().getRu();
    return mdoMembers.attributeMembers(attributes,
      mdoMembers.platformDescriptions(fullRu),
      mdoMembers.platformMetadata(fullRu + "Ссылка"));
  }

  /**
   * Реквизиты, которые таблица показывает полями.
   * <p>
   * Без платформенного описания таблицы (1С не установлена) этот отбор взять
   * неоткуда, и остаётся то, что известно из одних метаданных: реквизиты
   * объекта — поля его собственной таблицы. Про виртуальную сказать нечего:
   * её состав задаёт платформа.
   */
  private static List<? extends Attribute> declaredAttributes(QueryTableRequest request, MD md) {
    var table = request.table();
    if (table == null) {
      return request.ownTableOfMdo() && md instanceof AttributeOwner owner
        ? owner.getAllAttributes()
        : List.of();
    }
    var result = new ArrayList<Attribute>();
    for (var placeholder : request.declaredPlaceholders()) {
      result.addAll(QueryTablePlaceholders.attributesFor(placeholder, md));
    }
    result.addAll(namedFieldAttributes(table, md));
    return result;
  }

  /**
   * Реквизиты, одноимённые конкретно названным полям таблицы. Так к полю
   * подставляется тип из метаданных: {@code Владелец} справочника,
   * {@code Регистратор} регистра, {@code ТипЗначения} плана видов характеристик
   * платформа объявляет обобщённо.
   */
  private static List<? extends Attribute> namedFieldAttributes(ContextQueryTable table, MD md) {
    if (!(md instanceof AttributeOwner owner)) {
      return List.of();
    }
    var fieldNames = concreteFieldNames(table);
    return owner.getAllAttributes().stream()
      .filter(attribute -> matchesFieldName(attribute, fieldNames))
      .toList();
  }

  private static boolean matchesFieldName(Attribute attribute, Set<String> fieldNames) {
    var name = MdoMemberFactory.attributeBilingualName(attribute);
    return fieldNames.contains(name.primary().toLowerCase(Locale.ROOT))
      || (!name.en().isBlank() && fieldNames.contains(name.en().toLowerCase(Locale.ROOT)));
  }

  /** Имена полей таблицы без плейсхолдеров — оба написания, в нижнем регистре. */
  private static Set<String> concreteFieldNames(ContextQueryTable table) {
    var result = HashSet.<String>newHashSet(table.fields().size() * NAME_SPELLINGS);
    for (var field : table.fields()) {
      if (!QueryTableFieldSource.placeholderNames(field).isEmpty()) {
        continue;
      }
      result.add(field.name().getName().toLowerCase(Locale.ROOT));
      var alias = field.name().getAlias();
      if (!alias.isBlank()) {
        result.add(alias.toLowerCase(Locale.ROOT));
      }
    }
    return result;
  }
}
