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
package com.github._1c_syntax.bsl.languageserver.documentlink;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DocumentLink;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Сапплаер для формирования кликабельных ссылок из http(s)-адресов,
 * встречающихся в комментариях кода (ссылки на ИТС, задачи, стандарты и т.п.).
 */
@Component
public class CommentUrlDocumentLinkSupplier implements DocumentLinkSupplier {

  private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>)\\]}]+");

  @Override
  public List<DocumentLink> getDocumentLinks(DocumentContext documentContext) {
    var documentLinks = new ArrayList<DocumentLink>();

    for (Token comment : documentContext.getComments()) {
      addLinksFromComment(comment, documentLinks);
    }

    return documentLinks;
  }

  private static void addLinksFromComment(Token comment, List<DocumentLink> documentLinks) {
    var text = comment.getText();
    var matcher = URL_PATTERN.matcher(text);

    var line = comment.getLine() - 1;
    var commentStartChar = comment.getCharPositionInLine();

    while (matcher.find()) {
      var url = matcher.group();
      var startChar = commentStartChar + matcher.start();
      var endChar = commentStartChar + matcher.end();
      var range = Ranges.create(line, startChar, endChar);

      documentLinks.add(new DocumentLink(range, url));
    }
  }
}
