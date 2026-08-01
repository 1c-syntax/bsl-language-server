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

import org.jspecify.annotations.Nullable;

/**
 * Вид данных формы — тип, в который платформа превращает реквизит управляемой формы.
 * Прикладные типы на форму не переносятся: клиент видит не объект, а его данные.
 * <p>
 * Соответствие задано платформой и от конфигурации не зависит: объект (в том числе
 * менеджер значения константы и менеджер записи регистра) становится структурой,
 * набор записей — структурой с коллекцией записей, таблица значений — коллекцией,
 * дерево значений — деревом. Ссылки, примитивы, {@code СписокЗначений} и прочие
 * переносимые типы остаются собой и в этот перечень не входят.
 * <p>
 * У каждого вида — ru/en-имя платформенного типа данных формы, признак «состав свойств
 * известен, и под реквизит имеет смысл заводить специализацию по прикладному типу», а у
 * коллекций — ещё и ru/en-имя типа строки.
 */
enum FormDataKind {
  STRUCTURE(FormPlatformTypes.FORM_DATA_STRUCTURE_RU, FormPlatformTypes.FORM_DATA_STRUCTURE_EN, true, null, null),
  STRUCTURE_WITH_COLLECTION("ДанныеФормыСтруктураСКоллекцией", "FormDataStructureWithCollection",
    true, null, null),
  COLLECTION(FormPlatformTypes.FORM_DATA_COLLECTION_RU, FormPlatformTypes.FORM_DATA_COLLECTION_EN, false,
    FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_RU, FormPlatformTypes.FORM_DATA_COLLECTION_ITEM_EN),
  TREE("ДанныеФормыДерево", "FormDataTree", false, "ДанныеФормыЭлементДерева", "FormDataTreeItem");

  private final String baseTypeRu;
  private final String baseTypeEn;
  private final boolean specializable;
  private final @Nullable String itemTypeRu;
  private final @Nullable String itemTypeEn;

  FormDataKind(String baseTypeRu, String baseTypeEn, boolean specializable,
               @Nullable String itemTypeRu, @Nullable String itemTypeEn) {
    this.baseTypeRu = baseTypeRu;
    this.baseTypeEn = baseTypeEn;
    this.specializable = specializable;
    this.itemTypeRu = itemTypeRu;
    this.itemTypeEn = itemTypeEn;
  }

  String baseTypeRu() {
    return baseTypeRu;
  }

  String baseTypeEn() {
    return baseTypeEn;
  }

  /**
   * {@code true}, если состав свойств данных формы повторяет прикладной тип реквизита
   * и под него стоит завести специализацию. У таблиц и деревьев значений прикладного
   * типа с нужным составом нет — их колонки объявлены в самой форме, поэтому
   * специализируются они не отсюда, а от {@link #itemTypeRu()}.
   */
  boolean specializable() {
    return specializable;
  }

  /** Тип строки коллекции; {@code null} — вид не коллекция, строк у него нет. */
  @Nullable String itemTypeRu() {
    return itemTypeRu;
  }

  /** En-имя типа строки; {@code null} — вид не коллекция. */
  @Nullable String itemTypeEn() {
    return itemTypeEn;
  }
}
