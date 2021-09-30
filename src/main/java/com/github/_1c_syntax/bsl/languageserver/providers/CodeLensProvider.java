/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensData;
import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensSupplier;
import com.github._1c_syntax.bsl.languageserver.codelenses.databind.CodeLensDataObjectMapper;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.CodeLens;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CodeLensProvider {
  private final Map<String, CodeLensSupplier<CodeLensData>> codeLensSuppliers;
  private ObjectProvider<List<CodeLensSupplier<CodeLensData>>> enabledCodeLensSuppliersProvider;
  private final CodeLensDataObjectMapper codeLensDataObjectMapper;

  public CodeLensProvider(
    @Qualifier("codeLensSuppliersById") Map<String, CodeLensSupplier<CodeLensData>> codeLensSuppliers,
    @Qualifier("enabledCodeLensSuppliers") ObjectProvider<List<CodeLensSupplier<CodeLensData>>> enabledCodeLensSuppliersProvider,
    CodeLensDataObjectMapper objectMapper
  ) {
    this.codeLensSuppliers = codeLensSuppliers;
    this.enabledCodeLensSuppliersProvider = enabledCodeLensSuppliersProvider;
    this.codeLensDataObjectMapper = objectMapper;
  }

  public List<CodeLens> getCodeLens(DocumentContext documentContext) {
    // todo: надо предусмотреть, что если клиент не поддерживает асинхронный резолв,
    //  то код ленз провайдер должен вызывать явный резолв на своей стороне
    //  и отдавать полностью разрешенный код ленз на клиента.
    return enabledCodeLensSuppliersProvider.getObject().stream()
      .filter(codeLensSupplier -> codeLensSupplier.isApplicable(documentContext))
      .map(codeLensSupplier -> codeLensSupplier.getCodeLenses(documentContext))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  public CodeLens resolveCodeLens(
    DocumentContext documentContext,
    CodeLens unresolved,
    CodeLensData data
  ) {
    var codeLensSupplier = codeLensSuppliers.get(data.getId());
    var resolvedCodeLens = codeLensSupplier.resolve(documentContext, unresolved, data);
    resolvedCodeLens.setData(null);
    return resolvedCodeLens;
  }

  @SneakyThrows
  public CodeLensData extractData(CodeLens codeLens) {
    var rawCodeLensData = codeLens.getData();

    if (rawCodeLensData instanceof CodeLensData) {
      return (CodeLensData) rawCodeLensData;
    }

    return codeLensDataObjectMapper.readValue(rawCodeLensData.toString(), CodeLensData.class);
  }
}
