/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
import com.github._1c_syntax.bsl.languageserver.documentlink.DocumentLinkSupplier;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DocumentLink;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Класс-провайдер для формирования списка ссылок на внешние источники информации.
 */
@Component
@RequiredArgsConstructor
public class DocumentLinkProvider {
  private final Collection<DocumentLinkSupplier> suppliers;

  public List<DocumentLink> getDocumentLinks(DocumentContext documentContext) {
    return suppliers.stream()
      .map(supplier -> supplier.getDocumentLinks(documentContext))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }
}
