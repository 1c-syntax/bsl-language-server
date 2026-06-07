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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Разрешение имён OneScript-классов в документы и обратно — общий код для
 * провайдеров иерархии типов и перехода к реализациям.
 * <p>
 * Имя класса — то же, что используется в {@code Новый Имя} и в аннотациях
 * {@code &Расширяет}/{@code &Реализует}: для библиотечного класса это его
 * {@code qualifiedName} из {@code lib.config} (см. {@link OScriptLibraryIndex}),
 * для обычного {@code .os}-файла — basename.
 */
@Component
@RequiredArgsConstructor
public class OScriptClassResolver {

  private final OScriptLibraryIndex oScriptLibraryIndex;

  /**
   * Имена, под которыми класс известен другим классам: qualifiedNames
   * library-класса (их может быть несколько) либо basename файла для обычного
   * {@code .os}.
   *
   * @param documentContext контекст {@code .os}-документа-класса
   * @return непустой список имён класса
   */
  public List<String> classNames(DocumentContext documentContext) {
    var uri = documentContext.getUri();
    var libraryNames = oScriptLibraryIndex.findEntriesByUri(uri).stream()
      .filter(entry -> entry.kind() == OScriptLibraryIndex.EntryKind.CLASS)
      .map(OScriptLibraryIndex.LibraryEntry::qualifiedName)
      .distinct()
      .toList();
    if (!libraryNames.isEmpty()) {
      return libraryNames;
    }
    return List.of(FilenameUtils.getBaseName(uri.getPath()));
  }

  /**
   * Зарегистрирован ли документ как library-класс ({@code <class>} в {@code lib.config}
   * или convention-каталог {@code Классы}).
   */
  public boolean isLibraryClass(DocumentContext documentContext) {
    return oScriptLibraryIndex.findEntriesByUri(documentContext.getUri()).stream()
      .anyMatch(entry -> entry.kind() == OScriptLibraryIndex.EntryKind.CLASS);
  }

  /**
   * Разрешить имя класса ({@code &Расширяет("Имя")} / {@code &Реализует("Имя")})
   * в документ: сначала через каталог library-классов, затем — поиском по
   * basename среди {@code .os}-файлов контекста.
   *
   * @param name          имя класса
   * @param serverContext контекст сервера для поиска документа
   * @return документ класса либо {@link Optional#empty()}
   */
  public Optional<DocumentContext> resolveClassDocument(String name, ServerContext serverContext) {
    var libraryUri = oScriptLibraryIndex.findClassUri(name)
      .or(() -> oScriptLibraryIndex.findUri(name));
    if (libraryUri.isPresent()) {
      var document = serverContext.getDocument(libraryUri.get());
      if (document != null) {
        return Optional.of(document);
      }
    }
    // Deterministic выбор при совпадении basename у нескольких .os-файлов:
    // порядок итерации getDocuments() не гарантирован, поэтому сортируем по URI.
    return serverContext.getDocuments().values().stream()
      .filter(candidate -> candidate.getFileType() == FileType.OS)
      .filter(candidate -> FilenameUtils.getBaseName(candidate.getUri().getPath()).equalsIgnoreCase(name))
      .min(Comparator.comparing(candidate -> candidate.getUri().toString()));
  }
}
