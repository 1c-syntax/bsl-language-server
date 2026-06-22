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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
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
import java.util.stream.Stream;

/**
 * Сапплаер для формирования кликабельных ссылок «См.» (англ. «See»)
 * в описаниях символов.
 * <p>
 * В doc-комментарии символа BSL (метода либо переменной) допускается ссылка вида
 * {@code // См. ДругойМетод} (на метод того же модуля) либо {@code // См. ОбщийМодуль.Метод}
 * (на экспортный метод общего модуля). Сапплаер находит такие ссылки в описаниях
 * всех символов модуля, разрешает их в местоположение целевого метода и формирует
 * {@link DocumentLink} над текстом ссылки.
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
  private static void addLinksFromDescription(
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
   * <p>
   * Ссылка «См.» приходит из текста doc-комментария — это голая строка имени,
   * а не позиция в AST и не {@code Reference}. На текущей ветке {@code TypeService}
   * умеет резолвить только по позиции/{@code Reference} ({@code memberAt},
   * {@code typesAt(Reference)}) или возвращать {@code TypeRef} по имени
   * ({@code resolve(name, fileType)}), но не источниковый символ/{@code Location}
   * по имени. Поэтому имя метода резолвится напрямую через дерево символов модуля,
   * а имя общего модуля — штатной идиомой {@code findCommonModule → getDocument}.
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
      .map(SeeReferenceDocumentLinkSupplier::symbolLocation);
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
      .findCommonModule(moduleName)
      .map(commonModule -> commonModule.getMdoReference().getMdoRef())
      .flatMap(mdoRef ->
        documentContext.getServerContext().getDocument(mdoRef, ModuleType.CommonModule)
      )
      .flatMap(commonModuleDocument -> commonModuleDocument.getSymbolTree().getMethodSymbol(methodName))
      .map(SeeReferenceDocumentLinkSupplier::symbolLocation);
  }

  private static Location symbolLocation(SourceDefinedSymbol symbol) {
    return new Location(symbol.getOwner().getUri().toString(), symbol.getSelectionRange());
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
