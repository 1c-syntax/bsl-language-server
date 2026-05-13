/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

/**
 * Реестр типов и провайдеры. {@link com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry}
 * — центральная точка; {@link com.github._1c_syntax.bsl.languageserver.types.registry.PlatformTypesProvider}
 * — расширяемый источник платформенных типов (1С / OneScript / встроенный
 * bootstrap); {@link com.github._1c_syntax.bsl.languageserver.types.registry.UserTypesProvider}
 * — событийная регистрация пользовательских типов.
 */
package com.github._1c_syntax.bsl.languageserver.types.registry;
