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
package com.github._1c_syntax.bsl.languageserver.reporters.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.MetricStorage;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import com.github._1c_syntax.utils.Absolute;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.eclipse.lsp4j.Diagnostic;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Value
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileInfo {
  Path path;
  String mdoRef;
  List<Diagnostic> diagnostics;
  MetricStorage metrics;

  public FileInfo(String sourceDir, DocumentContext documentContext, List<Diagnostic> diagnostics) {
    URI uri = documentContext.getUri();
    path = Absolute.path(sourceDir).relativize(Absolute.path(uri));
    this.diagnostics = new ArrayList<>(diagnostics);
    metrics = documentContext.getMetrics();
    Optional<AbstractMDObjectBase> mdObjectBase = documentContext.getMdObject();
    if (mdObjectBase.isPresent()) {
      mdoRef = mdObjectBase.get().getMdoReference().getMdoRef();
    } else {
      mdoRef = "";
    }
  }
}
