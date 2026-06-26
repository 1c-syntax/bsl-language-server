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

import com.github._1c_syntax.bsl.languageserver.lsp.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.lsp.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.inlayhints.InlayHintData;
import com.github._1c_syntax.bsl.languageserver.inlayhints.InlayHintSupplier;
import com.github._1c_syntax.bsl.languageserver.inlayhints.infrastructure.InlayHintsConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.InlayHintWorkspaceCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Провайдер, обрабатывающий запросы {@code textDocument/inlayHint} и {@code inlayHint/resolve}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint">Inlay hint request</a>.
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#inlayHint_resolve">Inlay hint resolve request</a>
 */
@Component
@RequiredArgsConstructor
public class InlayHintProvider {

  private final Map<String, InlayHintSupplier<InlayHintData>> inlayHintSuppliersById;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;
  private final LanguageClientHolder clientHolder;
  private final LanguageServerConfiguration configuration;
  private final JsonMapper jsonMapper;

  /**
   * Получить список inlay hints в документе.
   *
   * @param documentContext Документ, для которого запрашиваются inlay hints.
   * @param params          Параметры запроса.
   * @return Список inlay hints в документе
   */
  public List<InlayHint> getInlayHint(DocumentContext documentContext, InlayHintParams params) {
    var parameters = configuration.getInlayHintOptions().getParameters();

    return inlayHintSuppliersById.values().stream()
      .filter(supplier -> InlayHintsConfiguration.supplierIsEnabled(supplier.getId(), parameters))
      .map(supplier -> supplier.getInlayHints(documentContext, params))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  /**
   * Разрешить хинт ({@code inlayHint/resolve}) — дорассчитать его «тяжёлые»
   * поля (tooltip и т.п.).
   * <p>
   * По идентификатору сапплаера из данных хинта находит сапплаер-владельца и
   * делегирует ему {@link InlayHintSupplier#resolve}. После разрешения поле
   * {@link InlayHint#getData()} очищается для экономии трафика.
   *
   * @param documentContext Контекст документа, к которому относится хинт.
   * @param unresolved      Неразрешённый хинт.
   * @param data            Данные хинта.
   * @return Разрешённый хинт.
   */
  public InlayHint resolveInlayHint(DocumentContext documentContext, InlayHint unresolved, InlayHintData data) {
    var supplier = inlayHintSuppliersById.get(data.getId());
    if (supplier == null) {
      return unresolved;
    }
    var resolved = supplier.resolve(documentContext, unresolved, data);
    resolved.setData(null);
    return resolved;
  }

  /**
   * Извлечь данные хинта из хинта.
   * <p>
   * Возвращает объект данных типа, с которым был зарегистрирован
   * сапплаер хинта (параметр-тип класса сапплаера).
   *
   * @param inlayHint Хинт, из которого необходимо извлечь данные.
   * @return Извлечённые данные хинта либо {@code null}, если хинт пришёл без поля
   *         {@link InlayHint#getData()} — резолвить такой хинт нечем.
   */
  @SneakyThrows
  public @Nullable InlayHintData extractData(InlayHint inlayHint) {
    var rawData = inlayHint.getData();

    if (rawData == null) {
      return null;
    }

    if (rawData instanceof InlayHintData data) {
      return data;
    }

    return jsonMapper.readValue(rawData.toString(), InlayHintData.class);
  }

  /**
   * Обработчик события {@link LanguageServerConfigurationChangedEvent}.
   * <p>
   * В случае поддержки запроса подключенным клиентом инициирует запрос {@code workspace/inlayHint/refresh}.
   *
   * @param event Событие
   */
  @EventListener
  public void handleEvent(LanguageServerConfigurationChangedEvent event) {
    // Per-workspace конфигурация — просто инициируем refresh для клиента
    refreshInlayHints();
  }

  /**
   * Отправить запрос на обновление inlay hints.
   */
  public void refreshInlayHints() {
    boolean refreshSupport = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getWorkspace)
      .map(WorkspaceClientCapabilities::getInlayHint)
      .map(InlayHintWorkspaceCapabilities::getRefreshSupport)
      .orElse(Boolean.FALSE);

    if (refreshSupport) {
      clientHolder.execIfConnected(LanguageClient::refreshInlayHints);
    }
  }
}
