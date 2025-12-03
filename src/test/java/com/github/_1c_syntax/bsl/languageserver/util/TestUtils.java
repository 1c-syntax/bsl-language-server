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
package com.github._1c_syntax.bsl.languageserver.util;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.utils.Absolute;
import org.jspecify.annotations.Nullable;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class TestUtils {

  public static final URI FAKE_DOCUMENT_URI = Absolute.uri("file:///fake-uri.bsl");
  public static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  @SneakyThrows
  public static DocumentContext getDocumentContextFromFile(String filePath) {

    String fileContent = FileUtils.readFileToString(
      new File(filePath),
      StandardCharsets.UTF_8
    );

    return getDocumentContext(Path.of(filePath).toUri(), fileContent);
  }

  public static DocumentContext getDocumentContext(URI uri, String fileContent) {
    return getDocumentContext(uri, fileContent, TestApplicationContext.getBean(ServerContext.class));
  }

  public static DocumentContext getDocumentContext(String fileContent) {
    return getDocumentContext(FAKE_DOCUMENT_URI, fileContent);
  }

  public static DocumentContext getDocumentContext(String fileContent, @Nullable ServerContext context) {
    ServerContext passedContext = context;
    if (passedContext == null) {
      passedContext = TestApplicationContext.getBean(ServerContext.class);
    }

    return getDocumentContext(FAKE_DOCUMENT_URI, fileContent, passedContext);
  }

  public static DocumentContext getDocumentContext(URI uri, String fileContent, ServerContext context) {
    var documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext, fileContent, 0);
    return documentContext;
  }

  public static TextDocumentIdentifier getTextDocumentIdentifier(URI uri) {
    return new TextDocumentIdentifier(uri.toString());
  }

}
