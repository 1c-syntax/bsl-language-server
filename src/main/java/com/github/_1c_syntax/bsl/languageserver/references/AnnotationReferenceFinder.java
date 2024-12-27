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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Methods;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AnnotationReferenceFinder implements ReferenceFinder {

  private final ServerContext serverContext;
  private final Map<String, AnnotationSymbol> registeredAnnotations = new CaseInsensitiveMap<>();

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

    registeredAnnotations.values().stream()
      .filter(annotationSymbol -> annotationSymbol.getOwner().getUri().equals(uri))
      .forEach(annotationSymbol -> registeredAnnotations.remove(annotationSymbol.getName()));

    findAndRegisterAnnotation(documentContext);
  }

  private void findAndRegisterAnnotation(DocumentContext documentContext) {
    if (documentContext.getFileType() != FileType.OS) {
      return;
    }

    var symbolTree = documentContext.getSymbolTree();

    Methods.getOscriptClassConstructor(symbolTree)
      .flatMap(AnnotationReferenceFinder::findAnnotation)
      .map(methodSymbolAnnotationPair -> AnnotationSymbol.from(getAnnotationName(methodSymbolAnnotationPair.getRight()), methodSymbolAnnotationPair.getLeft()))
      .ifPresent(annotationSymbol -> registeredAnnotations.put(annotationSymbol.getName(), annotationSymbol));
  }

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {
    DocumentContext document = serverContext.getDocument(uri);
    if (document == null || document.getFileType() != FileType.OS) {
      return Optional.empty();
    }

    return Trees.findTerminalNodeContainsPosition(document.getAst(), position)
      .filter(node -> node.getParent().getRuleContext().getRuleIndex() == BSLParser.RULE_annotationName)
      .flatMap((TerminalNode annotationNode) -> {
        var annotationName = annotationNode.getText();
        var annotationSymbol = registeredAnnotations.get(annotationName);
        if (annotationSymbol == null) {
          return Optional.empty();
        }
        return Optional.of(Reference.of(
          document.getSymbolTree().getModule(),
          annotationSymbol,
          new Location(uri.toString(), Ranges.create(annotationNode.getParent().getParent()))
        ));
      });
  }

  private static Optional<Pair<MethodSymbol, Annotation>> findAnnotation(MethodSymbol methodSymbol) {
    return methodSymbol.getAnnotations().stream()
      .filter(annotation -> annotation.getName().equalsIgnoreCase("Аннотация"))
      .findFirst()
      .map(annotation -> Pair.of(methodSymbol, annotation));
  }

  private static String getAnnotationName(Annotation annotation) {
    return annotation.getParameters().get(0).getValue();
  }
}
