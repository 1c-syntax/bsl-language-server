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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.events.ConfigurationTypesRegisteredEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClearedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClosedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

class InferredExpressionTypeIndexTest extends AbstractServerContextAwareTest {

  @Autowired
  private InferredExpressionTypeIndex index;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Test
  void cachesByNodeAndInvalidatesByUriEvents() {
    // given — документ и произвольный AST-узел в качестве ключа.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          ТЗ = Новый ТаблицаЗначений;
          А = ТЗ.Колонки;
      КонецПроцедуры
      """);
    var serverContext = documentContext.getServerContext();
    var uri = documentContext.getUri();
    var node = documentContext.getAst();
    var types = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "ТаблицаЗначений"));

    assertThat(index.get(uri, node)).as("до записи кэш пуст").isNull();

    index.put(uri, node, types);
    assertThat(index.get(uri, node)).as("после записи тип возвращается").isEqualTo(types);

    // изменение содержимого сбрасывает кэш по URI.
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    assertThat(index.get(uri, node)).as("сброс на изменение содержимого").isNull();

    // освобождение вторичных данных сбрасывает кэш по URI.
    index.put(uri, node, types);
    eventPublisher.publishEvent(new ServerContextDocumentClearedEvent(serverContext, documentContext));
    assertThat(index.get(uri, node)).as("сброс на освобождение вторичных данных").isNull();

    // закрытие документа сбрасывает кэш по URI.
    index.put(uri, node, types);
    eventPublisher.publishEvent(new ServerContextDocumentClosedEvent(serverContext, documentContext));
    assertThat(index.get(uri, node)).as("сброс на закрытие документа").isNull();

    // удаление файла сбрасывает кэш по URI.
    index.put(uri, node, types);
    eventPublisher.publishEvent(new ServerContextDocumentRemovedEvent(serverContext, uri));
    assertThat(index.get(uri, node)).as("сброс на удаление файла").isNull();
  }

  @Test
  void fullyClearsOnConfigurationTypesRegistered() {
    // given — записи по двум разным документам.
    var doc1 = TestUtils.getDocumentContext("Процедура Тест1() КонецПроцедуры");
    var doc2 = TestUtils.getDocumentContext("Процедура Тест2() КонецПроцедуры");
    var types = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка"));
    index.put(doc1.getUri(), doc1.getAst(), types);
    index.put(doc2.getUri(), doc2.getAst(), types);
    assertThat(index.get(doc1.getUri(), doc1.getAst())).isNotNull();
    assertThat(index.get(doc2.getUri(), doc2.getAst())).isNotNull();

    // when — зарегистрированы конфигурационные типы.
    eventPublisher.publishEvent(new ConfigurationTypesRegisteredEvent(doc1.getServerContext()));

    // then — полный сброс: конфигурационные член-доступы, инферившиеся в пусто,
    // должны быть пересчитаны с заполненным реестром.
    assertThat(index.get(doc1.getUri(), doc1.getAst())).as("полный сброс, doc1").isNull();
    assertThat(index.get(doc2.getUri(), doc2.getAst())).as("полный сброс, doc2").isNull();
  }
}
