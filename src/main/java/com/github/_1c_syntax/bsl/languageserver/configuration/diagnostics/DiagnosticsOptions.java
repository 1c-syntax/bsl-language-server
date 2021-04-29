/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.configuration.diagnostics;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.databind.ParametersDeserializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.HashMap;
import java.util.Map;

/**
 * Корневой класс для настройки {@link com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider}
 */
@Data
@AllArgsConstructor(onConstructor = @__({@JsonCreator(mode = JsonCreator.Mode.DISABLED)}))
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiagnosticsOptions {
  private ComputeTrigger computeTrigger = ComputeTrigger.ONSAVE;
  private SkipSupport skipSupport = SkipSupport.NEVER;
  private Mode mode = Mode.ON;
  private boolean ordinaryAppSupport = true;

  @JsonDeserialize(using = ParametersDeserializer.class)
  private Map<String, Either<Boolean, Map<String, Object>>> parameters = new HashMap<>();
}
