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
package com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class DiagnosticBeanPostProcessor implements BeanPostProcessor {

  private final LanguageServerConfiguration configuration;
  private final Map<Class<? extends BSLDiagnostic>, DiagnosticInfo> diagnosticInfos;

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (!BSLDiagnostic.class.isAssignableFrom(bean.getClass())) {
      return bean;
    }

    BSLDiagnostic diagnostic = (BSLDiagnostic) bean;

    var info = diagnosticInfos.get(diagnostic.getClass());
    diagnostic.setInfo(info);

    return diagnostic;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {

    if (!BSLDiagnostic.class.isAssignableFrom(bean.getClass())) {
      return bean;
    }

    BSLDiagnostic diagnostic = (BSLDiagnostic) bean;

    Either<Boolean, Map<String, Object>> diagnosticConfiguration =
      configuration.getDiagnosticsOptions().getParameters().get(diagnostic.getInfo().getCode().getStringValue());

    if (diagnosticConfiguration != null && diagnosticConfiguration.isRight()) {
      diagnostic.configure(diagnosticConfiguration.getRight());
    }

    return diagnostic;
  }

}
