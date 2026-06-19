/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

/**
 * Вывод типов выражений BSL: основной
 * {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer},
 * адресация по позиции
 * {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionAtPosition}.
 * Типы из inline-комментариев теперь берутся структурно из
 * {@code VariableDescription.trailingDescription.getTypes()} парсера.
 */
@NullMarked
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import org.jspecify.annotations.NullMarked;
