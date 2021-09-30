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
package com.github._1c_syntax.bsl.languageserver.codelenses.infrastructure;

import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensData;
import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensSupplier;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * Spring-конфигурация для определения бинов
 * пакета {@link com.github._1c_syntax.bsl.languageserver.codelenses}.
 */
@Configuration
public class CodeLensesConfiguration {

  /**
   * Получить список сапплаеров линз в разрезе их идентификаторов.
   *
   * @param codeLensSuppliers Плоский список сапплаеров.
   * @return Список сапплаеров линз в разрезе их идентификаторов.
   */
  @Bean
  @SuppressWarnings("unchecked")
  public Map<String, CodeLensSupplier<CodeLensData>> codeLensSuppliersById(
    Collection<CodeLensSupplier<? extends CodeLensData>> codeLensSuppliers
  ) {
    return codeLensSuppliers.stream()
      .map(codeLensSupplier -> (CodeLensSupplier<CodeLensData>) codeLensSupplier)
      .collect(Collectors.toMap(CodeLensSupplier::getId, Function.identity()));
  }

  @Bean
  @Scope(SCOPE_PROTOTYPE)
  public List<CodeLensSupplier<CodeLensData>> enabledCodeLensSuppliers(
    LanguageServerConfiguration configuration,
    @Qualifier("codeLensSuppliersById") Map<String, CodeLensSupplier<CodeLensData>> codeLensSuppliersById
  ) {
    List<CodeLensSupplier<CodeLensData>> suppliers = new ArrayList<>();
    if (configuration.getCodeLensOptions().isShowCognitiveComplexity()) {
      suppliers.add(codeLensSuppliersById.get("cognitiveComplexity"));
    }
    if (configuration.getCodeLensOptions().isShowCyclomaticComplexity()) {
      suppliers.add(codeLensSuppliersById.get("cyclomaticComplexity"));
    }

    return Collections.unmodifiableList(suppliers);
  }
}
