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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.client.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.TypeRelations;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ImplementationCapabilities;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Провайдер запроса {@code textDocument/implementation} для интерфейсов
 * OneScript библиотеки <a href="https://github.com/nixel2007/extends">extends</a>.
 * <p>
 * Интерфейс объявляется аннотацией-маркером {@code &Интерфейс} на конструкторе
 * класса-интерфейса; класс реализует его аннотацией {@code &Реализует("Интерфейс")}
 * (повторяемой). Переход к реализациям:
 * <ul>
 *   <li>курсор на экспортном методе интерфейса → одноимённые методы во всех
 *       реализующих классах;</li>
 *   <li>курсор в любом другом месте файла-интерфейса → сами реализующие классы.</li>
 * </ul>
 * Сам разбор отношений {@code &Реализует}/{@code &Расширяет} (в т.ч.
 * транзитивный обход через абстрактных родителей и иерархию интерфейсов)
 * делегирован {@link TypeRelations#implementors}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_implementation">Goto Implementation Request specification</a>
 */
@Component
@RequiredArgsConstructor
public class ImplementationProvider {

  private final TypeRelations typeRelations;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  // Результаты — по одной связи на класс/метод-реализатор, поэтому targetUri
  // уникален: сортировки по нему достаточно для детерминированного порядка.
  private final Comparator<LocationLink> linkComparator = Comparator.comparing(LocationLink::getTargetUri);

  // Кэшируется на initialize. linkSupport — gate для ответа LocationLink[] на запрос
  // textDocument/implementation. Если клиент не заявил поддержку, ответ понижается до Location[].
  private boolean linkSupport;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Кэширует клиентскую возможность {@code textDocument.implementation.linkSupport},
   * влияющую на формат ответа навигации: при её отсутствии ответ {@link LocationLink}
   * понижается до {@link Location}.
   */
  @EventListener(LanguageServerInitializeRequestReceivedEvent.class)
  public void handleInitializeEvent() {
    linkSupport = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getImplementation)
      .map(ImplementationCapabilities::getLinkSupport)
      .orElse(Boolean.FALSE);
  }

  /**
   * Найти реализации интерфейса, представленного текущим документом, в формате,
   * согласованном с клиентскими возможностями.
   * <p>
   * По спецификации LSP 3.14+ ответ типа {@link LocationLink} допустим, только если
   * клиент заявил {@code textDocument.implementation.linkSupport}. При наличии поддержки
   * возвращается правая сторона ({@link LocationLink}) с диапазоном-источником под
   * курсором ({@code originSelectionRange}) и диапазонами цели; иначе результат
   * понижается до левой стороны ({@link Location}) с {@code targetUri} и
   * {@code targetSelectionRange} каждой связи.
   *
   * @param documentContext контекст документа (ожидается файл-интерфейс)
   * @param params          параметры запроса (позиция курсора)
   * @return {@link Either} со списком {@link LocationLink} при поддержке связей либо
   *         со списком {@link Location} при её отсутствии; пустой список, если документ
   *         не является интерфейсом
   */
  public Either<List<? extends Location>, List<? extends LocationLink>> getImplementations(
    DocumentContext documentContext,
    ImplementationParams params
  ) {
    var links = findLocationLinks(documentContext, params);

    if (linkSupport) {
      return Either.forRight(links);
    }

    List<Location> locations = links.stream()
      .map(link -> new Location(link.getTargetUri(), link.getTargetSelectionRange()))
      .toList();
    return Either.forLeft(locations);
  }

  private List<LocationLink> findLocationLinks(DocumentContext documentContext, ImplementationParams params) {
    if (documentContext.getFileType() != FileType.OS
      || !typeRelations.isInterface(documentContext)) {
      return Collections.emptyList();
    }

    var interfaceMethod = exportMethodAt(documentContext, params);

    var result = new ArrayList<LocationLink>();
    for (var implementor : typeRelations.implementors(documentContext)) {
      if (interfaceMethod != null) {
        // originSelectionRange — диапазон идентификатора метода интерфейса под курсором.
        var originSelectionRange = interfaceMethod.getSelectionRange();
        implementor.getSymbolTree().getMethodSymbol(interfaceMethod.getName())
          .filter(MethodSymbol::isExport)
          .ifPresent(method -> result.add(
            link(implementor, method.getRange(), method.getSelectionRange(), originSelectionRange)));
      } else {
        var classSelectionRange = typeRelations.classSelectionRange(implementor);
        result.add(classLink(implementor, classRange(implementor), classSelectionRange));
      }
    }
    result.sort(linkComparator);
    return result;
  }

  /**
   * Экспортный метод интерфейса под курсором (для перехода к одноимённым
   * реализациям), либо {@code null}, если курсор не на экспортном методе (тогда
   * переход — к самим реализующим классам). Конструктор {@code ПриСозданииОбъекта}
   * по соглашению не экспортный, поэтому отсекается фильтром экспортности
   * (а у реализаций — фильтром {@code isExport} при поиске одноимённого метода).
   */
  private static @Nullable MethodSymbol exportMethodAt(DocumentContext documentContext, ImplementationParams params) {
    var symbol = documentContext.getSymbolTree().getSymbolAtPosition(params.getPosition());
    if (symbol instanceof MethodSymbol method && method.isExport()) {
      return method;
    }
    return null;
  }

  private static Range classRange(DocumentContext documentContext) {
    var moduleRange = documentContext.getSymbolTree().getModule().getRange();
    return moduleRange != null ? moduleRange : Ranges.create(0, 0, 0, 0);
  }

  private static LocationLink link(
    DocumentContext documentContext,
    Range targetRange,
    Range targetSelectionRange,
    Range originSelectionRange
  ) {
    return new LocationLink(
      documentContext.getUri().toString(),
      targetRange,
      targetSelectionRange,
      originSelectionRange
    );
  }

  /**
   * Связь на класс-реализатор без диапазона-источника: при переходе из тела
   * интерфейса (а не с конкретного метода) под курсором нет идентификатора,
   * поэтому {@code originSelectionRange} не задаётся.
   */
  private static LocationLink classLink(
    DocumentContext documentContext,
    Range targetRange,
    Range targetSelectionRange
  ) {
    var locationLink = new LocationLink();
    locationLink.setTargetUri(documentContext.getUri().toString());
    locationLink.setTargetRange(targetRange);
    locationLink.setTargetSelectionRange(targetSelectionRange);
    return locationLink;
  }
}
