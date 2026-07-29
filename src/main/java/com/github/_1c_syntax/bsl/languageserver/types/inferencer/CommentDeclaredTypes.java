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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.DescriptionTypes;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Тип переменной, объявленный автором в комментарии рядом с кодом.
 * <p>
 * Два места, где такой комментарий признаётся объявлением:
 * <ul>
 *   <li>висячий комментарий объявления — {@code Перем Х; // Тип - Строка};</li>
 *   <li>висячий комментарий строки присваивания — {@code Х = Ф(); // Тип - Строка}.
 *   Это «встроенная типизация локальной переменной» из стандарта 1С:EDT.</li>
 * </ul>
 * Сам текст не разбирается: типы структурно выделяет парсер описаний, здесь их только
 * резолвят в реестре. Для записи коллекции ({@code Массив из Число}) парсер отдаёт один
 * тип-голову — {@code Массив}.
 */
@Component
@RequiredArgsConstructor
public class CommentDeclaredTypes {

  private final TypeRegistry typeRegistry;

  /**
   * Типы из висячего комментария объявления переменной.
   *
   * @param variable переменная.
   * @return объявленные типы; пустой набор, если комментария нет либо типы в нём
   *     не распознаны.
   */
  public TypeSet ofDeclaration(VariableSymbol variable) {
    var description = variable.getDescription().orElse(null);
    if (description == null) {
      return TypeSet.EMPTY;
    }
    var trailing = description.getTrailingDescription().orElse(null);
    if (trailing == null) {
      return TypeSet.EMPTY;
    }
    return resolve(trailing.getTypes(), variable.getOwner().getFileType());
  }

  /**
   * Типы из висячего комментария в строке присваивания.
   * <p>
   * Комментарий разбирается тем же парсером описаний, что и комментарий объявления:
   * из токена строится описание переменной, а типы берутся из его висячей части.
   *
   * @param owner      документ с присваиванием.
   * @param assignment оператор присваивания.
   * @return объявленные типы; пустой набор, если комментария нет либо типы в нём
   *     не распознаны.
   */
  public TypeSet ofAssignment(DocumentContext owner, BSLParser.AssignmentContext assignment) {
    var trailingComment = Trees.getTrailingComment(owner.getTokens(), assignment.getStop());
    if (trailingComment.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var trailing = VariableDescription.create(Collections.emptyList(), trailingComment)
      .getTrailingDescription()
      .orElse(null);
    if (trailing == null) {
      return TypeSet.EMPTY;
    }
    return resolve(trailing.getTypes(), owner.getFileType());
  }

  /**
   * Резолвить разобранные парсером типы комментария по их именам.
   *
   * @param types    типы из описания.
   * @param fileType язык, на котором резолвятся имена.
   * @return найденные в реестре типы; пустой набор, если не нашлось ни одного.
   */
  private TypeSet resolve(@Nullable List<TypeDescription> types, FileType fileType) {
    if (types == null || types.isEmpty()) {
      return TypeSet.EMPTY;
    }
    Set<TypeRef> refs = new LinkedHashSet<>();
    for (var type : types) {
      var typeName = DescriptionTypes.resolveName(type);
      if (!typeName.isBlank()) {
        typeRegistry.resolve(typeName, fileType).ifPresent(refs::add);
      }
    }
    return refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(refs);
  }
}
