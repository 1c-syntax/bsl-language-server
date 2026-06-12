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
package com.github._1c_syntax.bsl.languageserver.types.model;

/**
 * Платформенный тип ({@code Массив}, {@code Структура}, {@code ТаблицаЗначений},
 * {@code СправочникМенеджер.X} и т.п.).
 * <p>
 * Сам по себе платформенный тип может расширяться пользовательскими членами
 * (см. {@code ConfigurationModuleMembersProvider}). Для запроса полного списка
 * членов используйте {@link com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry#getMembers(TypeRef, com.github._1c_syntax.bsl.types.FileType)}.
 *
 * @param ref ссылка на тип в реестре типов
 */
public record PlatformType(TypeRef ref) implements Type {
}
