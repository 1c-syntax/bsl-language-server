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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.types.registry.ConfigurationTypesProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Значение функции, вызывающей функцию своего же модуля, при первом разборе документа
 * считается по посчитанному значению вызванной, а не по одному её описанию.
 * <p>
 * При наполнении рабочей области документ разбирается впервые, и его состояние выставляется
 * уже после события о разборе. Расчёт значений идёт по этому событию, и вызванная функция
 * того же модуля должна посчитаться на месте, а не остаться непосчитанной.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
class SameDocumentFunctionOnFirstParseTest extends AbstractServerContextAwareTest {

  private static final Path FIXTURE = Path.of("src/test/resources/metadata/sameDocumentFirstParse").toAbsolutePath();

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private ApplicationEventPublisher publisher;

  @Autowired
  private TypeService typeService;

  @Test
  void exportedFunctionDeclaredBelowIsComputedInPlace() {
    assertThat(valueOf("Точка")).containsExactlyInAnyOrder("Неопределено", "Структура[Схема]");
  }

  @Test
  void localFunctionIsComputedInPlace() {
    assertThat(valueOf("ТочкаЧерезСлужебную")).containsExactlyInAnyOrder("Неопределено", "Структура[Схема]");
  }

  /**
   * Значение функции модуля после разбора так, как рабочую область наполняет
   * {@code populateContext}: разбор, освобождение, событие наполнения.
   */
  private List<String> valueOf(String functionName) {
    initServerContext(FIXTURE, false);
    context.getConfiguration();
    provider.tryRegister();
    var uri = Absolute.uri(FIXTURE.resolve("CommonModules").resolve("Помощник").resolve("Ext").resolve("Module.bsl")
      .toUri());
    var documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext);
    context.tryClearDocument(documentContext);
    publisher.publishEvent(new ServerContextPopulatedEvent(context));

    var method = documentContext.getSymbolTree().getMethodSymbol(functionName).orElseThrow();
    var types = typeService.getReturnTypes(method);
    return types.refs().stream()
      .map(ref -> {
        var fields = types.getLocalFields(ref).keySet();
        return fields.isEmpty() ? ref.qualifiedName() : ref.qualifiedName() + new TreeSet<>(fields);
      })
      .map(name -> name.replace(" ", ""))
      .toList();
  }

}
