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

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.rename.RenameWorkspaceEditBuilder;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Провайдер, обрабатывающий запросы {@code textDocument/rename}
 * и {@code textDocument/prepareRename}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rename">Rename Request specification</a>.
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_prepareRename">Prepare Document Request specification</a>.
 */
@Component
@RequiredArgsConstructor
public final class RenameProvider {

  private final ReferenceResolver referenceResolver;
  private final ReferenceIndex referenceIndex;
  private final Resources resources;
  private final RenameWorkspaceEditBuilder workspaceEditBuilder;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  // Кэшируется на initialize. Признак поддержки клиентом
  // textDocument.rename.prepareSupportDefaultBehavior: клиент способен откатиться к поведению
  // по умолчанию (выделение идентификатора под курсором), если сервер на prepareRename вернёт
  // PrepareRenameDefaultBehavior вместо явного диапазона. Сервер всегда возвращает явный
  // PrepareRenameResult с placeholder, поэтому флаг кэшируется для совместимости и не меняет
  // ответ — клиенты с этой возможностью получают тот же корректный результат.
  private boolean prepareSupportDefaultBehavior;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Кэширует клиентскую возможность {@code textDocument.rename.prepareSupportDefaultBehavior},
   * сообщающую, что клиент умеет откатываться к поведению переименования по умолчанию. Чтение
   * выполняется один раз на инициализацию, чтобы не обращаться к возможностям клиента на каждый
   * запрос {@code textDocument/prepareRename}.
   */
  @EventListener(LanguageServerInitializeRequestReceivedEvent.class)
  public void handleInitializeEvent() {
    prepareSupportDefaultBehavior = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getRename)
      .map(RenameCapabilities::getPrepareSupportDefaultBehavior)
      .isPresent();
  }

  /**
   * Сообщает, заявил ли подключённый клиент возможность
   * {@code textDocument.rename.prepareSupportDefaultBehavior}, то есть умеет ли он откатываться
   * к поведению переименования по умолчанию.
   * <p>
   * Значение кэшируется на этапе инициализации сервера (см. {@link #handleInitializeEvent()}).
   * Сервер всегда возвращает явный {@link PrepareRenameResult} с диапазоном и {@code placeholder},
   * поэтому данный признак не меняет формат ответа и служит для совместимости и диагностики.
   *
   * @return {@code true}, если клиент заявил поддержку отката к поведению по умолчанию.
   */
  public boolean isPrepareSupportDefaultBehavior() {
    return prepareSupportDefaultBehavior;
  }

  /**
   * Построить {@link WorkspaceEdit} с правками переименования символа.
   * <p>
   * Резолвит переименовываемый символ, собирает текстовые правки по всем его вхождениям и
   * делегирует выбор формата результата в {@link RenameWorkspaceEditBuilder}: при поддержке
   * клиентом {@code workspace.workspaceEdit.documentChanges} (и опционально
   * {@code workspace.workspaceEdit.changeAnnotationSupport}) возвращаются {@code documentChanges}
   * с аннотациями правок, иначе результат понижается до legacy {@code changes}-map.
   *
   * @param documentContext Контекст документа.
   * @param params          Параметры вызова.
   * @return Изменения документов
   */
  public WorkspaceEdit getRename(DocumentContext documentContext, RenameParams params) {

    checkNewName(params.getNewName());

    var position = params.getPosition();
    var sourceDefinedSymbol = referenceResolver.findReference(documentContext.getUri(), position)
      .filter(RenameProvider::isRenameable)
      .flatMap(Reference::getSourceDefinedSymbol);

    Map<String, List<TextEdit>> changes = Stream.concat(
      sourceDefinedSymbol
        .stream()
        .map(referenceIndex::getReferencesTo)
        .flatMap(Collection::stream),
      sourceDefinedSymbol
        .stream().map(RenameProvider::referenceOf)
    ).collect(Collectors.groupingBy(ref -> ref.uri().toString(), getTexEdits(params)));

    var oldName = sourceDefinedSymbol.map(SourceDefinedSymbol::getName).orElse(params.getNewName());
    return workspaceEditBuilder.build(
      changes,
      oldName,
      params.getNewName()
    );
  }

  private static Reference referenceOf(SourceDefinedSymbol symbol) {
    return Reference.of(
      symbol,
      symbol,
      new Location(symbol.getOwner().getUri().toString(), symbol.getSelectionRange()),
      OccurrenceType.DEFINITION
    );
  }

  /**
   * Подготовить переименование символа под курсором.
   * <p>
   * Резолвит ссылку под курсором и, если соответствующий символ можно переименовать текстовой
   * правкой (см. {@link #isRenameable(Reference)}), возвращает {@link PrepareRenameResult} с
   * диапазоном выделения ссылки ({@code range}) и текущим именем символа ({@code placeholder}).
   * Имя в {@code placeholder} позволяет клиенту предзаполнить поле ввода реальным идентификатором,
   * а не произвольным текстом под курсором. Если символ не переименовываем или ссылка не
   * резолвится, возвращается {@code null}, и клиент отклоняет подготовку переименования.
   *
   * @param documentContext Контекст документа.
   * @param params          Параметры вызова.
   * @return {@link PrepareRenameResult} с диапазоном и именем символа при возможности
   *         переименования либо {@code null}, если символ переименовать нельзя.
   */
  public @Nullable PrepareRenameResult getPrepareRename(
    DocumentContext documentContext,
    TextDocumentPositionParams params
  ) {
    return referenceResolver.findReference(documentContext.getUri(), params.getPosition())
      .filter(Reference::isSourceDefinedSymbolReference)
      .filter(RenameProvider::isRenameable)
      .flatMap(RenameProvider::toPrepareRenameResult)
      .orElse(null);
  }

  private static Optional<PrepareRenameResult> toPrepareRenameResult(Reference reference) {
    return reference.getSourceDefinedSymbol()
      .map(symbol -> new PrepareRenameResult(reference.selectionRange(), symbol.getName()));
  }

  /**
   * Проверяет, поддерживается ли переименование символа, на который указывает ссылка.
   * <p>
   * Имя модуля задаётся метаданными и не может быть переименовано текстовой правкой,
   * поэтому ссылки на символы с {@link SymbolKind#Module} не переименовываются.
   *
   * @param reference Ссылка на символ.
   * @return {@code true}, если символ можно переименовать через текстовую правку.
   */
  private static boolean isRenameable(Reference reference) {
    return reference.symbol().getSymbolKind() != SymbolKind.Module;
  }

  private static Collector<Reference, ?, List<TextEdit>> getTexEdits(RenameParams params) {
    return Collectors.mapping(
      Reference::selectionRange,
      Collectors.mapping(range -> newTextEdit(params, range), Collectors.toList())
    );
  }

  private static TextEdit newTextEdit(RenameParams params, Range range) {
    return new TextEdit(range, params.getNewName());
  }

  private void checkNewName(@Nullable String newName) {
    if (!isValidIdentifier(newName)) {
      var message = resources.getResourceString(getClass(), "invalidNewName", newName);
      throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, message, null));
    }
  }

  private static boolean isValidIdentifier(@Nullable String newName) {
    if (newName == null || newName.isEmpty()) {
      return false;
    }

    var tokens = new BSLTokenizer(newName).getTokens();

    return tokens.size() == 2
      && tokens.get(0).getType() == BSLLexer.IDENTIFIER
      && newName.equals(tokens.get(0).getText());
  }

}
