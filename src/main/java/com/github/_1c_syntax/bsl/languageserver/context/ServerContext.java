/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import com.github._1c_syntax.bsl.languageserver.utils.MdoRefBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.NamedForkJoinWorkerThreadFactory;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.mdclasses.MDClasses;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import com.github._1c_syntax.utils.Lazy;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerContext {
  private final ObjectProvider<DocumentContext> documentContextProvider;
  private final WorkDoneProgressHelper workDoneProgressHelper;
  private final LanguageServerConfiguration languageServerConfiguration;

  private final Map<URI, DocumentContext> documents = Collections.synchronizedMap(new HashMap<>());
  private final Lazy<CF> configurationMetadata = new Lazy<>(this::computeConfigurationMetadata);
  @Nullable
  @Setter
  private Path configurationRoot;
  private final Map<URI, String> mdoRefs = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, Map<ModuleType, DocumentContext>> documentsByMDORef
    = Collections.synchronizedMap(new HashMap<>());
  private final ReadWriteLock contextLock = new ReentrantReadWriteLock();

  private final Map<DocumentContext, State> states = new ConcurrentHashMap<>();
  private final Set<DocumentContext> openedDocuments = ConcurrentHashMap.newKeySet();

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
    var workDoneProgressReporter = workDoneProgressHelper.createProgress(
      files.size(),
      getMessage("populateFilesPostfix")
    );
    workDoneProgressReporter.beginProgress(getMessage("populatePopulatingContext"));

    LOGGER.debug("Populating context...");
    contextLock.writeLock().lock();

    try {

      files.parallelStream().forEach((File file) -> {

        workDoneProgressReporter.tick();

        var uri = file.toURI();
        var documentContext = getDocument(uri);
        if (documentContext == null) {
          documentContext = createDocumentContext(uri);
          rebuildDocument(documentContext);
          documentContext.freezeComputedData();
          tryClearDocument(documentContext);
        }
      });

    } finally {
      contextLock.writeLock().unlock();
    }

    workDoneProgressReporter.endProgress(getMessage("populateContextPopulated"));
    LOGGER.debug("Context populated.");
  }

  public Map<URI, DocumentContext> getDocuments() {
    return Collections.unmodifiableMap(documents);
  }

  @Nullable
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

  @Nullable
  public DocumentContext getDocument(URI uri) {
    return documents.get(Absolute.uri(uri));
  }

  public Map<ModuleType, DocumentContext> getDocuments(String mdoRef) {
    return documentsByMDORef.getOrDefault(mdoRef, Collections.emptyMap());
  }

  public DocumentContext addDocument(URI uri) {
    contextLock.readLock().lock();

    var documentContext = getDocument(uri);
    if (documentContext == null) {
      documentContext = createDocumentContext(uri);
    }

    contextLock.readLock().unlock();
    return documentContext;
  }

  public void removeDocument(URI uri) {
    var absoluteURI = Absolute.uri(uri);
    var documentContext = documents.get(absoluteURI);
    if (openedDocuments.contains(documentContext)) {
      throw new IllegalStateException(String.format("Document %s is opened", absoluteURI));
    }

    removeDocumentMdoRefByUri(absoluteURI);
    states.remove(documentContext);
    documents.remove(absoluteURI);
  }

  public void clear() {
    documents.clear();
    openedDocuments.clear();
    states.clear();
    documentsByMDORef.clear();
    mdoRefs.clear();
    configurationMetadata.clear();
  }

  /**
   * Помечает документ как открытый и перестраивает его содержимое
   * <p>
   * Документы, помеченные как открытые, не будут удаляться из контекста сервера при вызове {@link #removeDocument(URI)},
   * а так же не будут очищаться при вызове {@link #tryClearDocument(DocumentContext)}.
   * <p>
   * Если вспомогательные данные документа был в "замороженном" состоянии, то перед перестроением документа
   * они будут разморожены.
   *
   * @param documentContext документ, который необходимо открыть.
   * @param content         новое содержимое документа.
   * @param version         версия документа.
   */
  public void openDocument(DocumentContext documentContext, String content, Integer version) {
    openedDocuments.add(documentContext);
    documentContext.unfreezeComputedData();
    rebuildDocument(documentContext, content, version);
  }

  /**
   * Перестроить документ. В качестве содержимого будут использоваться данные,
   * прочитанные из файла, с которым связан документ.
   *
   * @param documentContext документ, который необходимо перестроить.
   */
  public void rebuildDocument(DocumentContext documentContext) {
    if (states.get(documentContext) == State.WITH_CONTENT) {
      return;
    }

    documentContext.rebuild();
    states.put(documentContext, State.WITH_CONTENT);
  }

  /**
   * Перестроить документ, используя новое содержимое.
   *
   * @param documentContext документ, который необходимо перестроить.
   * @param content         новое содержимое документа.
   * @param version         версия документа.
   */
  public void rebuildDocument(DocumentContext documentContext, String content, Integer version) {
    documentContext.rebuild(content, version);
    states.put(documentContext, State.WITH_CONTENT);
  }

  /**
   * Попытаться очистить документ, если он не открыт.
   *
   * @param documentContext документ, который необходимо попытаться закрыть.
   */
  public void tryClearDocument(DocumentContext documentContext) {
    if (openedDocuments.contains(documentContext)) {
      return;
    }

    states.put(documentContext, State.WITHOUT_CONTENT);
    documentContext.clearSecondaryData();
  }

  /**
   * Закрыть документ и очистить его содержимое.
   *
   * @param documentContext документ, который необходимо закрыть.
   */
  public void closeDocument(DocumentContext documentContext) {
    openedDocuments.remove(documentContext);
    states.put(documentContext, State.WITHOUT_CONTENT);
    documentContext.clearSecondaryData();
  }

  public CF getConfiguration() {
    return configurationMetadata.getOrCompute();
  }

  private DocumentContext createDocumentContext(URI uri) {
    var absoluteURI = Absolute.uri(uri);

    var documentContext = documentContextProvider.getObject(absoluteURI);

    documents.put(absoluteURI, documentContext);
    addMdoRefByUri(absoluteURI, documentContext);

    return documentContext;
  }

  private CF computeConfigurationMetadata() {
    if (configurationRoot == null) {
      return (CF) MDClasses.createConfiguration();
    }

    var progress = workDoneProgressHelper.createProgress(0, "");
    progress.beginProgress(getMessage("computeConfigurationMetadata"));

    var factory = new NamedForkJoinWorkerThreadFactory("compute-configuration-");
    var executorService = new ForkJoinPool(ForkJoinPool.getCommonPoolParallelism(), factory, null, true);

    CF configuration;
    try {
      configuration = (CF) executorService.submit(() -> MDClasses.createConfiguration(configurationRoot)).get();
    } catch (ExecutionException e) {
      LOGGER.error("Can't parse configuration metadata. Execution exception.", e);
      configuration = (CF) MDClasses.createConfiguration();
    } catch (InterruptedException e) {
      LOGGER.error("Can't parse configuration metadata. Interrupted exception.", e);
      configuration = (CF) MDClasses.createConfiguration();
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

  /**
   * Состояние документа в контексте.
   */
  private enum State {
    /**
     * В документе отсутствует контент или он был очищен.
     */
    WITHOUT_CONTENT,
    /**
     * В документе присутствует контент.
     */
    WITH_CONTENT
  }

}
