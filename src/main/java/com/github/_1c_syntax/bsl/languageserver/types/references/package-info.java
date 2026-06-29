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

/**
 * Type-aware реализации порта {@link com.github._1c_syntax.bsl.languageserver.references.ReferenceFinder}.
 * <p>
 * Резолвят ссылки, консультируясь с системой типов (платформенные/конфигурационные члены,
 * конструкторы, ключевые слова). Сам порт и type-agnostic finder'ы живут в пакете
 * {@code references}; эти адаптеры вынесены сюда, к своим зависимостям из {@code types},
 * чтобы пакет {@code references} не зависел от {@code types}.
 */
@NullMarked
package com.github._1c_syntax.bsl.languageserver.types.references;

import org.jspecify.annotations.NullMarked;
