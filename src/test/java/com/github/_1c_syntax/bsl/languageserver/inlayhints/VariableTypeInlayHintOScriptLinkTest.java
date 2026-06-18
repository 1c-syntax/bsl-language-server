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
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что хинт выведенного типа OneScript-переменной кликабелен — часть
 * метки ссылается на объявляющий тип класс/модуль (пользовательский тип
 * {@code TypeKind.USER}). Клиент без resolveSupport — ссылка строится жадно.
 */
@CleanupContextBeforeClassAndAfterClass
class VariableTypeInlayHintOScriptLinkTest extends AbstractServerContextAwareTest {

  private static final Path FIXTURE_ROOT =
    Path.of("src/test/resources/oscript-libraries/autumn-di").toAbsolutePath();

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private VariableTypeInlayHintSupplier supplier;

  @BeforeEach
  void setup() {
    initServerContext(FIXTURE_ROOT, false);
    index.reindex(context);
  }

  @Test
  void testUserClassTypeHintLinksToDeclaringModule() {

    // given
    // потребитель присваивает полю внедрённый через конструктор экземпляр класса
    // «Логгер»: правая часть присваивания выводится в пользовательский тип.
    var path = FIXTURE_ROOT.resolve("src/ПотребительВКонструкторе.os").toString();
    var documentContext = TestUtils.getDocumentContextFromFile(path, context);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var method = documentContext.getSymbolTree().getMethods().getFirst();
    var params = new InlayHintParams(textDocumentIdentifier, method.getRange());

    var loggerPath = FIXTURE_ROOT.resolve("src/Логгер.os");
    var loggerUri = Absolute.uri(loggerPath.toUri());
    var loggerContext = TestUtils.getDocumentContextFromFile(loggerPath.toString(), context);
    var loggerConstructor = loggerContext.getSymbolTree().getConstructor().orElseThrow();

    // when
    var inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    // единственный хинт — тип «Логгер» со ссылкой на объявляющий класс.
    assertThat(inlayHints)
      .hasSize(1)
      .first()
      .satisfies(this::assertLinksToLogger);

    InlayHint inlayHint = inlayHints.getFirst();
    var location = inlayHint.getLabel().getRight().getFirst().getLocation();
    assertThat(location.getUri()).isEqualTo(loggerUri.toString());
    // ссылка ведёт к конструктору ПриСозданииОбъекта, а не к первому токену модуля:
    // только так hover и go-to-definition в позиции ссылки осмысленны.
    assertThat(location.getRange()).isEqualTo(loggerConstructor.getSelectionRange());
  }

  private void assertLinksToLogger(InlayHint inlayHint) {
    InlayHintLabelPart labelPart = inlayHint.getLabel().getRight().getFirst();
    assertThat(labelPart.getValue()).isEqualTo(": Логгер");
    assertThat(labelPart.getLocation()).isNotNull();
  }
}
