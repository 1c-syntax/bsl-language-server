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

import com.github._1c_syntax.ls_core.context.CoreServerContext;
import com.github._1c_syntax.ls_core.context.DocumentContext;
import com.github._1c_syntax.mdclasses.metadata.Configuration;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import com.github._1c_syntax.utils.Lazy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
@Primary
public class BSLServerContext extends CoreServerContext {
  /**
   * Расширения анализируемых файлов
   */
  private static final String[] FILE_EXTENSIONS = new String[]{"bsl", "os"};

  private final Lazy<Configuration> configurationMetadata = new Lazy<>(this::computeConfigurationMetadata);

  private final Map<URI, String> mdoRefs = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, Map<ModuleType, BSLDocumentContext>> documentsByMDORef
    = Collections.synchronizedMap(new HashMap<>());
  private final ReadWriteLock contextLock = new ReentrantReadWriteLock();

  @Autowired
  public BSLServerContext() {
    super();
  }

  @Override
  public String[] sourceExtensions() {
    return FILE_EXTENSIONS;
  }

  @Override
  public void clear() {
    super.clear();
    documentsByMDORef.clear();
    mdoRefs.clear();
    configurationMetadata.clear();
  }

  @Override
  public void removeDocument(URI uri) {
    var absoluteURI = Absolute.uri(uri);
    removeDocumentMdoRefByUri(absoluteURI);
    super.removeDocument(absoluteURI);
  }

  @Override
  protected DocumentContext createDocumentContext(URI uri, String content) {
    var absoluteURI = Absolute.uri(uri);
    var documentContext = super.createDocumentContext(absoluteURI, content);
    addMdoRefByUri(absoluteURI, (BSLDocumentContext) documentContext);
    return documentContext;
  }

  @Override
  @Lookup
  protected DocumentContext lookupDocumentContext(URI absoluteURI, String content) {
    // так и должно быть, магия spring boot
    return null;
  }

  @Override
  public void populateContext(Collection<File> uris) {
    LOGGER.debug("Populating context...");
    contextLock.writeLock().lock();

    uris.parallelStream().forEach((File file) -> {
      var documentContext = (BSLDocumentContext) getDocument(file.toURI());
      if (documentContext == null) {
        documentContext = (BSLDocumentContext) createDocumentContext(file);
        documentContext.getSymbolTree();
        documentContext.clearSecondaryData();
      }
    });

    contextLock.writeLock().unlock();
    LOGGER.debug("Context populated.");
  }

  public Optional<BSLDocumentContext> getDocument(String mdoRef, ModuleType moduleType) {
    var documentsGroup = documentsByMDORef.get(mdoRef);
    if (documentsGroup != null) {
      return Optional.ofNullable(documentsGroup.get(moduleType));
    }
    return Optional.empty();
  }

  public Map<ModuleType, BSLDocumentContext> getDocuments(String mdoRef) {
    return documentsByMDORef.getOrDefault(mdoRef, Collections.emptyMap());
  }

  public Configuration getConfiguration() {
    return configurationMetadata.getOrCompute();
  }

  @SneakyThrows
  private Configuration computeConfigurationMetadata() {
    if (getProjectRoot() == null) {
      return Configuration.create();
    }
    var customThreadPool = new ForkJoinPool();
    return customThreadPool.submit(() -> Configuration.create(getProjectRoot())).get();
  }

  private void addMdoRefByUri(URI uri, BSLDocumentContext documentContext) {
    var modulesByObject = getConfiguration().getModulesByObject();
    var mdoByUri = modulesByObject.get(uri);

    if (mdoByUri != null) {
      var mdoRef = mdoByUri.getMdoReference().getMdoRef();
      mdoRefs.put(uri, mdoRef);
      var documentsGroup = documentsByMDORef.get(mdoRef);
      if (documentsGroup == null) {
        Map<ModuleType, BSLDocumentContext> newDocumentsGroup = new EnumMap<>(ModuleType.class);
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
      var documentContext = (BSLDocumentContext) getDocument(uri);
      if (documentsGroup != null && documentContext != null) {
        documentsGroup.remove(documentContext.getModuleType());
        if (documentsGroup.isEmpty()) {
          documentsByMDORef.remove(mdoRef);
        }
      }
      mdoRefs.remove(uri);
    }
  }
}
