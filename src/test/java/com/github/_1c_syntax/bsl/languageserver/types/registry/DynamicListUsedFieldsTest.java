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

import com.github._1c_syntax.bsl.mdo.storage.ManagedFormData;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormField;
import com.github._1c_syntax.bsl.mdo.storage.form.FormTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicListUsedFieldsTest {

  @Test
  void elementPathNamesTheFieldItStandsOn() {
    // given
    var data = ManagedFormData.builder()
      .addAttributes(list().build())
      .addElements(FormTable.builder().name("Список").dataPath("Список").build())
      .addElements(FormField.builder().name("СписокКод").dataPath("Список.Код").build())
      .build();

    // when
    var fields = DynamicListUsedFields.collect(data);

    // then
    assertThat(fields.get("список"))
      .as("сама таблица стоит на списке целиком и поля не называет")
      .containsExactly("Код");
  }

  @Test
  void pathIntoTheFieldTypeNamesBothLinkAndDereferencedField() {
    // given
    // У элемента на `Список.Контрагент.ИНН` в данных строки лежат оба:
    // сама ссылка и разыменованное поле под составным именем.
    var data = ManagedFormData.builder()
      .addAttributes(list().build())
      .addElements(FormField.builder().name("ИНН").dataPath("Список.Контрагент.ИНН").build())
      .build();

    // when
    var fields = DynamicListUsedFields.collect(data);

    // then
    assertThat(fields.get("список")).containsExactlyInAnyOrder("Контрагент", "Контрагент.ИНН");
  }

  @Test
  void rowPictureNamesItsFieldWithoutAnyColumn() {
    // given
    var data = ManagedFormData.builder()
      .addAttributes(list().build())
      .addElements(FormTable.builder()
        .name("Список")
        .dataPath("Список")
        .rowPictureDataPath("Список.ИндексКартинки")
        .build())
      .build();

    // when
    var fields = DynamicListUsedFields.collect(data);

    // then
    assertThat(fields.get("список"))
      .as("колонки у картинки строки нет, а поле читается")
      .containsExactly("ИндексКартинки");
  }

  @Test
  void brokenPathStillNamesItsField() {
    // given
    // Тильдой форма помечает битое свойство: путь ведёт туда, чего в источнике
    // нет. Поле формы под таким путём есть, и в наборе оно нужно — колонкой оно
    // станет без типа.
    var data = ManagedFormData.builder()
      .addAttributes(list().addUseAlwaysFields("~Список.Сервер").build())
      .addElements(FormTable.builder()
        .name("Список")
        .dataPath("Список")
        .rowPictureDataPath("~Список.ИндексКартинки")
        .build())
      .addElements(FormField.builder().name("Код").dataPath("~Список.Код").build())
      .build();

    // when
    var fields = DynamicListUsedFields.collect(data);

    // then
    assertThat(fields.get("список")).containsExactlyInAnyOrder("Сервер", "ИндексКартинки", "Код");
  }

  @Test
  void useAlwaysNamesTheField() {
    // given
    var data = ManagedFormData.builder()
      .addAttributes(list().addUseAlwaysFields("Список.Сервер").build())
      .build();

    // when
    var fields = DynamicListUsedFields.collect(data);

    // then
    assertThat(fields.get("список")).containsExactly("Сервер");
  }

  @Test
  void listSettingsNameFieldsWithoutTheAttributeName() {
    // given
    // Отбор и оформление самого списка называют поля относительно него.
    var data = ManagedFormData.builder()
      .addAttributes(list().addSettingsFields("ПометкаУдаления").addSettingsFields("Контрагент.ИНН").build())
      .build();

    // when
    var fields = DynamicListUsedFields.collect(data);

    // then
    assertThat(fields.get("список"))
      .containsExactlyInAnyOrder("ПометкаУдаления", "Контрагент", "Контрагент.ИНН");
  }

  @Test
  void formConditionalAppearanceNamesFieldsByPath() {
    // given
    var data = ManagedFormData.builder()
      .addAttributes(list().build())
      .addConditionalAppearanceFields("Список.Состояние")
      .addConditionalAppearanceFields("Объект.Том")
      .build();

    // when
    var fields = DynamicListUsedFields.collect(data);

    // then
    assertThat(fields.get("список"))
      .as("путь в другой реквизит полем этого списка не является")
      .containsExactly("Состояние");
  }

  @Test
  void listNobodyReadsHasNoFields() {
    // given
    var data = ManagedFormData.builder()
      .addAttributes(list().build())
      .addElements(FormField.builder().name("Прочее").dataPath("Объект.Наименование").build())
      .build();

    // when
    var fields = DynamicListUsedFields.collect(data);

    // then
    assertThat(fields.get("список")).isEmpty();
  }

  private static FormDynamicListAttribute.FormDynamicListAttributeBuilder list() {
    return FormDynamicListAttribute.builder()
      .name("Список")
      .mainTable("Catalog.Справочник1");
  }
}
