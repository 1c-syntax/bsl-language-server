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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.utils.Lazy;
import org.eclipse.lsp4j.TextDocumentItem;
import org.github._1c_syntax.mdclasses.metadata.Configuration;
import org.github._1c_syntax.mdclasses.metadata.ConfigurationBuilder;
import org.github._1c_syntax.mdclasses.metadata.additional.ConfigurationSource;

import javax.annotation.CheckForNull;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ServerContext {
  private final Map<String, DocumentContext> documents = Collections.synchronizedMap(new HashMap<>());
  private Path pathToConfigurationMetadata;
  private final Lazy<Configuration> configurationMetadata = new Lazy<>(this::computeConfigurationMetadata);

  public ServerContext () {
    this(null);
  }

  public ServerContext(Path pathToConfigurationMetadata) {
    this.pathToConfigurationMetadata = pathToConfigurationMetadata;
  }

  public Map<String, DocumentContext> getDocuments() {
    return Collections.unmodifiableMap(documents);
  }

  @CheckForNull
  public DocumentContext getDocument(String uri) {
    return documents.get(uri);
  }

  public DocumentContext addDocument(String uri, String content) {

    DocumentContext documentContext = documents.get(uri);
    if (documentContext == null) {
      documentContext = new DocumentContext(uri, content, this);
      documents.put(uri, documentContext);
    } else {
      documentContext.rebuild(content);
    }

    return documentContext;
  }

  public DocumentContext addDocument(TextDocumentItem textDocumentItem) {
    return addDocument(textDocumentItem.getUri(), textDocumentItem.getText());
  }

  public void clear() {
    documents.clear();
    configurationMetadata.clear();
  }

  public void setPathToConfigurationMetadata(Path pathToConfigurationMetadata) {
    this.pathToConfigurationMetadata = pathToConfigurationMetadata;
  }

  public Configuration getConfiguration () {
    return configurationMetadata.getOrCompute();
  }

  private Configuration computeConfigurationMetadata() {
    if (pathToConfigurationMetadata == null) {
      return null;
    }
    // TODO: для примера только конфигуратор
    ConfigurationBuilder configurationBuilder = new ConfigurationBuilder(ConfigurationSource.DESIGNER, pathToConfigurationMetadata);
    Configuration configuration;
    try {
      configuration = configurationBuilder.build();
    }
    catch (Exception e) {
      configuration = null;
      // TODO: нужно вывести в лог
    }
    return configuration;
  }



}
