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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.providers.InlayHintProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintCapabilities;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.InlayHintResolveSupportCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что хинт выведенного типа становится кликабельным — часть метки
 * ссылается на объявление типа в исходниках (модуль менеджера объекта
 * конфигурации), а у платформенных типов ссылки нет. Покрывает жадный и ленивый
 * (через {@code inlayHint/resolve}) пути построения ссылки.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
class VariableTypeInlayHintLinkTest extends AbstractServerContextAwareTest {

  private static final String MANAGER_MODULE_PATH =
    "./src/test/resources/metadata/designer/Catalogs/СправочникСМенеджером/Ext/ManagerModule.bsl";
  private static final String CALLER_PATH =
    "./src/test/resources/inlayhints/VariableTypeInlayHintModuleLink.bsl";

  @Autowired
  private VariableTypeInlayHintSupplier supplier;

  @Autowired
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  @Autowired
  private InlayHintProvider provider;

  @AfterEach
  void resetClientCapabilities() {
    clientCapabilitiesHolder.setCapabilities(null);
    supplier.handleInitializeEvent();
  }

  private void enableLabelLocationResolveSupport() {
    var inlayHintCapabilities = new InlayHintCapabilities();
    inlayHintCapabilities.setResolveSupport(
      new InlayHintResolveSupportCapabilities(List.of("label.location"))
    );
    var textDocumentCapabilities = new TextDocumentClientCapabilities();
    textDocumentCapabilities.setInlayHint(inlayHintCapabilities);
    var capabilities = new ClientCapabilities();
    capabilities.setTextDocument(textDocumentCapabilities);
    clientCapabilitiesHolder.setCapabilities(capabilities);
    supplier.handleInitializeEvent();
  }

  private InlayHint singleHintForManagerVariable() {
    // прогреваем ManagerModule, чтобы провайдер зарегистрировал тип менеджера и
    // обратный индекс тип→URI (DocumentContextContentChangedEvent).
    TestUtils.getDocumentContextFromFile(MANAGER_MODULE_PATH);

    var documentContext = TestUtils.getDocumentContextFromFile(CALLER_PATH);
    var method = documentContext.getSymbolTree().getMethods().getFirst();
    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var params = new InlayHintParams(textDocumentIdentifier, method.getRange());

    var inlayHints = supplier.getInlayHints(documentContext, params);
    assertThat(inlayHints).hasSize(1);
    return inlayHints.getFirst();
  }

  @Test
  void testManagerTypeHintLinksToManagerModuleEagerly() {

    // given
    // клиент не объявил resolveSupport для label.location — ссылка жадная.
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();

    // when
    var inlayHint = singleHintForManagerVariable();

    // then
    // часть метки ссылается на модуль менеджера справочника, data — без координат.
    InlayHintLabelPart labelPart = inlayHint.getLabel().getRight().getFirst();
    assertThat(labelPart.getValue()).isEqualTo(": СправочникМенеджер.СправочникСМенеджером");
    assertThat(labelPart.getLocation()).isNotNull();
    assertThat(labelPart.getLocation().getUri()).contains("ManagerModule.bsl");
  }

  @Test
  void testManagerTypeHintLinkIsResolvedLazilyWhenClientSupportsResolve() {

    // given
    // клиент объявил resolveSupport для label.location — ссылка ленивая.
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    enableLabelLocationResolveSupport();

    // when
    var unresolved = singleHintForManagerVariable();

    // then
    // жадно ссылки нет, но data несёт координаты объявления типа.
    assertThat(unresolved.getLabel().getRight().getFirst().getLocation()).isNull();
    assertThat(unresolved.getData()).isNotNull();

    // when
    // резолвим через провайдер (round-trip данных хинта).
    var documentContext = TestUtils.getDocumentContextFromFile(CALLER_PATH);
    var data = provider.extractData(unresolved);
    var resolved = provider.resolveInlayHint(documentContext, unresolved, data);

    // then
    // ссылка части метки заполнена объявлением типа, data очищена.
    InlayHintLabelPart resolvedPart = resolved.getLabel().getRight().getFirst();
    assertThat(resolvedPart.getLocation()).isNotNull();
    assertThat(resolvedPart.getLocation().getUri()).contains("ManagerModule.bsl");
    assertThat(resolved.getData()).isNull();
  }
}
