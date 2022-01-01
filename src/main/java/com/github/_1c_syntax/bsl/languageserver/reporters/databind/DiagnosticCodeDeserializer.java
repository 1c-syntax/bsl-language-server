/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;

/**
 * Десериализатор для {@link Either}, выступающего в роли хранилища кода диагностики.
 * См. {@link DiagnosticCode}
 */
public class DiagnosticCodeDeserializer extends JsonDeserializer<Either<String, Number>> {

  @Override
  public Either<String, Number> deserialize(
    JsonParser p,
    DeserializationContext ctxt
  ) throws IOException {
    return Either.forLeft(p.getValueAsString());
  }

}
