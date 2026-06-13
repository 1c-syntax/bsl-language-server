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
import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.inlayhints.InlayHintSupplier;
import com.github._1c_syntax.bsl.languageserver.inlayhints.infrastructure.InlayHintsConfiguration;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.InlayHintWorkspaceCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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

  private final Collection<InlayHintSupplier> allInlayHintSuppliers;
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

    return allInlayHintSuppliers.stream()
      .filter(supplier -> InlayHintsConfiguration.supplierIsEnabled(supplier.getId(), parameters))
      .map(supplier -> supplier.getInlayHints(documentContext, params))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  /**
   * Разрешить хинт ({@code inlayHint/resolve}) — дорассчитать его «тяжёлые»
   * поля (tooltip и т.п.).
   * <p>
   * По идентификатору сапплаера из {@link InlayHint#getData()} находит
   * сапплаер-владельца и делегирует ему {@link InlayHintSupplier#resolve}.
   * Хинт без данных или от неизвестного сапплаера возвращается без изменений.
   * После разрешения поле {@link InlayHint#getData()} очищается для экономии
   * трафика.
   *
   * @param documentContext Контекст документа, к которому относится хинт.
   * @param unresolved      Неразрешённый хинт.
   * @return Разрешённый хинт.
   */
  public InlayHint resolveInlayHint(DocumentContext documentContext, InlayHint unresolved) {
    var supplierId = extractSupplierId(unresolved);
    if (supplierId == null) {
      return unresolved;
    }

    var resolved = allInlayHintSuppliers.stream()
      .collect(Collectors.toMap(InlayHintSupplier::getId, Function.identity()))
      .getOrDefault(supplierId, null);

    if (resolved == null) {
      return unresolved;
    }

    var result = resolved.resolve(documentContext, unresolved);
    result.setData(null);
    return result;
  }

  /**
   * Извлечь URI документа из данных неразрешённого хинта — для поиска контекста
   * документа перед резолвом.
   *
   * @param inlayHint Неразрешённый хинт.
   * @return URI документа из {@link InlayHint#getData()}; {@code empty}, если данных нет.
   */
  public Optional<URI> extractUri(InlayHint inlayHint) {
    var uri = dataField(inlayHint, "uri");
    return uri == null ? Optional.empty() : Optional.of(Absolute.uri(uri));
  }

  private String extractSupplierId(InlayHint inlayHint) {
    return dataField(inlayHint, "supplierId");
  }

  private String dataField(InlayHint inlayHint, String field) {
    var rawData = inlayHint.getData();
    if (rawData == null) {
      return null;
    }
    // Клиент присылает data назад как JSON-объект (round-trip); in-process
    // (тесты, single-jvm клиент) — как исходный объект сапплаера. Конвертируем
    // через JsonMapper в карту единообразно для обоих случаев.
    Map<String, Object> dataMap = jsonMapper.convertValue(rawData, Map.class);
    var value = dataMap.get(field);
    return value == null ? null : value.toString();
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
