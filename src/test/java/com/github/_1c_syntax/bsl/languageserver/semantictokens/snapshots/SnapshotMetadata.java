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
package com.github._1c_syntax.bsl.languageserver.semantictokens.snapshots;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Метаданные снапшота в формате JSON-файла, который {@code sentry-cli snapshots upload}
 * читает рядом с изображением.
 *
 * @param displayName подпись в окне сравнения
 * @param group       группа в боковой панели
 * @param canvasTheme тема холста: {@code light} или {@code dark}
 * @param tags        строковые метки для фильтрации
 * @param context     произвольные данные для диагностики изменений
 */
public record SnapshotMetadata(
  @JsonProperty("display_name") String displayName,
  @JsonProperty("group") String group,
  @JsonProperty("canvas_theme") String canvasTheme,
  @JsonProperty("tags") Map<String, String> tags,
  @JsonProperty("context") Map<String, Object> context
) {
}
