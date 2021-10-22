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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.WorkDoneProgressHelper;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.Configuration;
import com.github._1c_syntax.utils.Absolute;
import com.github._1c_syntax.utils.Lazy;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.TextDocumentItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.annotation.CheckForNull;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerContext {
  private final ObjectProvider<DocumentContext> documentContextProvider;
  private final WorkDoneProgressHelper workDoneProgressHelper;
  private final LanguageServerConfiguration languageServerConfiguration;
  private final DiagnosticProvider diagnosticProvider;

  private final Map<URI, DocumentContext> documents = Collections.synchronizedMap(new HashMap<>());
  private final Lazy<Configuration> configurationMetadata = new Lazy<>(this::computeConfigurationMetadata);
  @CheckForNull
  @Setter
  private Path configurationRoot;
  private final Map<URI, String> mdoRefs = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, Map<ModuleType, DocumentContext>> documentsByMDORef
    = Collections.synchronizedMap(new HashMap<>());
  private final ReadWriteLock contextLock = new ReentrantReadWriteLock();

  public void populateContext() {
    if (configurationRoot == null) {
      LOGGER.info("Can't populate server context. Configuration root is not defined.");
      return;
    }

    var workDoneProgressReporter = workDoneProgressHelper.createProgress(0, "");
    workDoneProgressReporter.beginProgress(getMessage("populateFindFiles"));

    LOGGER.debug("Finding files to populate context...");
    var files = (List<File>) FileUtils.listFiles(
      configurationRoot.toFile(),
      new String[]{"bsl", "os"},
      true
    );
    workDoneProgressReporter.endProgress("");
    populateContext(files);
  }

  public void populateContext(List<File> files) {
    var workDoneProgressReporter = workDoneProgressHelper.createProgress(files.size(), getMessage("populateFilesPostfix"));
    workDoneProgressReporter.beginProgress(getMessage("populatePopulatingContext"));

    LOGGER.debug("Populating context...");
    contextLock.writeLock().lock();

    files.parallelStream().forEach((File file) -> {

      workDoneProgressReporter.tick();

      var documentContext = getDocument(file.toURI());
      if (documentContext == null) {
        documentContext = createDocumentContext(file);
        documentContext.freezeComputedData();
        documentContext.clearSecondaryData();
      }
    });

    contextLock.writeLock().unlock();

    workDoneProgressReporter.endProgress(getMessage("populateContextPopulated"));
    LOGGER.debug("Context populated.");
  }

  public Map<URI, DocumentContext> getDocuments() {
    return Collections.unmodifiableMap(documents);
  }

  @CheckForNull
  public DocumentContext getDocument(String uri) {
    return getDocument(URI.create(uri));
  }

  public Optional<DocumentContext> getDocument(String mdoRef, ModuleType moduleType) {
    var documentsGroup = documentsByMDORef.get(mdoRef);
    if (documentsGroup != null) {
      return Optional.ofNullable(documentsGroup.get(moduleType));
    }
    return Optional.empty();
  }

  @CheckForNull
  public DocumentContext getDocument(URI uri) {
    return documents.get(Absolute.uri(uri));
  }

  public Map<ModuleType, DocumentContext> getDocuments(String mdoRef) {
    return documentsByMDORef.getOrDefault(mdoRef, Collections.emptyMap());
  }

  public DocumentContext addDocument(URI uri, String content, int version) {
    contextLock.readLock().lock();

    var documentContext = getDocument(uri);
    if (documentContext == null) {
      documentContext = createDocumentContext(uri, content, version);
    } else {
      documentContext.rebuild(content, version);
      documentContext.unfreezeComputedData();
    }

    contextLock.readLock().unlock();
    return documentContext;
  }

  public DocumentContext addDocument(TextDocumentItem textDocumentItem) {
    return addDocument(
      URI.create(textDocumentItem.getUri()),
      textDocumentItem.getText(),
      textDocumentItem.getVersion()
    );
  }

  public void removeDocument(URI uri) {
    var absoluteURI = Absolute.uri(uri);
    removeDocumentMdoRefByUri(absoluteURI);
    documents.remove(absoluteURI);
  }

  public void clear() {
    documents.clear();
    documentsByMDORef.clear();
    mdoRefs.clear();
    configurationMetadata.clear();
  }

  public Configuration getConfiguration() {
    return configurationMetadata.getOrCompute();
  }

  @SneakyThrows
  private DocumentContext createDocumentContext(File file) {
    var content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    return createDocumentContext(file.toURI(), content, 0);
  }

  private DocumentContext createDocumentContext(URI uri, String content, int version) {
    var absoluteURI = Absolute.uri(uri);

    var documentContext = documentContextProvider.getObject(absoluteURI);
    documentContext.rebuild(content, version);

    documents.put(absoluteURI, documentContext);
    addMdoRefByUri(absoluteURI, documentContext);

    return documentContext;
  }

  private Configuration computeConfigurationMetadata() {
    if (configurationRoot == null) {
      return Configuration.create();
    }

    var progress = workDoneProgressHelper.createProgress(0, "");
    progress.beginProgress(getMessage("computeConfigurationMetadata"));

    Configuration configuration;
    var executorService = Executors.newCachedThreadPool();
    try {
      configuration = executorService.submit(() -> Configuration.create(configurationRoot)).get();
    } catch (ExecutionException e) {
      LOGGER.error("Can't parse configuration metadata. Execution exception.", e);
      configuration = Configuration.create();
    } catch (InterruptedException e) {
      LOGGER.error("Can't parse configuration metadata. Interrupted exception.", e);
      configuration = Configuration.create();
      Thread.currentThread().interrupt();
    } finally {
      executorService.shutdown();
    }

    progress.endProgress(getMessage("computeConfigurationMetadataDone"));

    return configuration;
  }

  private void addMdoRefByUri(URI uri, DocumentContext documentContext) {
    String mdoRef = MdoRefBuilder.getMdoRef(documentContext);

    mdoRefs.put(uri, mdoRef);
    documentsByMDORef.computeIfAbsent(
      mdoRef,
      k -> new EnumMap<>(ModuleType.class)
    ).put(documentContext.getModuleType(), documentContext);
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

  private String getMessage(String key) {
    return Resources.getResourceString(languageServerConfiguration.getLanguage(), getClass(), key);
  }
}
