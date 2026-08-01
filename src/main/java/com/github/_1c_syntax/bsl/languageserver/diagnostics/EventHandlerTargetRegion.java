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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormHandlerRoleIndex;
import com.github._1c_syntax.bsl.types.MultiName;
import com.github._1c_syntax.bsl.types.ScriptVariant;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Область, куда обработчик обязан лечь по стандарту std455. Имя двуязычно, а у событий
 * элементов таблицы к нему дописывается имя самой таблицы — стандарт задаёт его
 * шаблоном {@code ОбработчикиСобытийЭлементовТаблицыФормы<Имя таблицы>}.
 *
 * @param name   имя области из {@link Keywords}.
 * @param suffix то, что дописывается к имени; пусто у всех областей, кроме таблиц.
 */
record EventHandlerTargetRegion(MultiName name, String suffix) {

  /** Область обработчиков событий у всех модулей, кроме формы. */
  static final EventHandlerTargetRegion OBJECT =
    new EventHandlerTargetRegion(Keywords.EVENT_HANDLERS_REGION);

  /**
   * Область событий самой формы. Она же — отступной вариант, когда объявитель
   * обработчика неизвестен: назвать пользователю одну область всё равно надо, а из
   * четырёх форменных эта общая.
   */
  static final EventHandlerTargetRegion FORM_EVENTS =
    new EventHandlerTargetRegion(Keywords.FORM_EVENT_HANDLERS_REGION);

  /**
   * Области модуля формы, куда допустимо класть обработчик, когда о нём известно
   * только то, что он обработчик: форма могла не прочитаться, а метод мог совпасть
   * с событием по имени, не будучи объявленным в {@code Form.xml}.
   */
  private static final Set<String> FORM_PREFIXES = Set.of(
    Keywords.FORM_EVENT_HANDLERS_REGION.getRu().toLowerCase(Locale.ROOT),
    Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION.getRu().toLowerCase(Locale.ROOT),
    Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START.getRu().toLowerCase(Locale.ROOT),
    Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION.getRu().toLowerCase(Locale.ROOT),
    Keywords.FORM_EVENT_HANDLERS_REGION.getEn().toLowerCase(Locale.ROOT),
    Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION.getEn().toLowerCase(Locale.ROOT),
    Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START.getEn().toLowerCase(Locale.ROOT),
    Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION.getEn().toLowerCase(Locale.ROOT)
  );

  EventHandlerTargetRegion(MultiName name) {
    this(name, "");
  }

  /** Область по тому, кем обработчик объявлен: у каждой роли она своя. */
  static EventHandlerTargetRegion of(FormHandlerRoleIndex.Handler handler) {
    return switch (handler.role()) {
      case FORM_EVENT -> FORM_EVENTS;
      case HEADER_ITEM_EVENT -> new EventHandlerTargetRegion(Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION);
      case TABLE_ITEM_EVENT ->
        new EventHandlerTargetRegion(Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START, handler.owner());
      case COMMAND -> new EventHandlerTargetRegion(Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION);
    };
  }

  /**
   * Область, которую называем пользователю: конкретная неизвестна, только когда
   * объявитель обработчика не найден, а сообщение и quick fix обязаны назвать одну и
   * ту же — иначе диагностика зовёт в одну область, а исправление создаёт другую.
   */
  static EventHandlerTargetRegion orFallback(@Nullable EventHandlerTargetRegion expected) {
    return expected == null ? FORM_EVENTS : expected;
  }

  /** Любая ли это из областей обработчиков модуля формы. */
  static boolean isAnyFormRegion(String regionName) {
    var lowerCased = regionName.toLowerCase(Locale.ROOT);
    return FORM_PREFIXES.stream().anyMatch(lowerCased::startsWith);
  }

  /** Имя области на языке модуля — его и надо написать в коде. */
  String forVariant(ScriptVariant variant) {
    return name.get(variant) + suffix;
  }

  /** Это ли та самая область — независимо от языка, на котором она названа. */
  boolean matches(String regionName) {
    return regionName.equalsIgnoreCase(name.getRu() + suffix)
      || regionName.equalsIgnoreCase(name.getEn() + suffix);
  }
}
