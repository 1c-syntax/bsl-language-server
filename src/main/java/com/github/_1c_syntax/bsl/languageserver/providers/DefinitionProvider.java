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

import com.github._1c_syntax.bsl.languageserver.lsp.client.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Провайдер для перехода к определению символа.
 * <p>
 * Обрабатывает запросы {@code textDocument/definition}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_definition">Go to Definition specification</a>
 */
@Component
@RequiredArgsConstructor
public class DefinitionProvider {

  private final ReferenceResolver referenceResolver;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  // Кэшируется на initialize. linkSupport — gate для ответа LocationLink[] на запрос
  // textDocument/definition. Если клиент не заявил поддержку, ответ понижается до Location[].
  private boolean linkSupport;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Кэширует клиентскую возможность {@code textDocument.definition.linkSupport},
   * влияющую на формат ответа навигации: при её отсутствии ответ {@link LocationLink}
   * понижается до {@link Location}.
   */
  @EventListener(LanguageServerInitializeRequestReceivedEvent.class)
  public void handleInitializeEvent() {
    linkSupport = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getDefinition)
      .map(DefinitionCapabilities::getLinkSupport)
      .orElse(Boolean.FALSE);
  }

  /**
   * Получить местоположение определения символа в формате, согласованном с клиентскими
   * возможностями.
   * <p>
   * По спецификации LSP 3.14+ ответ типа {@link LocationLink} допустим, только если клиент
   * заявил {@code textDocument.definition.linkSupport}. При наличии поддержки возвращается
   * правая сторона ({@link LocationLink}); иначе результат понижается до левой стороны
   * ({@link Location}) с {@code targetUri} и {@code targetSelectionRange} каждой связи.
   *
   * @param documentContext Контекст документа
   * @param params          Параметры запроса
   * @return {@link Either} со списком {@link LocationLink} при поддержке связей либо
   *         со списком {@link Location} при её отсутствии
   */
  public Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(
    DocumentContext documentContext,
    DefinitionParams params
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

  private List<LocationLink> findLocationLinks(DocumentContext documentContext, DefinitionParams params) {
    Position position = params.getPosition();

    return referenceResolver.findReference(documentContext.getUri(), position)
      .map(DefinitionProvider::unwrapPlatformMemberSource)
      .filter(Reference::isSourceDefinedSymbolReference)
      .map(DefinitionProvider::toLocationLink)
      .map(Collections::singletonList)
      .orElse(Collections.emptyList());
  }

  /**
   * Если ссылка указывает на член платформенного/конфигурационного типа
   * (синтетический {@link PlatformMemberSymbol}, разрешённый через
   * {@code TypeService.memberAt}) и у его дескриптора есть source-defined
   * символ-источник (метод OneScript-класса, экспортная переменная-свойство
   * и т.п. — см. {@code OScriptModuleMembersProvider}), возвращает ссылку,
   * перенаправленную на этот источник; иначе возвращает ссылку без изменений.
   * <p>
   * Так основной пайплайн перехода не меняется: платформенные члены без
   * источника остаются {@link PlatformMemberSymbol} и отсекаются фильтром
   * {@link Reference#isSourceDefinedSymbolReference()} (остаётся только hover).
   * <p>
   * Обсуждение и дизайн: https://github.com/1c-syntax/bsl-language-server/pull/4197
   *
   * @param reference исходная ссылка под курсором.
   * @return ссылка на source-defined источник либо исходная ссылка.
   */
  private static Reference unwrapPlatformMemberSource(Reference reference) {
    if (!(reference.symbol() instanceof PlatformMemberSymbol platformMember)) {
      return reference;
    }
    return platformMember.getDescriptor().getSourceSymbol()
      .filter(SourceDefinedSymbol.class::isInstance)
      .map(SourceDefinedSymbol.class::cast)
      .map(sourceSymbol -> new Reference(
        reference.from(),
        sourceSymbol,
        reference.uri(),
        reference.selectionRange(),
        reference.occurrenceType()
      ))
      .orElse(reference);
  }

  private static LocationLink toLocationLink(Reference reference) {
    SourceDefinedSymbol symbol = (SourceDefinedSymbol) reference.symbol();

    return new LocationLink(
      symbol.getOwner().getUri().toString(),
      symbol.getRange(),
      symbol.getSelectionRange(),
      reference.selectionRange()
    );
  }
}
