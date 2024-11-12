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
import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AnnotationReferenceFinder implements ReferenceFinder {

  private final ServerContext serverContext;

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {
    DocumentContext document = serverContext.getDocument(uri);
    if (document == null || document.getFileType() != FileType.OS) {
      return Optional.empty();
    }

    var registeredAnnotations = serverContext.getDocuments().values().stream()
      .filter(documentContext -> documentContext.getFileType() == FileType.OS)
      .map(DocumentContext::getSymbolTree)
      .flatMap(symbolTree -> symbolTree.getMethodSymbol("ПриСозданииОбъекта").stream())
      .filter(methodSymbol -> methodSymbol.getAnnotations().stream().anyMatch(annotation -> annotation.getName().equals("Аннотация")))
      .map(methodSymbol -> Pair.of(methodSymbol, methodSymbol.getAnnotations().stream().filter(annotation -> annotation.getName().equals("Аннотация")).findFirst().get()))
      .collect(Collectors.toMap(methodSymbolAnnotationPair -> methodSymbolAnnotationPair.getRight().getParameters().get(0).getValue(), Pair::getLeft));

    return Trees.findTerminalNodeContainsPosition(document.getAst(), position)
      .filter(node -> node.getParent().getRuleContext().getRuleIndex() == BSLParser.RULE_annotationName)
      .flatMap((TerminalNode annotationNode) -> {
        var annotationName = annotationNode.getText();
        var foundAnnotationDeclaration = registeredAnnotations.get(annotationName);
        if (foundAnnotationDeclaration == null) {
          return Optional.empty();
        }
        return Optional.of(Reference.of(
          document.getSymbolTree().getModule(),
          AnnotationSymbol.from(annotationName, foundAnnotationDeclaration),
          new Location(uri.toString(), Ranges.create(annotationNode.getParent().getParent()))
        ));
      });
  }
}
