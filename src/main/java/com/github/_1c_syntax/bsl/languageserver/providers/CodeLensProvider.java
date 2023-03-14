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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensData;
import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensSupplier;
import com.github._1c_syntax.bsl.languageserver.codelenses.databind.CodeLensDataObjectMapper;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensWorkspaceCapabilities;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Провайдер, обрабатывающий запросы {@code textDocument/codeLens}, {@code codeLens/resolve},
 * а так же отвечающий за отправку запроса {@code workspace/codeLens/refresh}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_codeLens">CodeLens Request specification</a>.
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeLens_resolve">CodeLens Resolve Request specification</a>.
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#codeLens_refresh">CodeLens Refresh Request specification</a>.
 */
@Component
@RequiredArgsConstructor
public class CodeLensProvider {
  private final Map<String, CodeLensSupplier<CodeLensData>> codeLensSuppliersById;
  private final ObjectProvider<List<CodeLensSupplier<CodeLensData>>> enabledCodeLensSuppliersProvider;
  private final LanguageClientHolder clientHolder;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;
  private final CodeLensDataObjectMapper codeLensDataObjectMapper;

  private List<CodeLensSupplier<CodeLensData>> enabledCodeLensSuppliers;

  @PostConstruct
  protected void init() {
    enabledCodeLensSuppliers = enabledCodeLensSuppliersProvider.getObject();
  }

  /**
   * Получение списка {@link CodeLens} в документе.
   *
   * @param documentContext Контекст документа.
   * @return Список линз.
   */
  public List<CodeLens> getCodeLens(DocumentContext documentContext) {
    return enabledCodeLensSuppliers.stream()
      .filter(codeLensSupplier -> codeLensSupplier.isApplicable(documentContext))
      .map(codeLensSupplier -> codeLensSupplier.getCodeLenses(documentContext))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  /**
   * Провести операцию разрешения линзы (заполнение свойства
   * {@link CodeLens#setCommand(Command)}).
   * <p>
   * При разрешении линзы свойство {@link CodeLens#setData(Object)}
   * очищается с целью уменьшения трафика между клиентом и сервером.
   *
   * @param documentContext Контекст документа.
   * @param unresolved      Неразрешенная линза.
   * @param data            Данные линзы.
   * @return Разрешенная линза.
   */
  public CodeLens resolveCodeLens(
    DocumentContext documentContext,
    CodeLens unresolved,
    CodeLensData data
  ) {
    var codeLensSupplier = codeLensSuppliersById.get(data.getId());
    var resolvedCodeLens = codeLensSupplier.resolve(documentContext, unresolved, data);
    resolvedCodeLens.setData(null);
    return resolvedCodeLens;
  }

  /**
   * Обработчик события {@link LanguageServerConfigurationChangedEvent}.
   * <p>
   * В случае поддержки запроса подключенным клиентом инициирует запрос {@code workspace/codeLens/refresh}.
   *
   * @param event Событие
   */
  @EventListener
  public void handleEvent(LanguageServerConfigurationChangedEvent event) {
    enabledCodeLensSuppliers = enabledCodeLensSuppliersProvider.getObject();

    boolean clientSupportsRefreshCodeLenses = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getWorkspace)
      .map(WorkspaceClientCapabilities::getCodeLens)
      .map(CodeLensWorkspaceCapabilities::getRefreshSupport)
      .orElse(false);

    if (!clientSupportsRefreshCodeLenses) {
      return;
    }

    clientHolder.execIfConnected(LanguageClient::refreshCodeLenses);
  }

  /**
   * Извлечь данные линзы из линзы.
   * <p>
   * Возвращает объект данных типа, с которым был зарегистрирован
   * сапплаер линзы (параметр-тип класса сапплаера).
   *
   * @param codeLens Линза, из которой необходимо извлечь данные.
   * @return Извлеченные данные линзы.
   */
  @SneakyThrows
  public CodeLensData extractData(CodeLens codeLens) {
    var rawCodeLensData = codeLens.getData();

    if (rawCodeLensData instanceof CodeLensData) {
      return (CodeLensData) rawCodeLensData;
    }

    return codeLensDataObjectMapper.readValue(rawCodeLensData.toString(), CodeLensData.class);
  }
}
