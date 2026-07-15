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

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.AttributeNode;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.ConfigurationTree;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.ConfigurationTreeParams;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.MdClassNode;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.MetadataObjectNode;
import com.github._1c_syntax.bsl.mdclasses.Solution;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.support.AttributeKind;
import com.github._1c_syntax.bsl.types.MultiLanguageString;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Провайдер дерева конфигурации рабочей области в разрезе основной конфигурации и её расширений
 * (расширение протокола {@code workspace/x-configurationTree}).
 * <p>
 * View над {@link com.github._1c_syntax.bsl.mdclasses.Solution}: раздельно перечисляет объекты
 * метаданных верхнего уровня базовой конфигурации и каждого расширения с их именами, синонимами,
 * реквизитами и стандартными реквизитами.
 */
@Component
@RequiredArgsConstructor
public class ConfigurationTreeProvider {

  private final ServerContextProvider serverContextProvider;

  /**
   * Построить дерево конфигурации для указанной параметрами рабочей области.
   * <p>
   * Идентификатор рабочей области обязателен. Если не задан ни {@code workspaceUri}, ни
   * {@code workspaceName}, либо рабочая область не найдена, возвращает future, завершённый
   * ошибкой JSON-RPC {@link ResponseErrorCode#InvalidParams}.
   *
   * @param params параметры запроса (обязателен {@code workspaceUri} либо {@code workspaceName})
   * @return future с деревом конфигурации, либо завершённый ошибкой при невалидных параметрах
   *         или ненайденной рабочей области
   */
  public CompletableFuture<ConfigurationTree> configurationTree(ConfigurationTreeParams params) {
    var workspaceUri = params.workspaceUri();
    var workspaceName = params.workspaceName();
    var hasUri = workspaceUri != null && !workspaceUri.isBlank();
    var hasName = workspaceName != null && !workspaceName.isBlank();

    if (!hasUri && !hasName) {
      throw invalidParams("Не задан идентификатор рабочей области: требуется workspaceUri или workspaceName");
    }

    // Исключение оборачивается в JSON-RPC ответ самим LSP4J (RemoteEndpoint#exceptionToErrorObject).
    var tree = resolveContext(params)
      .map(context -> buildTree(context.getWorkspaceUri().toString(), context.getSolution()))
      .orElseThrow(() ->
        invalidParams("Рабочая область не найдена: " + (hasUri ? workspaceUri : workspaceName)));
    return CompletableFuture.completedFuture(tree);
  }

  private static ResponseErrorException invalidParams(String message) {
    return new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, message, null));
  }

  private Optional<ServerContext> resolveContext(ConfigurationTreeParams params) {
    var contexts = serverContextProvider.getAllContexts();

    var workspaceUri = params.workspaceUri();
    if (workspaceUri != null && !workspaceUri.isBlank()) {
      var normalized = Absolute.uri(workspaceUri);
      return Optional.ofNullable(contexts.get(normalized));
    }

    var workspaceName = params.workspaceName();
    if (workspaceName != null && !workspaceName.isBlank()) {
      return contexts.entrySet().stream()
        .filter(entry -> workspaceName.equals(extractWorkspaceName(entry.getKey())))
        .map(Map.Entry::getValue)
        .findFirst();
    }

    return Optional.empty();
  }

  /**
   * Построить дерево конфигурации из решения. Выделено для тестируемости в отрыве от
   * {@link ServerContext}.
   *
   * @param workspaceUri URI рабочей области (попадает в ответ)
   * @param solution     решение (базовая конфигурация и её расширения)
   * @return дерево конфигурации
   */
  static ConfigurationTree buildTree(String workspaceUri, Solution solution) {
    var base = solution.getBaseConfiguration();
    var configurationNode = toMdClassNode(
      base.getName(),
      base.getSynonym(),
      base.getChildren(),
      MdClassNode.KIND_CONFIGURATION,
      null,
      null
    );

    var extensions = solution.getExtensions().stream()
      .map(extension -> toMdClassNode(
        extension.getName(),
        extension.getSynonym(),
        extension.getChildren(),
        MdClassNode.KIND_EXTENSION,
        extension.getConfigurationExtensionPurpose().name(),
        extension.getNamePrefix()
      ))
      .toList();

    return new ConfigurationTree(workspaceUri, configurationNode, extensions);
  }

  private static MdClassNode toMdClassNode(
    String name,
    MultiLanguageString synonym,
    List<MD> children,
    String kind,
    @Nullable String purpose,
    @Nullable String namePrefix
  ) {
    var objects = children.stream()
      .map(ConfigurationTreeProvider::toObjectNode)
      .toList();
    return new MdClassNode(name, synonymText(synonym), kind, purpose, namePrefix, objects);
  }

  private static MetadataObjectNode toObjectNode(MD md) {
    var attributes = new ArrayList<AttributeNode>();
    var standardAttributes = new ArrayList<AttributeNode>();

    if (md instanceof AttributeOwner attributeOwner) {
      for (var attribute : attributeOwner.getAllAttributes()) {
        var node = new AttributeNode(attribute.getName(), synonymText(attribute.getSynonym()));
        if (attribute.getKind() == AttributeKind.STANDARD) {
          standardAttributes.add(node);
        } else {
          attributes.add(node);
        }
      }
    }

    return new MetadataObjectNode(
      md.getName(),
      synonymText(md.getSynonym()),
      md.getMdoType().name(),
      md.getMdoReference().getMdoRef(),
      List.copyOf(attributes),
      List.copyOf(standardAttributes)
    );
  }

  private static String synonymText(MultiLanguageString synonym) {
    return synonym.isEmpty() ? "" : synonym.getAny();
  }

  private static String extractWorkspaceName(URI workspaceUri) {
    var path = workspaceUri.getPath();
    if (path == null) {
      return workspaceUri.toString();
    }
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    var lastSlash = path.lastIndexOf('/');
    return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
  }
}
