/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.databind.AnalysisInfoObjectMapper;
import com.github._1c_syntax.bsl.types.MDOType;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class UsageReporter implements DiagnosticReporter {

  private final ServerContext serverContext;
  private final ReferenceIndex referenceIndex;


  @Override
  public String key() {
    return "usage";
  }

  @Override
  @SneakyThrows
  public void report(AnalysisInfo analysisInfo, Path outputDir) {
    ObjectMapper mapper = new AnalysisInfoObjectMapper();

    var emptyMethods = new ArrayList<Pair<URI, String>>();

    serverContext.getDocuments().values().forEach(documentContext -> {
      var skip = new AtomicBoolean(false);
      documentContext.getMdObject().ifPresent(abstractMDObjectBase -> {
          var mdoType = abstractMDObjectBase.getMdoType();
          if (mdoType == MDOType.FORM || mdoType == MDOType.COMMON_FORM) {
            skip.set(true);
          }
        }
      );

      if (skip.get()) {
        return;
      }
      documentContext.getSymbolTree().getMethods().forEach(methodSymbol -> {
        if (referenceIndex.getReferencesTo(methodSymbol).isEmpty()) {
          emptyMethods.add(Pair.of(documentContext.getUri(), methodSymbol.getName()));
        }
      });

      documentContext.clearSecondaryData();
    });


    var map = emptyMethods.stream()
      .collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));

    try {
      File reportFile = new File(outputDir.toFile(), "./bsl-usage.json");
      mapper.writeValue(reportFile, map);
      LOGGER.info("Usage report saved to {}", reportFile.getAbsolutePath());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
