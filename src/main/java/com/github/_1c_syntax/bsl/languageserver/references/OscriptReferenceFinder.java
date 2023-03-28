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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.providers.SelectionRangeProvider;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;


@Component
@RequiredArgsConstructor
public class OscriptReferenceFinder implements ReferenceFinder {

  private final ServerContext serverContext;

  @Override
  public Optional<Reference> findReference(URI uri, Position position) {

    DocumentContext document = serverContext.getDocument(uri);
    if (document == null || document.isComputedDataFrozen()) {
      return Optional.empty();
    }

    var node = SelectionRangeProvider.findNodeContainsPosition(document.getAst(), position);

    if (node.isEmpty()) {
      return Optional.empty();
    }

    return serverContext.getDocuments().values().stream()
      .filter(documentContext -> documentContext.getTypeName().equals(node.get().getText()))
      .filter(d -> filterByType(node, d.getModuleType()))
      .map(documentContext -> new Reference(
        documentContext.getSymbolTree().getModule(),
        documentContext.getSymbolTree().getModule(),
        documentContext.getUri(),
        Ranges.create(0, 0, 0, 0),
        OccurrenceType.DEFINITION)
      ).findAny();

  }

  private boolean filterByType(Optional<TerminalNode> node, ModuleType moduleType) {

    if (node.isEmpty()) {
      return false;
    }

    if ((node.get().getParent() instanceof BSLParser.TypeNameContext)
      && moduleType == ModuleType.OScriptClass) {
      return true;
    } else {
      return (node.get().getParent() instanceof BSLParser.ComplexIdentifierContext)
        && moduleType == ModuleType.OScriptModule;
    }

  }

}
