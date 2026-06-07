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
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Типизация поля-держателя родителя библиотеки {@code extends}: обращение к
 * родителю должно подсказывать члены супер-класса. Покрывает явный держатель
 * (поле с {@code &Родитель}) и неявное поле {@code _ОбъектРодитель}.
 */
@CleanupContextBeforeClassAndAfterClass
class CompletionParentFieldTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Autowired
  private OScriptLibraryIndex index;

  @Test
  void explicitParentFieldExposesSuperclassMembers() {
    initLib();

    // Объявления Перем — до методов (требование языка).
    var content = "&Родитель\n"
      + "Перем СсылкаНаРодителя Экспорт;\n"
      + "\n"
      + "&Расширяет(\"БазовыйКласс\")\n"
      + "Процедура ПриСозданииОбъекта()\n"
      + "КонецПроцедуры\n"
      + "\n"
      + "Функция Тест() Экспорт\n"
      + "\tСсылкаНаРодителя.\n"
      + "КонецФункции\n";

    assertThat(labelsAfterDot(content, "СсылкаНаРодителя."))
      .contains("БазовыйМетод", "БазовоеСвойство");
  }

  @Test
  void parentFieldHasNoSuperclassMembersWithoutExtends() {
    initLib();

    // Класс НЕ объявляет &Расширяет — поле &Родитель не должно получать тип
    // супер-класса (нет родителя).
    var content = "&Родитель\n"
      + "Перем СсылкаНаРодителя Экспорт;\n"
      + "\n"
      + "Функция Тест() Экспорт\n"
      + "\tСсылкаНаРодителя.\n"
      + "КонецФункции\n";

    assertThat(labelsAfterDot(content, "СсылкаНаРодителя."))
      .doesNotContain("БазовыйМетод", "БазовоеСвойство");
  }

  @Test
  void implicitParentFieldExposesSuperclassMembers() {
    initLib();

    var content = "&Расширяет(\"БазовыйКласс\")\n"
      + "Процедура ПриСозданииОбъекта()\n"
      + "КонецПроцедуры\n"
      + "\n"
      + "Функция Тест() Экспорт\n"
      + "\t_ОбъектРодитель.\n"
      + "КонецФункции\n";

    assertThat(labelsAfterDot(content, "_ОбъектРодитель."))
      .contains("БазовыйМетод", "БазовоеСвойство");
  }

  @Test
  void plainModuleFieldIsNotTypedAsParent() {
    initLib();

    // Обычное поле (не &Родитель и не _ОбъектРодитель) не получает тип родителя.
    var content = "Перем Обычное Экспорт;\n"
      + "\n"
      + "&Расширяет(\"БазовыйКласс\")\n"
      + "Процедура ПриСозданииОбъекта()\n"
      + "КонецПроцедуры\n"
      + "\n"
      + "Функция Тест() Экспорт\n"
      + "\tОбычное.\n"
      + "КонецФункции\n";

    assertThat(labelsAfterDot(content, "Обычное."))
      .doesNotContain("БазовыйМетод", "БазовоеСвойство");
  }

  private java.util.List<String> labelsAfterDot(String content, String receiver) {
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);

    var lines = content.split("\n", -1);
    int line = -1;
    int character = -1;
    for (int i = 0; i < lines.length; i++) {
      var idx = lines[i].indexOf(receiver);
      if (idx >= 0) {
        line = i;
        character = idx + receiver.length();
        break;
      }
    }
    assertThat(line).as("receiver %s must be present", receiver).isGreaterThanOrEqualTo(0);

    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(line, character));

    CompletionList completion = completionProvider.getCompletion(dc, params);
    return completion.getItems().stream().map(CompletionItem::getLabel).toList();
  }

  private void initLib() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/extends-lib").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);
  }
}
