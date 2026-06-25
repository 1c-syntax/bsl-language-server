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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.DescriptionTypes;
import com.github._1c_syntax.bsl.parser.description.support.Hyperlink;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Единый для обоих языков (BSL и OneScript) резолвер типа переменной-свойства из
 * типизирующего висячего комментария её декларации
 * ({@code Перем X Экспорт; // Тип} / {@code // см. Метод}).
 * <p>
 * Используется и провайдером членов OneScript-классов
 * ({@code OScriptModuleMembersProvider}), и провайдером членов модулей
 * конфигурации ({@code ConfigurationModuleMembersProvider}, экспортные
 * переменные модулей объекта/набора записей и т.п.) — чтобы вывод типа из
 * комментария вёл себя одинаково независимо от языка.
 */
@Component
@RequiredArgsConstructor
public class MemberTypeFromCommentResolver {

  private final TypeRegistry typeRegistry;
  private final SymbolTypeIndex symbolTypeIndex;

  /**
   * Тип переменной-свойства из её висячего комментария.
   * <ul>
   *   <li>прямые типы {@code trailingDescription.getTypes()} ({@code // Массив из Число} —
   *       тип-голова через {@link DescriptionTypes#resolveName});</li>
   *   <li>если прямых типов нет — {@code См.}-ссылки {@code getLinks()}
   *       ({@code // см. НовыйСложно}): неквалифицированная ссылка на функцию того же модуля
   *       даёт её возвращаемый тип (через {@link SymbolTypeIndex#resolveSeeReference},
   *       поэтому переносятся и поля структуры/ТЗ из JsDoc), иначе трактуется как имя типа;
   *       квалифицированная ссылка ({@code Модуль.Метод}) разворачивается обходом членов
   *       через {@link SymbolTypeIndex#resolveHyperlink(String, FileType)}.</li>
   * </ul>
   *
   * @param variable переменная-свойство.
   * @param fileType язык владельца ({@link FileType#BSL} / {@link FileType#OS}) — для резолва имён.
   * @return {@link TypeSet} (возможно с {@code localFields}); {@link TypeSet#EMPTY}, если тип не выводится.
   */
  public TypeSet resolve(VariableSymbol variable, FileType fileType) {
    var trailing = variable.getDescription()
      .flatMap(description -> description.getTrailingDescription())
      .orElse(null);
    if (trailing == null) {
      return TypeSet.EMPTY;
    }

    var directRefs = new ArrayList<TypeRef>();
    var types = trailing.getTypes();
    if (types != null) {
      for (var td : types) {
        var name = DescriptionTypes.resolveName(td);
        if (!name.isBlank()) {
          typeRegistry.resolve(name, fileType).ifPresent(directRefs::add);
        }
      }
    }
    // Прямые типы приоритетнее; к См.-ссылкам обращаемся, только если тип не указан явно.
    if (!directRefs.isEmpty()) {
      return TypeSet.of(directRefs);
    }
    var result = TypeSet.EMPTY;
    for (var link : trailing.getLinks()) {
      result = result.union(hyperlinkTypes(variable, link, fileType));
    }
    return result;
  }

  private TypeSet hyperlinkTypes(VariableSymbol variable, Hyperlink link, FileType fileType) {
    return symbolTypeIndex.resolveSeeReference(link.link(), variable.getOwner(), fileType);
  }
}
