/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

/**
 * Источники типов OneScript: обнаружение библиотек
 * ({@link com.github._1c_syntax.bsl.languageserver.types.oscript.ConventionalLibraryDiscovery},
 * {@link com.github._1c_syntax.bsl.languageserver.types.oscript.LibConfigDiscovery}),
 * разбор {@code lib.config}
 * ({@link com.github._1c_syntax.bsl.languageserver.types.oscript.LibConfigParser}),
 * индекс
 * ({@link com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex})
 * и регистрация членов модулей в реестре типов.
 */
@NullMarked
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import org.jspecify.annotations.NullMarked;
