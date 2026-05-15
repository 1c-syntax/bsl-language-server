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
package com.github._1c_syntax.bsl.languageserver.configuration.platform;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Workspace-scoped настройки для подсистемы платформенных типов
 * (см. {@code BslContextPlatformTypesProvider}).
 */
@Getter
@Setter
public class V8PlatformOptions {

  /**
   * Путь к каталогу {@code bin} установленной платформы 1С — там, где лежат
   * файлы синтакс-помощника ({@code shcntx_*.hbk}, {@code shlang_*.hbk}).
   * Если не задан (по умолчанию), используется автодетект самой свежей
   * установки на машине.
   * <p>
   * Пример: {@code C:\Program Files\1cv8\8.3.27.1786\bin}.
   */
  @JsonProperty("binPath")
  @Nullable
  private Path binPath;
}
