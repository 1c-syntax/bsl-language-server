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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.mdo.CommonAttribute;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Общие реквизиты как поля таблицы, включая разделители.
 * <p>
 * В составе объекта метаданных их нет — принадлежность задаётся со стороны
 * самого общего реквизита, поэтому источник отдельный.
 */
@Component
@WorkspaceScope
@Order(CommonAttributeFieldSource.ORDER)
@RequiredArgsConstructor
class CommonAttributeFieldSource implements QueryTableFieldSource {

  static final int ORDER = 30;

  private final MdoMemberFactory mdoMembers;

  /**
   * Общие реквизиты последней спрошенной конфигурации. Их отбор — перебор всех объектов
   * конфигурации, а спрашивают о них по каждой таблице каждой формы с динамическим списком;
   * состав же меняется только вместе с экземпляром конфигурации. Сама конфигурация держится
   * слабо: перечитанная заменит прежнюю, и прежняя не должна жить из-за этой ссылки.
   */
  private final AtomicReference<CommonAttributes> lastConfiguration = new AtomicReference<>();

  @Override
  public List<MemberDescriptor> fields(QueryTableRequest request) {
    var md = request.mdo();
    var configuration = request.configuration();
    if (md == null || configuration == null || !declaresCommonAttributes(request)) {
      return List.of();
    }
    var all = commonAttributesOf(configuration);
    return mdoMembers.commonAttributeMembers(MdoMemberFactory.applicableCommonAttributes(md, all));
  }

  private List<CommonAttribute> commonAttributesOf(CF configuration) {
    var last = lastConfiguration.get();
    if (last != null && last.configuration().get() == configuration) {
      return last.attributes();
    }
    var attributes = configuration.getChildrenByMdoRef().values().stream()
      .filter(CommonAttribute.class::isInstance)
      .map(CommonAttribute.class::cast)
      .toList();
    lastConfiguration.set(new CommonAttributes(new WeakReference<>(configuration), attributes));
    return attributes;
  }

  private record CommonAttributes(WeakReference<CF> configuration, List<CommonAttribute> attributes) {
  }

  /**
   * Объявляет ли таблица общие реквизиты полями. Без платформенного описания
   * таблицы это известно только про собственную таблицу объекта: общий реквизит
   * — часть его состава, а показывает ли его виртуальная таблица, задаёт
   * платформа.
   */
  private static boolean declaresCommonAttributes(QueryTableRequest request) {
    return request.table() == null
      ? request.ownTableOfMdo()
      : request.declaredPlaceholders().contains(QueryTablePlaceholders.COMMON_ATTRIBUTE);
  }
}
