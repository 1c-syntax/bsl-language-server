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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кем объявлен обработчик, живущий в модуле формы: самой формой, элементом шапки,
 * элементом таблицы или командой.
 * <p>
 * Для системы типов эта разница несущественна — все они висят на типе форме
 * {@code MemberKind.EVENT}-членами и резолвятся по имени. Но снаружи она видна:
 * от роли зависит и стандартная область модуля (ИТС std455), и то, как обработчик
 * называется в hover'е. Источник знания один — {@code Form.xml}, поэтому индекс
 * наполняет {@link FormTypesProvider} при регистрации формы.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class FormHandlerRoleIndex {

  /** Кем объявлен обработчик. */
  public enum Role {
    /** Событие самой формы ({@code ПриСозданииНаСервере}). */
    FORM_EVENT,
    /** Событие элемента вне таблиц. */
    HEADER_ITEM_EVENT,
    /** Событие элемента таблицы либо самой таблицы. */
    TABLE_ITEM_EVENT,
    /** Действие команды формы. */
    COMMAND
  }

  /**
   * Роль обработчика вместе с тем, кто его объявил.
   *
   * @param role  вид объявителя.
   * @param owner имя объявителя: элемента, команды или таблицы, которой принадлежит
   *              элемент. У события самой формы — пусто.
   */
  public record Handler(Role role, String owner) {
  }

  private final EventHandlerResolver eventHandlerResolver;

  private final Map<TypeRef, Map<String, Handler>> byForm = new ConcurrentHashMap<>();

  /**
   * Запоминает роли обработчиков одной формы.
   *
   * @param formRef  тип формы.
   * @param handlers обработчики по имени процедуры (в нижнем регистре).
   */
  void register(TypeRef formRef, Map<String, Handler> handlers) {
    if (handlers.isEmpty()) {
      byForm.remove(formRef);
    } else {
      byForm.put(formRef, Map.copyOf(handlers));
    }
  }

  /**
   * Роль обработчика по методу модуля.
   *
   * @param documentContext модуль, в котором объявлен метод.
   * @param methodName      имя метода.
   * @return роль; пусто, если модуль не формы либо метод обработчиком не объявлен.
   */
  public Optional<Handler> roleOf(DocumentContext documentContext, String methodName) {
    return eventHandlerResolver.eventOwnerTypeRef(documentContext)
      .map(byForm::get)
      .map(handlers -> handlers.get(methodName.toLowerCase(Locale.ROOT)));
  }
}
