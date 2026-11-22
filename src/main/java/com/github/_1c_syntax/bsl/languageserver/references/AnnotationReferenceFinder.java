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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextConfigurationBindingChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationParamSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationPositionRepository;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationPositionRepository.AnnotationPosition;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationRepository;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

/**
 * Поиск ссылок на пользовательские аннотации OneScript и их параметры.
 * <p>
 * Позиции обращений сохраняются при разборе документа и переживают освобождение content/AST.
 * Цели разрешаются через актуальный реестр определений аннотаций одного workspace.
 */
@Component
@WorkspaceScope
@Order(180)
@RequiredArgsConstructor
public class AnnotationReferenceFinder implements ReferenceFinder {

  private final AnnotationRepository annotationRepository;
  private final AnnotationPositionRepository annotationPositionRepository;

  @EventListener
  public void handleContextRefresh(ServerContextPopulatedEvent event) {
    annotationRepository.clear();
    event.getSource().getDocuments().values().forEach(this::findAndRegisterAnnotation);
  }

  @EventListener
  public void handleDocumentContextChange(DocumentContextContentChangedEvent event) {
    var document = event.getSource();
    annotationRepository.removeByUri(document.getUri());
    findAndRegisterAnnotation(document);
    if (event.isContentChanged() || !annotationPositionRepository.contains(document.getUri())) {
      indexPositions(document);
    } else {
      annotationPositionRepository.refreshModule(document);
    }
  }

  @EventListener
  public void handleServerContextDocumentRemovedEvent(ServerContextDocumentRemovedEvent event) {
    annotationRepository.removeByUri(event.getUri());
    annotationPositionRepository.removeByUri(event.getUri());
  }

  @EventListener
  public void handleConfigurationBindingChanged(DocumentContextConfigurationBindingChangedEvent event) {
    var document = event.getSource();
    annotationRepository.removeByUri(document.getUri());
    findAndRegisterAnnotation(document);
    // Перепривязка меняет определения, но не текст: сохранённые позиции остаются актуальными.
    annotationPositionRepository.refreshModule(document);
  }

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {
    return annotationPositionRepository.find(uri, position)
      .filter(match -> match.module().getOwner().getFileType() == FileType.OS)
      .flatMap(match -> resolveSymbol(match.position()).map(symbol -> Reference.of(
        match.module(), symbol,
        new Location(uri.toString(), match.position().selectionRange()))));
  }

  private Optional<SourceDefinedSymbol> resolveSymbol(AnnotationPosition position) {
    var annotation = annotationRepository.findByName(position.annotationName());
    var parameterName = position.parameterName();
    if (parameterName == null) {
      return annotation.map(SourceDefinedSymbol.class::cast);
    }
    return annotation.flatMap(AnnotationSymbol::getParent)
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .map(method -> AnnotationParamSymbol.from(parameterName, method));
  }

  private void indexPositions(DocumentContext document) {
    if (document.getFileType() == FileType.OS) {
      var collector = new AnnotationPositionCollector();
      annotationPositionRepository.replace(document, collector.collect(document.getAst()));
    }
  }

  private void findAndRegisterAnnotation(DocumentContext documentContext) {
    // In normal case this method may be called twice per each document context:
    // 1. When the document context is created during the server context population or document opening
    // 2. When server context is fully populated.
    // This can lead to the situation when annotations registered from opened documents are cleared after
    // populateContext step.
    // Due to limitation of mechanism to only OS files, we can leave it as is for now, but it should be refactored
    // in the future.
    if (documentContext.getFileType() != FileType.OS) {
      return;
    }

    var symbolTree = documentContext.getSymbolTree();

    var methodSymbolAnnotationPair = symbolTree.getConstructor()
      .flatMap(AnnotationReferenceFinder::findAnnotation);

    methodSymbolAnnotationPair
      .map(Pair::getRight)
      .flatMap(AnnotationReferenceFinder::getAnnotationName)
      .flatMap(annotationName -> methodSymbolAnnotationPair.map(Pair::getLeft)
        .map(methodSymbol -> AnnotationSymbol.from(annotationName, methodSymbol)))
      .ifPresent(annotationRepository::register);
  }

  private static Optional<Pair<MethodSymbol, Annotation>> findAnnotation(MethodSymbol methodSymbol) {
    return methodSymbol.getAnnotations().stream()
      .filter(annotation -> annotation.getName().equalsIgnoreCase("Аннотация"))
      .findFirst()
      .filter(annotation -> annotation.getParameters().size() == 1)
      .map(annotation -> Pair.of(methodSymbol, annotation));
  }

  private static Optional<String> getAnnotationName(Annotation annotation) {
    var annotationParameterDefinition = annotation.getParameters().getFirst();
    return Optional.of(annotationParameterDefinition)
      .map(AnnotationParameterDefinition::value)
      .map(Either::getLeft);
  }
}
