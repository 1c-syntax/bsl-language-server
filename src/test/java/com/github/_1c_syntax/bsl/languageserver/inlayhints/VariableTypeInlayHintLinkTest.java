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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintParams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что хинт выведенного типа становится кликабельным — часть метки
 * ссылается на объявление типа в исходниках (модуль менеджера объекта
 * конфигурации). Объявление типа известно на этапе построения хинта, поэтому
 * ссылка проставляется жадно.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
class VariableTypeInlayHintLinkTest extends AbstractServerContextAwareTest {

  private static final String MANAGER_MODULE_PATH =
    "./src/test/resources/metadata/designer/Catalogs/СправочникСМенеджером/Ext/ManagerModule.bsl";
  private static final String CALLER_PATH =
    "./src/test/resources/inlayhints/VariableTypeInlayHintModuleLink.bsl";

  @Autowired
  private VariableTypeInlayHintSupplier supplier;

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
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();

    // when
    var inlayHint = singleHintForManagerVariable();

    // then
    // часть метки жадно ссылается на модуль менеджера справочника.
    InlayHintLabelPart labelPart = inlayHint.getLabel().getRight().getFirst();
    assertThat(labelPart.getValue()).isEqualTo(": СправочникМенеджер.СправочникСМенеджером");
    assertThat(labelPart.getLocation()).isNotNull();
    assertThat(labelPart.getLocation().getUri()).contains("ManagerModule.bsl");
  }
}
