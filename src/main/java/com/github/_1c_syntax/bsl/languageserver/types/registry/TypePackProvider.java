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
   * Тип, который данный provider предоставляет (для регистрации и
   * последующей деинициализации). Используется как ключ группировки в реестре.
   *
   * @param exposedAsGlobal если {@code true} — имя типа также регистрируется
   *                        как глобальное свойство (его можно использовать как
   *                        ресивер dot-выражения: {@code КодировкаТекста.UTF8},
   *                        {@code Документы.Контрагенты}).
   */
  record TypeDecl(
    TypeKind kind,
    String qualifiedName,
    List<String> aliases,
    Collection<MemberDescriptor> members,
    boolean exposedAsGlobal,
    String description
  ) {

    public TypeDecl(
      TypeKind kind,
      String qualifiedName,
      List<String> aliases,
      Collection<MemberDescriptor> members,
      boolean exposedAsGlobal
    ) {
      this(kind, qualifiedName, aliases, members, exposedAsGlobal, "");
    }

    public TypeRef toRef() {
      return new TypeRef(kind, qualifiedName);
    }
  }

  /**
   * @return типы, регистрируемые этим provider'ом
   */
  Collection<TypeDecl> getTypes();
}
