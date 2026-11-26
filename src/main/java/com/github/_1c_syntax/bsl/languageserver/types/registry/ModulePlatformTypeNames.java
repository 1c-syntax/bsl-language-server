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

import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Имена семейств платформенных типов, представляющих контекст отдельных видов модулей.
 * <p>
 * Общий источник имён для поиска доступности и контрактов событий в синтакс-помощнике.
 * Класс задаёт только имя семейства; контексты и события получаются из реестра типов.
 * Для видов, зависящих от объекта метаданных, имя здесь не задаётся.
 */
@UtilityClass
public class ModulePlatformTypeNames {

  /**
   * Базовое имя типа модуля в СП без имени объекта метаданных или generic-параметра.
   * Специализация конкретного сервиса может иметь суффикс {@code .<Имя сервиса>}.
   *
   * @param moduleType вид модуля.
   * @return имя семейства или {@code null}, если для вида модуля нет фиксированного семейства,
   *     в том числе когда имя зависит от вида объекта метаданных.
   */
  public static @Nullable String familyName(ModuleType moduleType) {
    return switch (moduleType) {
      case CommandModule -> "Модуль команды";
      case HTTPServiceModule -> "Модуль HTTP-сервиса";
      case WEBServiceModule -> "Модуль Web-сервиса";
      case IntegrationServiceModule -> "Модуль сервиса интеграции";
      case BotModule -> "Модуль бота";
      case WEBSocketClientModule -> "Модуль WebSocket клиента";
      case RecalculationModule -> "ПерерасчетНаборЗаписей";
      default -> null;
    };
  }
}
