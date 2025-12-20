/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.documentlink;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Supplier for forming links to URLs found in method and variable descriptions.
 */
@Component
@RequiredArgsConstructor
public class DescriptionDocumentLinkSupplier implements DocumentLinkSupplier {

  private static final Pattern URL_PATTERN = Pattern.compile(
    "(https?|ftp)://[^\\s\"'<>]+",
    Pattern.CASE_INSENSITIVE
  );

  @Override
  public List<DocumentLink> getDocumentLinks(DocumentContext documentContext) {
    var documentLinks = new ArrayList<DocumentLink>();
    var contentList = documentContext.getContentList();

    // Process method descriptions
    documentContext.getSymbolTree().getMethods().stream()
      .filter(method -> method.getDescription().isPresent())
      .forEach(method -> {
        var description = method.getDescription().get();
        documentLinks.addAll(extractLinksFromRange(contentList, description.getRange()));
      });

    // Process variable descriptions
    documentContext.getSymbolTree().getVariables().stream()
      .filter(variable -> variable.getDescription().isPresent())
      .forEach(variable -> {
        var description = variable.getDescription().get();
        documentLinks.addAll(extractLinksFromRange(contentList, description.getRange()));
      });

    return documentLinks;
  }

  private List<DocumentLink> extractLinksFromRange(String[] contentList, Range range) {
    var links = new ArrayList<DocumentLink>();
    int startLine = range.getStart().getLine();
    int endLine = range.getEnd().getLine();

    for (int lineNumber = startLine; lineNumber <= endLine && lineNumber < contentList.length; lineNumber++) {
      var line = contentList[lineNumber];
      var matcher = URL_PATTERN.matcher(line);

      while (matcher.find()) {
        var url = matcher.group();
        var linkRange = new Range(
          new Position(lineNumber, matcher.start()),
          new Position(lineNumber, matcher.end())
        );

        links.add(new DocumentLink(linkRange, url));
      }
    }

    return links;
  }
}
