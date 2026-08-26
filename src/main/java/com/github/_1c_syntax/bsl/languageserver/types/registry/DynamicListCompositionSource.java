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

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListField;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Поля, которые динамический список объявил сам — записи состава полей.
 * <p>
 * Спрашивается первым: объявление списка точнее любого вывода по таблице.
 * Тип у записи состава указывают редко (в разобранных выгрузках — 42 записи из
 * 18 087), поэтому чаще всего запись даёт только имя, а тип поле получает от
 * следующего источника.
 * <p>
 * Поля со сгруппированным путём ({@code СубконтоДт.СубконтоДт1}) колонкой
 * строки не становятся: обратиться к ним можно только через владельца группы.
 */
@Component
@WorkspaceScope
@Order(DynamicListCompositionSource.ORDER)
@RequiredArgsConstructor
class DynamicListCompositionSource implements QueryTableFieldSource {

  static final int ORDER = 10;

  private final TypeRegistry typeRegistry;

  @Override
  public List<MemberDescriptor> fields(QueryTableRequest request) {
    var list = request.list();
    if (list == null || list.getFields().isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<MemberDescriptor>(list.getFields().size());
    for (var field : list.getFields()) {
      var member = member(field);
      if (member != null) {
        result.add(member);
      }
    }
    return List.copyOf(result);
  }

  private @Nullable MemberDescriptor member(FormDynamicListField field) {
    var name = field.getDataPath().isBlank() ? field.getName() : field.getDataPath();
    if (name.isBlank() || name.contains(".")) {
      return null;
    }
    if (field.getValueType().isEmpty()) {
      // Тип не объявлен — имя всё равно нужно: без него поля не будет вовсе.
      return MemberDescriptor.property(name);
    }
    return MdoMemberFactory.property(BilingualString.of(name),
      ValueTypes.resolve(typeRegistry, field.getValueType()));
  }
}
