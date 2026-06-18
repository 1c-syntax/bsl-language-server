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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Автодополнение членов общего модуля по цепочке {@code ОбщегоНазначения.<префикс>}:
 * после точки на имени общего модуля должны подсказываться его экспортные методы.
 * Общий модуль — глобальное свойство; его тип-значение несёт экспортные методы
 * из символьного дерева модуля.
 */
@CleanupContextBeforeClassAndAfterClass
class CompletionCommonModuleMembersTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void completionAfterCommonModuleDotOffersExportedMethods() {
    // given: загруженная конфигурация с общим модулем ОбщегоНазначения
    // (экспортная функция ЗначениеВМассиве)
    initServerContext(Absolute.path(PATH_TO_METADATA));

    var content = "Процедура Демо()\n\tОбщегоНазначения.Знач\nКонецПроцедуры\n";
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_DOCUMENT_URI, content, context);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    // Курсор сразу после «ОбщегоНазначения.Знач» на второй строке (tab + 16 + '.' + 4 = 22).
    params.setPosition(new Position(1, "\tОбщегоНазначения.Знач".length()));

    // when
    var completion = completionProvider.getCompletion(dc, params);

    // then: экспортный метод модуля с префиксом «Знач» подсказан
    assertThat(completion.getItems())
      .extracting(CompletionItem::getLabel)
      .as("после ОбщегоНазначения.Знач должен подсказываться экспортный метод модуля")
      .contains("ЗначениеВМассиве");
  }
}
