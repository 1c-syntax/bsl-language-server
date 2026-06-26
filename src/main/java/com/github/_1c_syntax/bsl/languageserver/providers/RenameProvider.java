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
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.rename.NewNameValidator;
import com.github._1c_syntax.bsl.languageserver.rename.RenameWorkspaceEditBuilder;
import com.github._1c_syntax.bsl.languageserver.rename.SymbolDefinitionReferenceFactory;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
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
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
  private final NewNameValidator newNameValidator;
  private final SymbolDefinitionReferenceFactory symbolDefinitionReferenceFactory;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  // Кэшируется на initialize. Признак поддержки клиентом
  // textDocument.rename.prepareSupportDefaultBehavior: клиент способен сам вычислить диапазон
  // переименования (идентификатор под курсором) и использовать его как placeholder, если сервер
  // на prepareRename вернёт PrepareRenameDefaultBehavior вместо явного PrepareRenameResult.
  // Для таких клиентов сервер отдаёт компактный ответ-«поведение по умолчанию», иначе —
  // явный PrepareRenameResult с диапазоном и placeholder (для старых клиентов).
  private boolean prepareSupportDefaultBehavior;

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Кэширует клиентскую возможность {@code textDocument.rename.prepareSupportDefaultBehavior},
   * сообщающую, что клиент умеет вычислять поведение переименования по умолчанию. Чтение
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
        .stream().map(symbolDefinitionReferenceFactory::referenceOf)
    ).collect(Collectors.groupingBy(ref -> ref.uri().toString(), getTexEdits(params)));

    var oldName = sourceDefinedSymbol.map(SourceDefinedSymbol::getName).orElse(params.getNewName());
    return workspaceEditBuilder.build(
      changes,
      oldName,
      params.getNewName()
    );
  }

  /**
   * Подготовить переименование символа под курсором для ответа на {@code textDocument/prepareRename}.
   * <p>
   * Резолвит ссылку под курсором и, если соответствующий символ можно переименовать текстовой
   * правкой (см. {@link #isRenameable(Reference)}), формирует ответ в зависимости от заявленных
   * клиентом возможностей:
   * <ul>
   *   <li>если клиент заявил {@code textDocument.rename.prepareSupportDefaultBehavior}
   *   (см. {@link #handleInitializeEvent()}), возвращается {@link PrepareRenameDefaultBehavior}
   *   с {@code defaultBehavior == true} — серверу не нужно вычислять диапазон, клиент сам выделит
   *   идентификатор под курсором и использует его как {@code placeholder}; для идентификатора BSL
   *   или OneScript под курсором это совпадает с именем символа, поэтому UX идентичен, а ответ
   *   сервера компактен;</li>
   *   <li>иначе возвращается явный {@link PrepareRenameResult} с диапазоном выделения ссылки
   *   ({@code range}) и текущим именем символа ({@code placeholder}) — для клиентов, не умеющих
   *   откатываться к поведению по умолчанию.</li>
   * </ul>
   * Если символ не переименовываем или ссылка не резолвится, возвращается {@link Either3} без
   * значения (отказ) в обоих режимах, и клиент отклоняет подготовку переименования. Проверка
   * переименовываемости выполняется на сервере независимо от возможностей клиента.
   * <p>
   * Формирование итоговой формы ответа (в том числе случай отказа) выполняется здесь, чтобы
   * сервисный слой оставался тонким делегатом.
   *
   * @param documentContext Контекст документа.
   * @param params          Параметры вызова.
   * @return {@link Either3} с {@link PrepareRenameDefaultBehavior} (если клиент умеет поведение по
   *         умолчанию) либо {@link PrepareRenameResult} (диапазон и имя символа) при возможности
   *         переименования, либо {@link Either3} без значения, если символ переименовать нельзя.
   */
  public Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> getPrepareRename(
    DocumentContext documentContext,
    TextDocumentPositionParams params
  ) {
    return referenceResolver.findReference(documentContext.getUri(), params.getPosition())
      .filter(Reference::isSourceDefinedSymbolReference)
      .filter(RenameProvider::isRenameable)
      .map(this::toPrepareRenameResponse)
      .orElseGet(RenameProvider::rejectPrepareRename);
  }

  private Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> toPrepareRenameResponse(
    Reference reference
  ) {
    if (prepareSupportDefaultBehavior) {
      return Either3.forThird(new PrepareRenameDefaultBehavior(true));
    }
    return reference.getSourceDefinedSymbol()
      .<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>>map(symbol ->
        Either3.forSecond(new PrepareRenameResult(reference.selectionRange(), symbol.getName())))
      .orElseGet(RenameProvider::rejectPrepareRename);
  }

  private static Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> rejectPrepareRename() {
    // Отказ от переименования: ответ без значимого диапазона. Сохраняем прежнее поведение,
    // при котором сервис отдавал Either3.forFirst поверх отсутствующего диапазона.
    @Nullable
    Range noRange = null;
    return Either3.forFirst(noRange);
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
    if (!newNameValidator.isValidIdentifier(newName)) {
      var message = resources.getResourceString(getClass(), "invalidNewName", newName);
      throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, message, null));
    }
  }

}
