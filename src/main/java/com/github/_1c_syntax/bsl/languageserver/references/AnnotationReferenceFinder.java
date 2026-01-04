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
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationParamSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Methods;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Поиск ссылок на методы из аннотаций.
 * <p>
 * Обрабатывает аннотации (например, {@code &НаКлиенте}, {@code &НаСервере})
 * и находит ссылки на методы, указанные в них.
 */
@Component
@RequiredArgsConstructor
public class AnnotationReferenceFinder implements ReferenceFinder {

  private final ServerContext serverContext;
  private final Map<String, AnnotationSymbol> registeredAnnotations = new ConcurrentHashMap<>();

  @EventListener
  public void handleContextRefresh(ServerContextPopulatedEvent event) {
    registeredAnnotations.clear();
    serverContext.getDocuments()
      .values()
      .forEach(this::findAndRegisterAnnotation);
  }

  @EventListener
  public void handleDocumentContextChange(DocumentContextContentChangedEvent event) {
    DocumentContext documentContext = event.getSource();
    var uri = documentContext.getUri();

    removeAnnotationsRegisteredForUri(uri);
    findAndRegisterAnnotation(documentContext);
  }

  @EventListener
  public void handleServerContextDocumentRemovedEvent(ServerContextDocumentRemovedEvent event) {
    var uri = event.getUri();

    removeAnnotationsRegisteredForUri(uri);
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

    var methodSymbolAnnotationPair = Methods.getOscriptClassConstructor(symbolTree)
      .flatMap(AnnotationReferenceFinder::findAnnotation);

    methodSymbolAnnotationPair
      .map(Pair::getRight)
      .flatMap(AnnotationReferenceFinder::getAnnotationName)
      .flatMap(annotationName -> methodSymbolAnnotationPair.map(Pair::getLeft)
        .map(methodSymbol -> AnnotationSymbol.from(annotationName, methodSymbol)))
      .ifPresent(annotationSymbol ->
        registeredAnnotations.put(annotationSymbol.getName(), annotationSymbol));
  }

  private void removeAnnotationsRegisteredForUri(URI uri) {
    registeredAnnotations.values()
      .removeIf(annotationSymbol -> annotationSymbol.getOwner().getUri().equals(uri));
  }

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {
    DocumentContext documentContext = serverContext.getDocument(uri);
    if (documentContext == null || documentContext.getFileType() != FileType.OS) {
      return Optional.empty();
    }

    var maybeTerminalNode = Trees.findTerminalNodeContainsPosition(documentContext.getAst(), position);
    if (maybeTerminalNode.isEmpty()) {
      return Optional.empty();
    }

    var terminalNode = maybeTerminalNode.get();
    var parent = terminalNode.getParent();
    if (!(parent instanceof ParserRuleContext parentContext)) {
      return Optional.empty();
    }

    // Проверяем parentContext напрямую
    if (parentContext instanceof BSLParser.AnnotationNameContext annotationNameContext) {
      return getReferenceToAnnotationSymbol(uri, annotationNameContext, documentContext);
    }

    if (parentContext instanceof BSLParser.AnnotationParamNameContext annotationParamNameContext) {
      return getReferenceToAnnotationParamSymbol(annotationParamNameContext, documentContext);
    }

    // Ищем AnnotationParamValueContext среди parentContext и его предков
    var annotationParamValueContext = parentContext instanceof BSLParser.AnnotationParamValueContext apv
      ? apv
      : Trees.<BSLParser.AnnotationParamValueContext>getAncestorByRuleIndex(
      parentContext, BSLParser.RULE_annotationParamValue);

    return Optional.ofNullable(annotationParamValueContext)
      .flatMap(apv -> getReferenceToAnnotationParamSymbol(apv, documentContext));
  }

