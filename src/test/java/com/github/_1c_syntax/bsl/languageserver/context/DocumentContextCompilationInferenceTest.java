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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentContextCompilationInferenceTest extends AbstractServerContextAwareTest {
  @Autowired
  private TypeService typeService;

  @Test
  void ownCalleeUsesItsContextBeforeRebuildPublishesDocumentState() {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    var document = context.addDocument(root.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri());
    document.rebuild("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Значение()
        #Если Сервер Тогда
          Возврат 1;
        #Иначе
          Возврат "";
        #КонецЕсли
      КонецФункции
      &НаКлиенте
      Процедура Клиент()
        Б = Значение(); // клиент
      КонецПроцедуры
      &НаСервере
      Процедура Сервер()
        Б = Значение(); // сервер
      КонецПроцедуры
      """, 0);

    assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITHOUT_CONTENT);
    assertThat(typesAt(document, "Б = Значение(); // клиент")).containsExactly("Строка");
    assertThat(typesAt(document, "Б = Значение(); // сервер")).containsExactly("Число");
    assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITHOUT_CONTENT);
  }

  private List<String> typesAt(DocumentContext document, String marker) {
    var source = document.getContent();
    var offset = source.indexOf(marker) + 4;
    var line = source.substring(0, offset).split("\n").length - 1;
    var character = offset - source.lastIndexOf('\n', offset) - 1;
    return typeService.expressionTypesAt(document, new Position(line, character)).refs().stream()
      .map(TypeRef::qualifiedName).toList();
  }

  @Test
  void growingModuleCellsInvalidateContextualFunctionResults() {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    var document = context.addDocument(root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri());
    document.rebuild("""
      Перем Первый, Второй;
      Процедура Инициализация()
        Первый = ВторойТип();
        Второй = Новый Структура("Ключ", 1);
      КонецПроцедуры
      Функция ВторойТип()
        Возврат Второй;
      КонецФункции
      Функция ПервыйТип() Экспорт
        Возврат Первый;
      КонецФункции
      &НаСервере
      Процедура Проверка()
        Б = ПервыйТип().Ключ;
      КонецПроцедуры
      """, 0);

    assertThat(typesAt(document, "Б = ПервыйТип().Ключ;")).containsExactly("Число");
  }
}
