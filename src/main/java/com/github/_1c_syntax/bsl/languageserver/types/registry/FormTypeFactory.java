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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Заведение синтетических типов формы в реестре: у каждого из них два имени —
 * русское и английское, и оба должны резолвиться.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
class FormTypeFactory {

  private final TypeRegistry typeRegistry;

  /**
   * Регистрирует конфигурационный тип с английским алиасом.
   *
   * @param qualifiedRu русское имя типа.
   * @param qualifiedEn английское имя; пустое либо совпадающее алиаса не заводит.
   * @return тип из реестра.
   */
  TypeRef registerWithAlias(String qualifiedRu, String qualifiedEn) {
    var ref = typeRegistry.registerConfigurationType(qualifiedRu);
    if (!qualifiedEn.isBlank() && !qualifiedEn.equals(qualifiedRu)) {
      typeRegistry.registerConfigurationTypeAlias(qualifiedEn, ref);
    }
    typeRegistry.registerDisplayName(ref, BilingualString.of(qualifiedRu, qualifiedEn));
    return ref;
  }

  /**
   * Тип по имени, а если такого в реестре нет — интернированная ссылка на него.
   * Нужна там, где имя платформенного типа известно, а сам тип мог не приехать
   * (нет синтакс-помощника, другая версия платформы).
   *
   * @param qualifiedName имя типа.
   * @return тип из реестра либо интернированная ссылка.
   */
  TypeRef resolveOrIntern(String qualifiedName) {
    return typeRegistry.resolve(qualifiedName)
      .orElseGet(() -> typeRegistry.intern(TypeKind.PLATFORM, qualifiedName));
  }
}
