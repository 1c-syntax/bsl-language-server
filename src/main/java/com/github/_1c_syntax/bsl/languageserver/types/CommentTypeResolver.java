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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableTypeSource;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.DescriptionTypes;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.description.CollectionTypeDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import com.github._1c_syntax.bsl.parser.description.support.Hyperlink;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Тип, объявленный автором в комментарии рядом с кодом — единый для обоих языков
 * (BSL и OneScript) и для всех мест, где такой комментарий признаётся объявлением:
 * <ul>
 *   <li>объявление переменной: {@code Перем Х; // Строка -} или {@code Перем Х; // см. Метод};</li>
 *   <li>комментарий над объявлением: {@code // Дата - момент обновления} строкой выше
 *       {@code Перем Х;};</li>
 *   <li>строка присваивания: {@code Х = Ф(); // Строка -} — «типизация локальной переменной
 *       в строке» из методической рекомендации.</li>
 * </ul>
 * Висячий комментарий приоритетнее: он стоит вплотную к объявлению.
 * Разбор един намеренно: один и тот же комментарий обязан пониматься одинаково и когда
 * переменную спрашивают как член типа (провайдеры членов модулей), и когда её тип выводят
 * по коду ({@link VariableTypeSource}).
 * <p>
 * Сам текст не разбирается: типы структурно выделяет парсер описаний, здесь их резолвят
 * в реестре, а {@code см.}-ссылки разворачивают через {@link SymbolTypeIndex} — поэтому
 * вместе с типом приходят и поля структуры или таблицы значений из его описания.
 */
@Component
@RequiredArgsConstructor
public class CommentTypeResolver implements VariableTypeSource {

  private final TypeRegistry typeRegistry;
  private final SymbolTypeIndex symbolTypeIndex;

  /**
   * Типы из висячего комментария объявления переменной.
   *
   * @param variable переменная.
   * @return объявленные типы; пустой набор, если комментария нет либо типы в нём
   *     не распознаны.
   */
  @Override
  public TypeSet typesOf(VariableSymbol variable) {
    return resolve(variable, variable.getOwner().getFileType());
  }

  /**
   * Тип переменной из её комментария — висячего либо стоящего над объявлением.
   * <ul>
   *   <li>прямые типы описания ({@code // Массив из Число} —
   *       тип-голова через {@link DescriptionTypes#resolveName});</li>
   *   <li>если прямых типов нет — {@code См.}-ссылки {@code getLinks()}
   *       ({@code // см. НовыйСложно}): неквалифицированная ссылка на функцию того же модуля
   *       даёт её возвращаемый тип (через {@link SymbolTypeIndex#resolveSeeReference},
   *       поэтому переносятся и поля структуры/ТЗ из JsDoc), иначе трактуется как имя типа;
   *       квалифицированная ссылка ({@code Модуль.Метод}) разворачивается обходом членов
   *       через {@link SymbolTypeIndex#resolveHyperlink(String, FileType)}.</li>
   * </ul>
   *
   * @param variable переменная.
   * @param fileType язык владельца ({@link FileType#BSL} / {@link FileType#OS}) — для резолва имён.
   * @return {@link TypeSet} (возможно с {@code localFields}); {@link TypeSet#EMPTY}, если тип не выводится.
   */
  public TypeSet resolve(VariableSymbol variable, FileType fileType) {
    var description = variable.getDescription().orElse(null);
    if (description == null) {
      return TypeSet.EMPTY;
    }
    var owner = variable.getOwner();
    var trailing = description.getTrailingDescription().orElse(null);
    if (trailing != null) {
      var fromTrailing = typesOfComment(trailing.getTypes(), trailing.getLinks(), owner, fileType);
      if (!fromTrailing.isEmpty()) {
        return fromTrailing;
      }
    }
    return typesOfComment(description.getTypes(), description.getLinks(), owner, fileType);
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
    return typesOfComment(trailing.getTypes(), trailing.getLinks(), owner, owner.getFileType());
  }

  /**
   * Общий разбор описания: прямые типы приоритетнее, к {@code см.}-ссылкам обращаемся,
   * только если тип не указан явно.
   *
   * @param types    типы из описания.
   * @param links    ссылки из описания.
   * @param owner    документ-владелец — для разворота ссылок на функции того же модуля.
   * @param fileType язык, на котором резолвятся имена.
   * @return найденные типы; пустой набор, если не нашлось ни одного.
   */
  private TypeSet typesOfComment(
    @Nullable List<TypeDescription> types,
    List<Hyperlink> links,
    DocumentContext owner,
    FileType fileType
  ) {
    var direct = TypeSet.EMPTY;
    if (types != null) {
      for (var type : types) {
        var name = DescriptionTypes.resolveName(type);
        if (name.isBlank()) {
          continue;
        }
        var ref = typeRegistry.resolve(name, fileType).orElse(null);
        if (ref != null) {
          direct = direct.union(withElementTypes(ref, type, fileType));
        }
      }
    }
    if (!direct.isEmpty()) {
      return direct;
    }
    var result = TypeSet.EMPTY;
    for (var link : links) {
      result = result.union(symbolTypeIndex.resolveSeeReference(link.link(), owner, fileType));
    }
    return result;
  }

  /**
   * Тип-голова вместе с типами элементов записи {@code Массив из Строка}.
   * <p>
   * Элементы навешиваются только там, где элемент коллекции сам по себе неизвестен:
   * у {@code Массив} реестр знает лишь {@code Произвольный}, и объявленный тип его
   * уточняет. Если же у коллекции элемент собственный — {@code КлючИЗначение} у
   * {@code Соответствие}, {@code ЭлементСпискаЗначений} у {@code СписокЗначений} —
   * запись {@code из Строка} называет не элемент, а значение внутри него, и выразить
   * это одной строкой нельзя. Реестровый элемент тогда точнее, объявленный отбрасывается.
   *
   * @param headRef  разрешённый тип-голова записи.
   * @param type     описание типа из комментария.
   * @param fileType язык, на котором резолвятся имена элементов.
   * @return набор из одного типа-головы, при наличии — с типами элементов.
   */
  private TypeSet withElementTypes(TypeRef headRef, TypeDescription type, FileType fileType) {
    var head = TypeSet.of(headRef);
    if (!(type instanceof CollectionTypeDescription collection) || hasOwnElementType(headRef)) {
      return head;
    }
    var elements = new ArrayList<TypeRef>();
    for (var valueType : collection.valueTypes()) {
      var name = DescriptionTypes.resolveName(valueType);
      if (!name.isBlank()) {
        typeRegistry.resolve(name, fileType).ifPresent(elements::add);
      }
    }
    return elements.isEmpty() ? head : head.withElement(headRef, TypeSet.of(elements));
  }

  /**
   * Знает ли реестр собственный тип элемента коллекции.
   *
   * @param ref тип коллекции.
   * @return {@code true}, если элемент известен и это не {@link TypeRef#ANY} —
   *     заглушка «элемент любой».
   */
  private boolean hasOwnElementType(TypeRef ref) {
    var defaults = typeRegistry.getDefaultElementTypes(ref).refs();
    return !defaults.isEmpty() && !defaults.equals(Set.of(TypeRef.ANY));
  }
}
