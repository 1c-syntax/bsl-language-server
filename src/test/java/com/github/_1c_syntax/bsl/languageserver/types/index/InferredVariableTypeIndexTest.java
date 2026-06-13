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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClearedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClosedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

class InferredVariableTypeIndexTest extends AbstractServerContextAwareTest {

  @Autowired
  private ExpressionTypeInferencer inferencer;

  @Autowired
  private InferredVariableTypeIndex index;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Test
  void cachesInferredVariableTypeAndInvalidatesByUriEvents() {
    // given — модуль с типизированной переменной.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          ТЗ = Новый ТаблицаЗначений;
          А = ТЗ.Колонки;
      КонецПроцедуры
      """);
    var serverContext = documentContext.getServerContext();
    var uri = documentContext.getUri();
    var variable = variable(documentContext, "ТЗ");

    // before — тип ещё не вычислялся.
    assertThat(index.get(variable)).as("до инференса кэш пуст").isNull();

    // after inferSymbol — тип выведен и закэширован.
    var types = inferencer.inferSymbol(variable);
    assertThat(types.refs()).extracting(TypeRef::qualifiedName).contains("ТаблицаЗначений");
    assertThat(index.get(variable)).as("после инференса тип закэширован").isEqualTo(types);

    // изменение документа сбрасывает кэш по URI.
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    assertThat(index.get(variable)).as("сброс на изменение содержимого").isNull();

    // освобождение вторичных данных (tryClearDocument в batch-анализе) сбрасывает кэш по URI.
    inferencer.inferSymbol(variable);
    assertThat(index.get(variable)).isNotNull();
    eventPublisher.publishEvent(new ServerContextDocumentClearedEvent(serverContext, documentContext));
    assertThat(index.get(variable)).as("сброс на освобождение вторичных данных").isNull();

    // закрытие документа сбрасывает кэш по URI.
    inferencer.inferSymbol(variable);
    assertThat(index.get(variable)).isNotNull();
    eventPublisher.publishEvent(new ServerContextDocumentClosedEvent(serverContext, documentContext));
    assertThat(index.get(variable)).as("сброс на закрытие документа").isNull();

    // удаление файла сбрасывает кэш по URI.
    inferencer.inferSymbol(variable);
    assertThat(index.get(variable)).isNotNull();
    eventPublisher.publishEvent(new ServerContextDocumentRemovedEvent(serverContext, uri));
    assertThat(index.get(variable)).as("сброс на удаление файла").isNull();
  }

  @Test
  void realTryClearDocumentEvictsCacheViaAop() {
    // given — документ с инферированной переменной в реальном ServerContext.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          ТЗ = Новый ТаблицаЗначений;
      КонецПроцедуры
      """);
    var serverContext = documentContext.getServerContext();
    var variable = variable(documentContext, "ТЗ");
    inferencer.inferSymbol(variable);
    assertThat(index.get(variable)).as("тип закэширован").isNotNull();

    // when — реальный tryClearDocument на не-открытом документе; AOP-аспект
    // публикует ServerContextDocumentClearedEvent.
    var cleared = serverContext.tryClearDocument(documentContext);

    // then — метод сообщил о реальной очистке, и кэш сброшен сквозь аспект.
    assertThat(cleared).as("данные реально освобождены").isTrue();
    assertThat(index.get(variable)).as("кэш сброшен событием очистки").isNull();
  }

  @Test
  void openedDocumentIsNotClearedAndKeepsCache() {
    // given — открытый в редакторе документ с инферированной переменной.
    var source = """
      Процедура Тест()
          ТЗ = Новый ТаблицаЗначений;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(source);
    var serverContext = documentContext.getServerContext();
    serverContext.openDocument(documentContext, source, 1);
    var variable = variable(documentContext, "ТЗ");
    inferencer.inferSymbol(variable);
    assertThat(index.get(variable)).isNotNull();

    // when — tryClearDocument на открытом документе.
    var cleared = serverContext.tryClearDocument(documentContext);

    // then — no-op: событие не публикуется, кэш открытого документа уцелел.
    assertThat(cleared).as("открытый документ не очищается").isFalse();
    assertThat(index.get(variable)).as("кэш открытого документа сохранён").isNotNull();
  }

  private static VariableSymbol variable(DocumentContext documentContext, String name) {
    return documentContext.getSymbolTree().getVariables().stream()
      .filter(v -> v.getName().equalsIgnoreCase(name))
      .findFirst()
      .orElseThrow();
  }
}
