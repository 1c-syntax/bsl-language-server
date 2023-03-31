/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.support.ScriptVariant;
import lombok.experimental.UtilityClass;

/**
 * Класс с методами-утилитами для MdoReference.
 */
@UtilityClass
public class MdoReferences {

  /**
   * Получить mdoRef в языке конфигурации
   *
   * @param documentContext the document context
   * @param mdo             the mdo
   * @return the locale mdoRef
   */
  public String getLocaleMdoRef(DocumentContext documentContext, MD mdo) {
    final var mdoReference = mdo.getMdoReference();
    if (documentContext.getServerContext().getConfiguration().getScriptVariant() == ScriptVariant.ENGLISH) {
      return mdoReference.getMdoRef();
    }
    return mdoReference.getMdoRefRu();
  }

  /**
   * Получить имя родителя метаданного в языке конфигурации.
   *
   * @param documentContext the document context
   * @param mdo             the mdo
   * @return the locale owner mdo name
   */
  public String getLocaleOwnerMdoName(DocumentContext documentContext, MD mdo) {
    final var names = getLocaleMdoRef(documentContext, mdo).split("\\.");
    if (names.length <= 1){
      return "";
    }
    return names[0].concat(".").concat(names[1]);
  }

}
