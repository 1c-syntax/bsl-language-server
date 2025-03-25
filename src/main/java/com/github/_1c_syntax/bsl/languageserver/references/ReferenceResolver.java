/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Основной класс, отвечающий за поиск ссылок на символы из под курсора.
 */
@Component
@RequiredArgsConstructor
public class ReferenceResolver {
  /**
   * Список конкретных поисковых движков.
   */
  private final List<ReferenceFinder> finders;

  /**
   * Поиск символа по позиции курсора.
   *
   * @param uri      URI документа, в котором необходимо осуществить поиск.
   * @param position позиция курсора.
   * @return данные ссылки.
   */
  public Optional<Reference> findReference(URI uri, Position position) {
    return finders.stream()
      .map(referenceFinder -> referenceFinder.findReference(uri, position))
      .flatMap(Optional::stream)
      .findFirst();
  }
}
