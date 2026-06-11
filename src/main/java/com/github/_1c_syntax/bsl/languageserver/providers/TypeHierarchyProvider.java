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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.TypeRelations;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Провайдер иерархии типов для OneScript-классов, использующих библиотеку
 * наследования <a href="https://github.com/nixel2007/extends">extends</a>.
 * <p>
 * Обрабатывает запросы {@code textDocument/prepareTypeHierarchy},
 * {@code typeHierarchy/supertypes} и {@code typeHierarchy/subtypes}.
 * <p>
 * Отношения наследования провайдер не разбирает сам, а спрашивает у
 * {@link TypeRelations} — единой точки истины об {@code &Расширяет}; имена
 * классов для отображения берутся из {@link OScriptLibraryIndex}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareTypeHierarchy">Prepare Type Hierarchy Request specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#typeHierarchy_supertypes">Type Hierarchy Supertypes specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#typeHierarchy_subtypes">Type Hierarchy Subtypes specification</a>
 */
@Component
@RequiredArgsConstructor
public class TypeHierarchyProvider {

  private static final Comparator<TypeHierarchyItem> ITEM_COMPARATOR = Comparator
    .comparing(TypeHierarchyItem::getName, String.CASE_INSENSITIVE_ORDER)
    .thenComparing(TypeHierarchyItem::getUri);

  private final OScriptLibraryIndex oScriptLibraryIndex;
  private final TypeRelations typeRelations;

  /**
   * Подготовить корневой элемент иерархии типов для документа.
   * <p>
   * Иерархия строится только для {@code .os}-файлов, участвующих в наследовании
   * (класс-наследник, класс-родитель или library-класс). Позиция курсора не
   * влияет на результат: {@code .os}-файл целиком соответствует одному классу.
   *
   * @param documentContext контекст документа
   * @param params          параметры запроса
   * @return одноэлементный список с классом документа либо пустой список
   */
  public List<TypeHierarchyItem> prepareTypeHierarchy(
    DocumentContext documentContext,
    TypeHierarchyPrepareParams params
  ) {
    if (documentContext.getFileType() != FileType.OS) {
      return Collections.emptyList();
    }
    if (!participatesInHierarchy(documentContext)) {
      return Collections.emptyList();
    }
    return List.of(toItem(documentContext));
  }

  /**
   * Получить родительские классы (супертипы) элемента иерархии.
   *
   * @param documentContext контекст документа, соответствующего элементу
   * @param params          параметры запроса
   * @return список супертипов (обычно ноль или один — extends реализует
   *         одиночное наследование)
   */
  public List<TypeHierarchyItem> supertypes(
    DocumentContext documentContext,
    TypeHierarchySupertypesParams params
  ) {
    return typeRelations.supertype(documentContext)
      .map(this::toItem)
      .map(List::of)
      .orElseGet(Collections::emptyList);
  }

  /**
   * Получить дочерние классы (подтипы) элемента иерархии — все {@code .os}-классы
   * workspace, объявившие данный класс своим родителем через {@code &Расширяет}.
   *
   * @param documentContext контекст документа, соответствующего элементу
   * @param params          параметры запроса
   * @return отсортированный список подтипов
   */
  public List<TypeHierarchyItem> subtypes(
    DocumentContext documentContext,
    TypeHierarchySubtypesParams params
  ) {
    var result = typeRelations.subtypes(documentContext).stream()
      .map(this::toItem)
      .sorted(ITEM_COMPARATOR)
      .toList();
    return result.isEmpty() ? Collections.emptyList() : result;
  }

  /**
   * Документ участвует в иерархии типов, если это library-класс, объявляет
   * родителя через {@code &Расширяет} или сам является чьим-то родителем.
   */
  private boolean participatesInHierarchy(DocumentContext documentContext) {
    return oScriptLibraryIndex.isLibraryClass(documentContext)
      || typeRelations.supertypeName(documentContext).isPresent()
      || !typeRelations.subtypes(documentContext).isEmpty();
  }

  private TypeHierarchyItem toItem(DocumentContext documentContext) {
    var module = documentContext.getSymbolTree().getModule();
    // classNames по контракту непустой: qualifiedName library-класса либо basename файла.
    var primaryName = oScriptLibraryIndex.classNames(documentContext).getFirst();

    var item = new TypeHierarchyItem(
      primaryName,
      SymbolKind.Class,
      documentContext.getUri().toString(),
      module.getRange(),
      typeRelations.classSelectionRange(documentContext)
    );
    typeRelations.supertypeName(documentContext).ifPresent(parent -> item.setDetail(": " + parent));
    return item;
  }
}
