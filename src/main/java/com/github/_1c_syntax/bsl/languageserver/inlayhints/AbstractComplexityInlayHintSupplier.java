/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.computer.ComplexitySecondaryLocation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractComplexityInlayHintSupplier implements InlayHintSupplier {
  private final Map<URI, Set<String>> enabledMethods = new HashMap<>();

  @Override
  public List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params) {
    var enabledMethodsInFile = enabledMethods.getOrDefault(documentContext.getUri(), Collections.emptySet());
    var cognitiveComplexityLocations = getComplexityLocations(documentContext);

    return documentContext.getSymbolTree().getMethodsByName().entrySet().stream()
      .filter(entry -> enabledMethodsInFile.contains(entry.getKey()))
      .map(Map.Entry::getValue)
      .map(methodSymbol -> cognitiveComplexityLocations.getOrDefault(methodSymbol, Collections.emptyList()).stream()
        .map(AbstractComplexityInlayHintSupplier::toInlayHint)
        .collect(Collectors.toList()))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  protected abstract Map<MethodSymbol, List<ComplexitySecondaryLocation>> getComplexityLocations(
    DocumentContext documentContext
  );
  
  private static InlayHint toInlayHint(ComplexitySecondaryLocation complexitySecondaryLocation) {
    var inlayHint = new InlayHint();
    inlayHint.setPosition(complexitySecondaryLocation.getRange().getStart());
    inlayHint.setPaddingRight(Boolean.TRUE);
    inlayHint.setKind(InlayHintKind.Parameter);
    inlayHint.setLabel(complexitySecondaryLocation.getMessage());
    return inlayHint;
  }

  public void toggleHints(URI uri, String methodName) {
    var methodsInFile = enabledMethods.computeIfAbsent(uri, uri1 -> new HashSet<>());
    if (methodsInFile.contains(methodName)) {
      methodsInFile.remove(methodName);
    } else {
      methodsInFile.add(methodName);
    }
  }

}
