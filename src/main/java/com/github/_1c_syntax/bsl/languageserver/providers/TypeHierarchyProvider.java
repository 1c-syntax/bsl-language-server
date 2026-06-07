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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptClassResolver;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptExtends;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Провайдер иерархии типов для OneScript-классов, использующих библиотеку
 * наследования <a href="https://github.com/oscript-library/extends">extends</a>
 * (автор — nixel2007).
 * <p>
 * Обрабатывает запросы {@code textDocument/prepareTypeHierarchy},
 * {@code typeHierarchy/supertypes} и {@code typeHierarchy/subtypes}.
 * <p>
 * Наследование в библиотеке {@code extends} объявляется аннотацией
 * {@code &Расширяет("ИмяРодителя")} (или её английским псевдонимом
 * {@code &Extends}) над конструктором класса {@code ПриСозданииОбъекта}:
 * <pre>
 *   &amp;Расширяет("Родитель")
 *   Процедура ПриСозданииОбъекта()
 *   КонецПроцедуры
 * </pre>
 * Имя родителя — то же, что используется в {@code Новый Родитель}: для
 * библиотечного класса это его {@code qualifiedName} из {@code lib.config}
 * (см. {@link OScriptClassResolver}), для обычного {@code .os}-файла — basename.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareTypeHierarchy">Prepare Type Hierarchy Request specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#typeHierarchy_supertypes">Type Hierarchy Supertypes specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#typeHierarchy_subtypes">Type Hierarchy Subtypes specification</a>
 */
@Component
@RequiredArgsConstructor
public class TypeHierarchyProvider {

  private final OScriptClassResolver classResolver;
  private final OScriptExtends oScriptExtends;

  private final Comparator<TypeHierarchyItem> itemComparator = Comparator
    .comparing(TypeHierarchyItem::getName, String.CASE_INSENSITIVE_ORDER)
    .thenComparing(TypeHierarchyItem::getUri);

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
    return oScriptExtends.parentClassName(documentContext)
      .flatMap(name -> classResolver.resolveClassDocument(name, documentContext.getServerContext()))
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
    var result = subtypeDocuments(documentContext).stream()
      .map(this::toItem)
      .sorted(itemComparator)
      .toList();
    return result.isEmpty() ? Collections.emptyList() : result;
  }

  /**
   * Документ участвует в иерархии типов, если это library-класс, объявляет
   * родителя через {@code &Расширяет} или сам является чьим-то родителем.
   */
  private boolean participatesInHierarchy(DocumentContext documentContext) {
    return classResolver.isLibraryClass(documentContext)
      || oScriptExtends.parentClassName(documentContext).isPresent()
      || !subtypeDocuments(documentContext).isEmpty();
  }

  /**
   * Все {@code .os}-классы workspace, объявившие данный класс своим родителем
   * через {@code &Расширяет}/{@code &Extends}.
   */
  private List<DocumentContext> subtypeDocuments(DocumentContext documentContext) {
    var ownNames = classResolver.classNames(documentContext).stream()
      .map(name -> name.toLowerCase(Locale.ROOT))
      .collect(Collectors.toSet());

    var result = new ArrayList<DocumentContext>();
    for (var candidate : documentContext.getServerContext().getDocuments().values()) {
      if (candidate.getFileType() != FileType.OS || candidate.getUri().equals(documentContext.getUri())) {
        continue;
      }
      oScriptExtends.parentClassName(candidate)
        .filter(parent -> ownNames.contains(parent.toLowerCase(Locale.ROOT)))
        .ifPresent(parent -> result.add(candidate));
    }
    return result;
  }

  private TypeHierarchyItem toItem(DocumentContext documentContext) {
    var module = documentContext.getSymbolTree().getModule();
    var names = new LinkedHashSet<>(classResolver.classNames(documentContext));

    var item = new TypeHierarchyItem(
      names.iterator().next(),
      SymbolKind.Class,
      documentContext.getUri().toString(),
      module.getRange(),
      selectionRange(documentContext)
    );
    oScriptExtends.parentClassName(documentContext).ifPresent(parent -> item.setDetail(": " + parent));
    return item;
  }

  /**
   * Диапазон выделения корневого элемента: subName-конструктора, если он есть
   * (на нём обычно объявлена аннотация {@code &Расширяет}), иначе — первый
   * токен модуля.
   */
  private static Range selectionRange(DocumentContext documentContext) {
    return documentContext.getSymbolTree().getConstructor()
      .map(MethodSymbol::getSelectionRange)
      .orElseGet(() -> {
        var module = documentContext.getSymbolTree().getModule();
        var range = module.getSelectionRange();
        return range != null ? range : Ranges.create(0, 0, 0, 0);
      });
  }
}
