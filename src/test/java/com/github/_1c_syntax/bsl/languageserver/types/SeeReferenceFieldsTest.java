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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ссылка {@code см.} на член другого модуля приносит не только имя типа, но и то, что описано
 * вместе с ним: колонки таблицы у параметра, поля структуры у возвращаемого значения.
 */
@CleanupContextBeforeClassAndAfterClass
class SeeReferenceFieldsTest extends AbstractServerContextAwareTest {

  private static final Path FIXTURE = Path.of("src/test/resources/metadata/seeReferenceFields").toAbsolutePath();

  @Autowired
  private TypeService typeService;

  private DocumentContext consumer;

  @BeforeEach
  void setUp() {
    initServerContextOnce(FIXTURE);
    consumer = context.getDocument(Absolute.uri(
      FIXTURE.resolve("CommonModules").resolve("Потребитель").resolve("Ext").resolve("Module.bsl").toUri()));
    context.rebuildDocument(consumer);
  }

  @Test
  void rowOfParameterDescribedInAnotherModuleHasItsColumns() {
    // when
    var types = typeService.receiverTypesAt(consumer, positionOf("Команда.Представление", "Команда.".length()));

    // then
    assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("СтрокаТаблицыЗначений");
    assertThat(fieldNames(types)).containsExactly("Представление");
  }

  @Test
  void valueOfFunctionDescribedInAnotherModuleHasItsFields() {
    // when
    var types = typeService.receiverTypesAt(consumer, positionOf("Настройки.Имя", "Настройки.".length()));

    // then
    assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
    assertThat(fieldNames(types)).containsExactly("Имя");
  }

  @Test
  void referenceToProcedureGivesNoValue() {
    // `см. Источник.ПередДобавлением` — процедура: значения у неё нет, описывать нечего.
    var types = typeService.expressionTypesAt(consumer,
      positionOf("ТипПроцедурой = Процедурой", "ТипПроцедурой = ".length()));

    assertThat(types.refs()).isEmpty();
  }

  @Test
  void referenceToMissingParameterGivesNoType() {
    var types = typeService.expressionTypesAt(consumer,
      positionOf("ТипБезПараметра = БезПараметра", "ТипБезПараметра = ".length()));

    assertThat(types.refs()).isEmpty();
  }

  @Test
  void referencesClosedIntoCycleBetweenModulesTerminate() {
    // `Потребитель.Крайние.ПоКругу` ссылается на `Источник.Цикл.ПоКругу`, а тот — обратно:
    // описания ходят по кругу, и разворот обрывается, а не уходит в бесконечную рекурсию.
    var types = typeService.expressionTypesAt(consumer,
      positionOf("ТипПоКругу = ПоКругу", "ТипПоКругу = ".length()));

    assertThat(types.refs()).isEmpty();
  }

  private static List<String> fieldNames(TypeSet types) {
    var names = new TreeSet<String>();
    for (var ref : types.refs()) {
      names.addAll(types.getLocalFields(ref).keySet());
    }
    return List.copyOf(names);
  }

  /** Позиция внутри первого вхождения маркера: смещение от его начала плюс один символ. */
  private Position positionOf(String marker, int offsetInMarker) {
    var content = consumer.getContent();
    var markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер '%s' найден в фикстуре", marker).isNotNegative();
    var targetOffset = markerStart + offsetInMarker + 1;
    var lineStart = content.lastIndexOf('\n', targetOffset - 1) + 1;
    var line = content.substring(0, targetOffset).split("\n").length - 1;
    return new Position(line, targetOffset - lineStart);
  }
}
