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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.description.support.Hyperlink;
import com.github._1c_syntax.bsl.parser.description.support.SimpleRange;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сапплаер для формирования кликабельных ссылок «См.» (англ. «See»)
 * в описаниях методов.
 * <p>
 * В doc-комментарии метода BSL допускается ссылка вида {@code // См. ДругойМетод}
 * (на метод того же модуля) либо {@code // См. ОбщийМодуль.Метод} (на экспортный
 * метод общего модуля). Сапплаер находит такие ссылки, разрешает их в местоположение
 * целевого метода и формирует {@link DocumentLink} над текстом ссылки.
 * <p>
 * Ссылки, которые не удалось разрешить в существующий метод, пропускаются —
 * висячие (битые) ссылки не создаются.
 */
@Component
public class SeeReferenceDocumentLinkSupplier implements DocumentLinkSupplier {

  private static final char MODULE_METHOD_SEPARATOR = '.';

  @Override
  public List<DocumentLink> getDocumentLinks(DocumentContext documentContext) {
    var documentLinks = new ArrayList<DocumentLink>();

    for (MethodSymbol method : documentContext.getSymbolTree().getMethods()) {
      method.getDescription().ifPresent(description ->
        description.getLinks().forEach(link ->
          addLinkIfResolvable(documentContext, link, documentLinks)
        )
      );
    }

    return documentLinks;
  }

  private static void addLinkIfResolvable(
    DocumentContext documentContext,
    Hyperlink hyperlink,
    List<DocumentLink> documentLinks
  ) {
    var reference = hyperlink.link();
    if (reference.isEmpty()) {
      return;
    }

    resolveTarget(documentContext, reference)
      .ifPresent(target -> {
        var range = referenceRange(hyperlink, reference);
        var documentLink = new DocumentLink(range, locationToTarget(target));
        documentLinks.add(documentLink);
      });
  }

  /**
   * Разрешает текст ссылки «См.» в местоположение целевого метода.
   *
   * @param documentContext контекст документа, в котором встретилась ссылка
   * @param reference       текст ссылки: {@code ИмяМетода} или {@code ОбщийМодуль.Метод}
   *
   * @return местоположение объявления метода, если ссылка разрешима, иначе {@link Optional#empty()}
   */
  private static Optional<Location> resolveTarget(DocumentContext documentContext, String reference) {
    var separatorIndex = reference.indexOf(MODULE_METHOD_SEPARATOR);
    if (separatorIndex < 0) {
      return resolveSameModuleMethod(documentContext, reference);
    }

    var moduleName = reference.substring(0, separatorIndex);
    var methodName = reference.substring(separatorIndex + 1);
    return resolveCommonModuleMethod(documentContext, moduleName, methodName);
  }

  private static Optional<Location> resolveSameModuleMethod(
    DocumentContext documentContext,
    String methodName
  ) {
    return documentContext.getSymbolTree()
      .getMethodSymbol(methodName)
      .map(SeeReferenceDocumentLinkSupplier::methodLocation);
  }

  private static Optional<Location> resolveCommonModuleMethod(
    DocumentContext documentContext,
    String moduleName,
    String methodName
  ) {
    if (moduleName.isEmpty() || methodName.isEmpty()) {
      return Optional.empty();
    }

    return documentContext.getServerContext()
      .getConfiguration()
      .findCommonModule(moduleName)
      .map(commonModule -> commonModule.getMdoReference().getMdoRef())
      .flatMap(mdoRef ->
        documentContext.getServerContext().getDocument(mdoRef, ModuleType.CommonModule)
      )
      .flatMap(commonModuleDocument -> commonModuleDocument.getSymbolTree().getMethodSymbol(methodName))
      .map(SeeReferenceDocumentLinkSupplier::methodLocation);
  }

  private static Location methodLocation(MethodSymbol method) {
    return new Location(method.getOwner().getUri().toString(), method.getSelectionRange());
  }

  private static String locationToTarget(Location location) {
    var range = location.getRange();
    var start = range.getStart();
    return "%s#L%d,%d".formatted(location.getUri(), start.getLine() + 1, start.getCharacter() + 1);
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
