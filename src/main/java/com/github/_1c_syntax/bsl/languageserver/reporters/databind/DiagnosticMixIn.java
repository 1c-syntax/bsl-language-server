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
package com.github._1c_syntax.bsl.languageserver.reporters.databind;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * Mix-in класс для донастройки (де)сериализации {@link org.eclipse.lsp4j.Diagnostic} через Jackson ObjectMapper.
 * См. {@link tools.jackson.databind.json.JsonMapper.Builder#addMixIn(Class, Class)}
 */
public abstract class DiagnosticMixIn {

  @JsonSerialize(using = DiagnosticCodeSerializer.class)
  @JsonDeserialize(using = DiagnosticCodeDeserializer.class)
  private Either<String, Number> code;

  @JsonIgnore
  public abstract void setCode(String code);

  @JsonProperty
  public abstract void setCode(Either<String, Number> code);
}
