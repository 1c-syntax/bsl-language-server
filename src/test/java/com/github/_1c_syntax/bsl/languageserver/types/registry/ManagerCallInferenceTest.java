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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Вызовы через менеджер объекта: тип получателя цепочки и формы, которые отдают его методы.
 * <p>
 * Автодополнение берёт тип получателя не там, где инференс (см. {@code receiverTypesAt}),
 * поэтому специализация, видимая в выводе типов, может не доезжать до подсказки.
 * Репорт: на {@code РегистрыБухгалтерии.X.СоздатьНаборЗаписей().} у {@code Добавить}
 * показывался обобщённый {@code РегистрБухгалтерииЗапись.<Имя регистра бухгалтерии>}.
 */
@CleanupContextBeforeClassAndAfterClass
class ManagerCallInferenceTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_RECORDERS = "src/test/resources/metadata/ordinaryForms";
  private static final String MODULE = "CommonModules/Модуль1/Ext/Module.bsl";

  private static final String RECORD_SET = "РегистрБухгалтерииНаборЗаписей.РегистрБухгалтерии1";

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private TypeService typeService;

  private DocumentContext documentContext;

  @BeforeEach
  void setUp() {
    initServerContextOnce(Absolute.path(PATH_TO_RECORDERS));
    context.getConfiguration();
    provider.tryRegister();
    var uri = Absolute.uri(new File(PATH_TO_RECORDERS, MODULE));
    documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext);
  }

  @Test
  void receiverOfChainedCallIsTheSpecializedRecordSet() {
    // РегистрыБухгалтерии.РегистрБухгалтерии1.СоздатьНаборЗаписей().Добавить()
    assertThat(receiverTypesAt(5, 64))
      .containsExactly(RECORD_SET);
  }

  @Test
  void receiverOfVariableIsTheSpecializedRecordSet() {
    // Набор.Добавить()
    assertThat(receiverTypesAt(3, 8))
      .containsExactly(RECORD_SET);
  }

  @Test
  void managerFormMethodsGiveTheFormOfThatObject() {
    // Имени формы эти методы не требуют: какую открывать — известно из её роли
    // у объекта. Формы у Справочник1 обычные, поэтому тип — Форма.<mdoRef>.
    assertThat(typesAtRhs("ФормаСписка"))
      .containsExactly("Форма.Справочник.Справочник1.Форма.ФормаСписка");
    assertThat(typesAtRhs("ФормаВыбора"))
      .containsExactly("Форма.Справочник.Справочник1.Форма.ФормаВыбора");
    assertThat(typesAtRhs("ФормаЭлемента"))
      .containsExactly("Форма.Справочник.Справочник1.Форма.ФормаЭлемента");
  }

  @Test
  void managerGetFormTakesTheNameRelativeToTheObject() {
    // У ПолучитьФорму имя задаётся относительно объекта, а не полным путём.
    assertThat(typesAtRhs("ФормаПоИмени"))
      .containsExactly("Форма.Справочник.Справочник1.Форма.ФормаВыбора");
  }

  /** Типы правой части присваивания: каретка на последнем сегменте цепочки. */
  private java.util.List<String> typesAtRhs(String assignedVar) {
    var content = documentContext.getContent();
    var marker = assignedVar + " = ";
    var markerIdx = content.indexOf(marker);
    assertThat(markerIdx).as("маркер `%s` не найден", marker).isGreaterThanOrEqualTo(0);
    var rhsStart = markerIdx + marker.length();
    var caret = content.lastIndexOf('.', content.indexOf(';', rhsStart)) + 1;
    var lineStart = content.lastIndexOf('\n', caret) + 1;
    var line = content.substring(0, caret).split("\n").length - 1;
    return typeService.expressionTypesAt(documentContext, new Position(line, caret - lineStart))
      .refs().stream()
      .map(TypeRef::qualifiedName)
      .toList();
  }

  private java.util.List<String> receiverTypesAt(int line, int character) {
    return typeService.receiverTypesAt(documentContext, new Position(line, character))
      .refs().stream()
      .map(TypeRef::qualifiedName)
      .toList();
  }
}
