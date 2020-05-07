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

import com.github._1c_syntax.mdclasses.mdo.MDObjectBase;
import com.github._1c_syntax.mdclasses.metadata.Configuration;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import com.github._1c_syntax.utils.Lazy;
import org.eclipse.lsp4j.TextDocumentItem;

import javax.annotation.CheckForNull;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class ServerContext {
  private final Map<URI, DocumentContext> documents = Collections.synchronizedMap(new HashMap<>());
  private final Lazy<Configuration> configurationMetadata = new Lazy<>(this::computeConfigurationMetadata);
  @CheckForNull
  private Path configurationRoot;
  private final Map<URI, String> mdoRefs = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, Map<ModuleType, DocumentContext>> documentsByMDORef
    = Collections.synchronizedMap(new HashMap<>());

  public ServerContext() {
    this(null);
  }

  public ServerContext(@CheckForNull Path configurationRoot) {
    this.configurationRoot = configurationRoot;
  }

  public Map<URI, DocumentContext> getDocuments() {
    return Collections.unmodifiableMap(documents);
  }

  public Map<URI, String> getMdoRefs() {
    return Collections.unmodifiableMap(mdoRefs);
  }

  public Map<String, Map<ModuleType, DocumentContext>> getDocumentsByMdoRef() {
    return Collections.unmodifiableMap(documentsByMDORef);
  }

  @CheckForNull
  public DocumentContext getDocument(String uri) {
    return getDocument(URI.create(uri));
  }

  public DocumentContext getDocument(String mdoRef, ModuleType moduleType) {
    var documentsGroup = documentsByMDORef.get(mdoRef);
    if (documentsGroup != null) {
      return documentsGroup.get(moduleType);
    }
    return null;
  }

  @CheckForNull
  public DocumentContext getDocument(URI uri) {
    return documents.get(Absolute.uri(uri));
  }

  @CheckForNull
  public Map<ModuleType, DocumentContext> getDocumentsByMdoRef(String mdoRef) {
    return documentsByMDORef.get(mdoRef);
  }

  public DocumentContext addDocument(URI uri, String content) {
    URI absoluteURI = Absolute.uri(uri);

    DocumentContext documentContext = getDocument(absoluteURI);
    if (documentContext == null) {
      documentContext = new DocumentContext(absoluteURI, content, this);
      documents.put(absoluteURI, documentContext);
      addMdoRefByUri(absoluteURI, documentContext);
    } else {
      documentContext.rebuild(content);
    }

    return documentContext;
  }

  public DocumentContext addDocument(TextDocumentItem textDocumentItem) {
    return addDocument(URI.create(textDocumentItem.getUri()), textDocumentItem.getText());
  }

  public void removeDocument(URI uri) {
    URI absoluteURI = Absolute.uri(uri);
    removeDocumentMdoRefByUri(absoluteURI);
    documents.remove(absoluteURI);
  }

  public void clear() {
    documents.clear();
    documentsByMDORef.clear();
    mdoRefs.clear();
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

  private void addMdoRefByUri(URI uri, DocumentContext documentContext) {
    addMdoRefByUri(getConfiguration().getModulesByURI(), uri, documentContext);
  }

  private void addMdoRefByUri(Map<URI, MDObjectBase> modulesByUri, URI uri, DocumentContext documentContext) {
    var mdoByUri = modulesByUri.get(uri);
    if (mdoByUri != null) {
      var mdoRef = mdoByUri.getMdoRef();
      mdoRefs.put(uri, mdoRef);
      var documentsGroup = documentsByMDORef.get(mdoRef);
      if (documentsGroup == null) {
        Map<ModuleType, DocumentContext> newDocumentsGroup = new EnumMap<>(ModuleType.class);
        newDocumentsGroup.put(documentContext.getModuleType(), documentContext);
        documentsByMDORef.put(mdoRef, newDocumentsGroup);
      } else {
        documentsGroup.put(documentContext.getModuleType(), documentContext);
      }
    }
  }

  private void removeDocumentMdoRefByUri(URI uri) {
    var mdoRef = mdoRefs.get(uri);
    if (mdoRef != null) {
      var documentsGroup = documentsByMDORef.get(mdoRef);
      if (documentsGroup != null) {
        documentsGroup.remove(documents.get(uri).getModuleType());
        if (documentsGroup.isEmpty()) {
          documentsByMDORef.remove(mdoRef);
        }
      }
      mdoRefs.remove(uri);
    }
  }
}
