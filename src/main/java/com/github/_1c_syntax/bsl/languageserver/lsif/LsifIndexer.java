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
package com.github._1c_syntax.bsl.languageserver.lsif;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.lsif.supplier.LsifDataSupplier;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Индексатор для генерации LSIF-дампа.
 * <p>
 * Обходит все документы проекта и генерирует LSIF-граф с использованием
 * зарегистрированных поставщиков данных.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LsifIndexer {

  private static final String LSIF_VERSION = "0.6.0";
  private static final String TOOL_NAME = "bsl-language-server";
  private static final String LANGUAGE_ID = "bsl";

  private final ServerContext serverContext;
  private final List<LsifDataSupplier> dataSuppliers;

  /**
   * Выполняет индексацию проекта и записывает LSIF-дамп.
   *
   * @param srcDir     путь к каталогу исходных файлов
   * @param outputFile путь к выходному файлу
   * @param toolVersion версия инструмента
   */
  public void index(Path srcDir, Path outputFile, String toolVersion) throws IOException {
    LOGGER.info("Starting LSIF indexing for {}", srcDir);

    var files = (List<File>) FileUtils.listFiles(srcDir.toFile(), new String[]{"bsl", "os"}, true);
    serverContext.populateContext(files);

    try (var emitter = new LsifEmitter(outputFile)) {
      // Emit metaData
      var projectRoot = srcDir.toUri().toString();
      emitter.emitMetaData(LSIF_VERSION, projectRoot, TOOL_NAME, toolVersion);

      // Emit project
      var projectId = emitter.emitProject(LANGUAGE_ID);

      // Process each document
      for (File file : files) {
        processDocument(file, projectId, emitter);
      }

      LOGGER.info("LSIF indexing completed. Output: {}", outputFile);
    }
  }

  private void processDocument(File file, long projectId, LsifEmitter emitter) {
    var uri = Absolute.uri(file);
    var documentContext = serverContext.getDocument(uri);

    if (documentContext == null) {
      LOGGER.warn("Document not found in context: {}", uri);
      return;
    }

    LOGGER.debug("Processing document: {}", uri);

    // Emit document vertex
    var documentId = emitter.emitDocument(uri.toString(), getLanguageId(file));

    // Link document to project
    emitter.emitBelongsTo(documentId, projectId);

    // Call all data suppliers
    for (LsifDataSupplier supplier : dataSuppliers) {
      supplier.supply(documentContext, documentId, emitter);
    }

    // Clean up AST to free memory
    serverContext.tryClearDocument(documentContext);
  }

  private String getLanguageId(File file) {
    var extension = file.getName().toLowerCase();
    if (extension.endsWith(".os")) {
      return "oscript";
    }
    return LANGUAGE_ID;
  }
}
