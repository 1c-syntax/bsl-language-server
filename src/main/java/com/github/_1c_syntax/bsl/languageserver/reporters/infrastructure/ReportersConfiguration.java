/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.reporters.infrastructure;

import com.github._1c_syntax.bsl.languageserver.cli.AnalyzeCommand;
import com.github._1c_syntax.bsl.languageserver.reporters.DiagnosticReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class ReportersConfiguration {

  @Bean
  @Lazy
  public List<DiagnosticReporter> filteredReporters(
    Collection<DiagnosticReporter> allReporters,
    AnalyzeCommand command
  ) {
    List<String> reporterKeys = Arrays.asList(command.getReportersOptions());
    return allReporters.stream()
      .filter(diagnosticReporter -> reporterKeys.contains(diagnosticReporter.key()))
      .collect(Collectors.toList());
  }
}
