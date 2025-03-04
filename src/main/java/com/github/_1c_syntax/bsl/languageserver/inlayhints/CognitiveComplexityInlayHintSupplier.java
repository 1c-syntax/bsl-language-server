/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.computer.ComplexitySecondaryLocation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Поставщик подсказок о когнитивной сложности методов.
 */
@Component
@RequiredArgsConstructor
public class CognitiveComplexityInlayHintSupplier extends AbstractComplexityInlayHintSupplier {

  /**
   * {@inheritDoc}
   */
  @Override
  protected Map<MethodSymbol, List<ComplexitySecondaryLocation>> getComplexityLocations(
    DocumentContext documentContext
  ) {
    return documentContext
      .getCognitiveComplexityData()
      .getMethodsComplexitySecondaryLocations();
  }
}
