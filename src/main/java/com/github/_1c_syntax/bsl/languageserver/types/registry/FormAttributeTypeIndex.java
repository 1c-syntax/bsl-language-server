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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Типы реквизитов формы <b>так, как они объявлены в {@code Form.xml}</b>: реквизит
 * {@code Объект} — это {@code ДокументОбъект.ЗаказПоставщику}, а не
 * {@code ДанныеФормыСтруктура}, в которую он превращён на форме.
 * <p>
 * Свойством формы такой тип не выставишь — на форме за реквизитом действительно стоят
 * данные, — но он и есть результат обратного преобразования:
 * {@code РеквизитФормыВЗначение("Объект")} возвращает ровно объявленный тип. Знание
 * приходит оттуда же, откуда типы данных формы, поэтому индекс наполняет
 * {@link FormTypesProvider} при регистрации формы, а читает — вывод типов.
 * <p>
 * Хранятся только реквизиты управляемых форм: у обычной формы преобразования нет,
 * и функций обратного преобразования у неё тоже нет.
 */
@Component
@WorkspaceScope
public class FormAttributeTypeIndex {

  /** {@code тип формы → (имя реквизита в нижнем регистре → объявленный тип)}. */
  private final Map<TypeRef, Map<String, TypeSet>> byForm = new ConcurrentHashMap<>();

  /**
   * Запоминает объявленные типы реквизитов одной формы.
   *
   * @param formRef       тип формы.
   * @param declaredTypes типы по имени реквизита в нижнем регистре.
   */
  void register(TypeRef formRef, Map<String, TypeSet> declaredTypes) {
    if (declaredTypes.isEmpty()) {
      byForm.remove(formRef);
    } else {
      byForm.put(formRef, Map.copyOf(declaredTypes));
    }
  }

  /**
   * Объявленный тип реквизита формы.
   *
   * @param formRef       тип формы.
   * @param attributeName имя реквизита.
   * @return объявленный тип; пустой набор, если это не форма либо реквизита с таким
   *     именем у неё нет.
   */
  public TypeSet declaredType(TypeRef formRef, String attributeName) {
    return byForm.getOrDefault(formRef, Map.of())
      .getOrDefault(attributeName.toLowerCase(Locale.ROOT), TypeSet.EMPTY);
  }
}
