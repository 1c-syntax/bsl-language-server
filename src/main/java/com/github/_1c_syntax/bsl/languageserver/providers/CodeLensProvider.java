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
import com.github._1c_syntax.bsl.languageserver.client.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensData;
import com.github._1c_syntax.bsl.languageserver.codelenses.CodeLensSupplier;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensCapabilities;
import org.eclipse.lsp4j.CodeLensResolveSupportCapabilities;
import org.eclipse.lsp4j.CodeLensWorkspaceCapabilities;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Провайдер, обрабатывающий запросы {@code textDocument/codeLens} и {@code codeLens/resolve}.
 */
@Component
@RequiredArgsConstructor
public class CodeLensProvider {

  private final ObjectProvider<CodeLensSupplier> enabledCodeLensSuppliersProvider;
  private final Map<String, CodeLensSupplier> codeLensSuppliersById;
  private final LanguageClientHolder clientHolder;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  /**
   * Получение списка {@link CodeLens} в документе.
   * <p>
   * Если клиент заявил поддержку резолва команд линз
   * ({@code textDocument.codeLens.resolveSupport} со свойством {@code command}, LSP 3.18),
   * линзы возвращаются неразрешёнными — команда заполняется позже запросом
   * {@code codeLens/resolve}. Иначе линзы разрешаются сразу, на месте:
   * полагаться на резолв для такого клиента нельзя.
   *
   * @param documentContext Контекст документа.
   * @return Список линз.
   */
  public List<CodeLens> getCodeLens(DocumentContext documentContext) {
    var codeLenses = enabledCodeLensSuppliersProvider.getObject().stream()
      .filter(codeLensSupplier -> codeLensSupplier.isApplicable(documentContext))
      .map(codeLensSupplier -> codeLensSupplier.getCodeLenses(documentContext))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    if (!clientResolvesCommands()) {
      codeLenses.forEach(codeLens -> resolveEagerly(documentContext, codeLens));
    }

    return codeLenses;
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
   * Заявил ли подключённый клиент поддержку резолва команд линз — перечислил ли он
   * свойство {@code command} в {@code textDocument.codeLens.resolveSupport.properties}.
   *
   * @return {@code true}, если команду линзы можно отложить на {@code codeLens/resolve};
   *         {@code false}, если клиент не заявил {@code resolveSupport} (в том числе
   *         не поддерживает LSP 3.18) — тогда команду нужно отдать сразу.
   */
  private boolean clientResolvesCommands() {
    return clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getCodeLens)
      .map(CodeLensCapabilities::getResolveSupport)
      .map(CodeLensResolveSupportCapabilities::getProperties)
      .map(properties -> properties.contains("command"))
      .orElse(false);
  }

  /**
   * Разрешить линзу на месте — для клиентов без поддержки {@code codeLens/resolve}.
   * Данные линзы очищаются, как и при обычном резолве: резолвить такую линзу клиент не станет.
   *
   * @param documentContext Контекст документа.
   * @param codeLens        Неразрешённая линза с данными.
   */
  private void resolveEagerly(DocumentContext documentContext, CodeLens codeLens) {
    if (codeLens.getData() instanceof CodeLensData data) {
      resolveCodeLens(documentContext, codeLens, data);
    }
  }

  /**
   * Обработчик события {@link LanguageServerConfigurationChangedEvent}.
   * <p>
   * Если клиент поддерживает метод {@code workspace/codeLens/refresh},
   * запрашивает у клиента обновление данных линз.
   *
   * @param event Событие
   */
  @EventListener
  @SneakyThrows
  public void handleEvent(LanguageServerConfigurationChangedEvent event) {
    if (!clientHolder.isConnected()) {
      return;
    }

    var clientSupportsCodeLensRefresh = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getWorkspace)
      .map(WorkspaceClientCapabilities::getCodeLens)
      .map(CodeLensWorkspaceCapabilities::getRefreshSupport)
      .orElse(false);

    if (clientSupportsCodeLensRefresh) {
      refreshCodeLenses();
    }
  }

  /**
   * Отправить запрос клиенту на обновление линз ({@code workspace/codeLens/refresh}).
   */
  private void refreshCodeLenses() {
    clientHolder.execIfConnected((LanguageClient client) -> {
      client.refreshCodeLenses();
      return null;
    });
  }
}