  private Optional<Reference> getReferenceToAnnotationSymbol(URI uri,
                                                             BSLParser.AnnotationNameContext annotationName,
                                                             DocumentContext documentContext) {
    var annotationNode = (BSLParser.AnnotationContext) annotationName.getParent();

    return getAnnotationSymbol(annotationName)
      .map(annotationSymbol -> Reference.of(
        documentContext.getSymbolTree().getModule(),
        annotationSymbol,
        new Location(uri.toString(), Ranges.create(annotationNode))
      ));
  }

  private Optional<Reference> getReferenceToAnnotationParamSymbol(
    BSLParser.AnnotationParamNameContext annotationParamName,
    DocumentContext documentContext) {

    return Optional.of(annotationParamName)
      .map(ParserRuleContext::getParent) // BSLParser.AnnotationParamContext
      .map(BSLParser.AnnotationParamContext.class::cast)
      .flatMap(annotationParamContext -> getReferenceToAnnotationParam(
        documentContext,
        Optional.of(annotationParamContext))
      );
  }

  private Optional<Reference> getReferenceToAnnotationParamSymbol(
    BSLParser.AnnotationParamValueContext annotationParamValue,
    DocumentContext documentContext) {

    return Optional.of(annotationParamValue)
      .map(ParserRuleContext::getParent) // BSLParser.AnnotationParamContext
      .filter(BSLParser.AnnotationParamContext.class::isInstance)
      .map(BSLParser.AnnotationParamContext.class::cast)
      .flatMap(annotationParamContext ->
        getReferenceToAnnotationParam(documentContext, Optional.of(annotationParamContext)));
  }

  private Optional<AnnotationSymbol> getAnnotationSymbol(BSLParser.AnnotationNameContext annotationNode) {
    var annotationName = annotationNode.getText();
    return Optional.ofNullable(registeredAnnotations.get(annotationName));
  }

  private Optional<Reference> getReferenceToAnnotationParam(
    DocumentContext documentContext,
    Optional<BSLParser.AnnotationParamContext> annotationParamContext
  ) {

    var annotationParamName = annotationParamContext
      .map(BSLParser.AnnotationParamContext::annotationParamName)
      .map(ParserRuleContext::getText)
      .orElse("Значение");

    ParserRuleContext annotationParamLocation = annotationParamContext
      .map(BSLParser.AnnotationParamContext::annotationParamName)
      .map(ParserRuleContext.class::cast)
      .or(() -> annotationParamContext.map(BSLParser.AnnotationParamContext::annotationParamValue))
      .orElseThrow();

    return annotationParamContext
      .map(ParserRuleContext::getParent) // BSLParser.AnnotationParamsContext
      .map(ParserRuleContext::getParent) // BSLParser.AnnotationContext
      .map(BSLParser.AnnotationContext.class::cast)

      .map(BSLParser.AnnotationContext::annotationName)
      .flatMap(this::getAnnotationSymbol)
      .flatMap(AnnotationSymbol::getParent)
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .map(annotationDefinitionMethodSymbol ->
        AnnotationParamSymbol.from(annotationParamName, annotationDefinitionMethodSymbol))
      .map(annotationParamSymbol -> Reference.of(
        documentContext.getSymbolTree().getModule(),
        annotationParamSymbol,
        new Location(documentContext.getUri().toString(), Ranges.create(annotationParamLocation))
      ));
  }

  private static Optional<Pair<MethodSymbol, Annotation>> findAnnotation(MethodSymbol methodSymbol) {
    return methodSymbol.getAnnotations().stream()
      .filter(annotation -> annotation.getName().equalsIgnoreCase("Аннотация"))
      .findFirst()
      .filter(annotation -> annotation.getParameters().size() == 1)
      .map(annotation -> Pair.of(methodSymbol, annotation));
  }

  private static Optional<String> getAnnotationName(Annotation annotation) {
    var annotationParameterDefinition = annotation.getParameters().get(0);
    return Optional.of(annotationParameterDefinition)
      .map(AnnotationParameterDefinition::value)
      .map(Either::getLeft);
  }
}
