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
import com.github._1c_syntax.bsl.languageserver.context.symbol.Describable;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.SourceSymbolLinks;
import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import com.github._1c_syntax.bsl.parser.description.support.Hyperlink;
import com.github._1c_syntax.bsl.parser.description.support.SimpleRange;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Сапплаер для формирования кликабельных ссылок «См.» (англ. «See»)
 * в описаниях символов.
 * <p>
 * В doc-комментарии символа BSL (метода либо переменной) допускается ссылка вида
 * {@code // См. ДругойМетод} (на метод того же модуля) либо {@code // См. Модуль.Метод}
 * (на экспортный метод другого модуля — общего, менеджера и т.п.). Сапплаер находит
 * такие ссылки в описаниях всех символов модуля, разрешает их в определение целевого
 * метода через {@link TypeService} и формирует {@link DocumentLink} над текстом ссылки.
 * <p>
 * Ссылки, которые не удалось разрешить в существующий метод, пропускаются —
 * висячие (битые) ссылки не создаются.
 */
@Component
@RequiredArgsConstructor
public class SeeReferenceDocumentLinkSupplier implements DocumentLinkSupplier {

  private final TypeService typeService;

  @Override
  public List<DocumentLink> getDocumentLinks(DocumentContext documentContext) {
    var documentLinks = new ArrayList<DocumentLink>();

    var symbolTree = documentContext.getSymbolTree();
    Stream.<Describable>concat(
      symbolTree.getMethods().stream(),
      symbolTree.getVariables().stream()
    ).forEach(describable ->
      describable.getDescription().ifPresent(description ->
        addLinksFromDescription(documentContext, description, documentLinks)
      )
    );

    return documentLinks;
  }

  /**
   * Добавляет ссылки «См.» из описания символа, включая описание висячего (trailing)
   * комментария переменной.
   * <p>
   * Висячий комментарий ({@code Перем П; // См. ДругойМетод}) хранится отдельным
   * {@link VariableDescription} в {@link VariableDescription#getTrailingDescription()},
   * и его ссылки не попадают в {@code getLinks()} основного описания, поэтому обрабатываются явно.
   */
  private void addLinksFromDescription(
    DocumentContext documentContext,
    SourceDefinedSymbolDescription description,
    List<DocumentLink> documentLinks
  ) {
    description.getLinks().forEach(link ->
      addLinkIfResolvable(documentContext, link, documentLinks)
    );

    if (description instanceof VariableDescription variableDescription) {
      variableDescription.getTrailingDescription().ifPresent(trailingDescription ->
        addLinksFromDescription(documentContext, trailingDescription, documentLinks)
      );
    }
  }

  private void addLinkIfResolvable(
    DocumentContext documentContext,
    Hyperlink hyperlink,
    List<DocumentLink> documentLinks
  ) {
    var reference = hyperlink.link();
    if (reference.isEmpty()) {
      return;
    }

    typeService.resolveSeeReference(reference, documentContext)
      .ifPresent(target -> {
        var range = referenceRange(hyperlink, reference);
        var documentLink = new DocumentLink(range, SourceSymbolLinks.navigationTarget(target));
        documentLinks.add(documentLink);
      });
  }

  /**
   * Вычисляет диапазон именно текста ссылки внутри гиперссылки «См.».
   * <p>
   * Диапазон самой гиперссылки охватывает ключевое слово «См.»/«See» вместе со ссылкой,
   * поэтому диапазон ссылки вычисляется по её длине от конца гиперссылки.
   *
   * @param hyperlink гиперссылка, разобранная парсером описания
   * @param reference текст ссылки
   *
   * @return диапазон текста ссылки в координатах документа
   */
  private static Range referenceRange(Hyperlink hyperlink, String reference) {
    SimpleRange range = hyperlink.range();
    var endLine = range.endLine();
    var endCharacter = range.endCharacter();
    var startCharacter = endCharacter - reference.length();
    return Ranges.create(endLine, startCharacter, endLine, endCharacter);
  }
}
