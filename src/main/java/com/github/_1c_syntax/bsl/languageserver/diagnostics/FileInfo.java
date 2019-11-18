/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.MetricStorage;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.eclipse.lsp4j.Diagnostic;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Value
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileInfo {
  private final Path path;
  private final List<Diagnostic> diagnostics;
  private MetricStorage metrics;

  public FileInfo(String sourceDir, DocumentContext documentContext, List<Diagnostic> diagnostics) {
    URI uri = URI.create(documentContext.getUri());
    path = Paths.get(sourceDir).toAbsolutePath().relativize(Paths.get(uri).toAbsolutePath());
    this.diagnostics = new ArrayList<>(diagnostics);
    metrics = documentContext.getMetrics();
  }
}
