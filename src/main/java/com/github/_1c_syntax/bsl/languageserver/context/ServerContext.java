/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.utils.Absolute;
import com.github._1c_syntax.utils.Lazy;
import com.github._1c_syntax.mdclasses.metadata.Configuration;
import org.eclipse.lsp4j.TextDocumentItem;

import javax.annotation.CheckForNull;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ServerContext {
  private final Map<URI, DocumentContext> documents = Collections.synchronizedMap(new HashMap<>());
  private final Lazy<Configuration> configurationMetadata = new Lazy<>(this::computeConfigurationMetadata);
  @CheckForNull
  private Path configurationRoot;

  public ServerContext() {
    this(null);
  }

  public ServerContext(@CheckForNull Path configurationRoot) {
    this.configurationRoot = configurationRoot;
  }

  public Map<URI, DocumentContext> getDocuments() {
    return Collections.unmodifiableMap(documents);
  }

  @CheckForNull
  public DocumentContext getDocument(String uri) {
    return getDocument(URI.create(uri));
  }

  @CheckForNull
  public DocumentContext getDocument(URI uri) {
    return documents.get(Absolute.uri(uri));
  }

  public DocumentContext addDocument(URI uri, String content) {
    URI absoluteURI = Absolute.uri(uri);

    DocumentContext documentContext = getDocument(absoluteURI);
    if (documentContext == null) {
      documentContext = new DocumentContext(absoluteURI, content, this);
      documents.put(absoluteURI, documentContext);
    } else {
      documentContext.rebuild(content);
    }

    return documentContext;
  }

  public DocumentContext addDocument(TextDocumentItem textDocumentItem) {
    return addDocument(URI.create(textDocumentItem.getUri()), textDocumentItem.getText());
  }

  public void removeDocument(URI uri) {
    documents.remove(Absolute.uri(uri));
  }

  public void clear() {
    documents.clear();
    configurationMetadata.clear();
  }

  public void setConfigurationRoot(@CheckForNull Path configurationRoot) {
    this.configurationRoot = configurationRoot;
  }

  public Configuration getConfiguration() {
    return configurationMetadata.getOrCompute();
  }

  private Configuration computeConfigurationMetadata() {
    if (configurationRoot == null) {
      return Configuration.create();
    }

    return Configuration.create(configurationRoot);
  }
}
