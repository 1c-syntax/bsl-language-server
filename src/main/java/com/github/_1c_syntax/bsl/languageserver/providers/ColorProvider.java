/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.color.ColorInformationSupplier;
import com.github._1c_syntax.bsl.languageserver.color.ColorPresentationSupplier;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ColorProvider {
  private final List<ColorInformationSupplier> colorInformationSuppliers;
  private final List<ColorPresentationSupplier> colorPresentationSuppliers;

  public List<ColorInformation> getDocumentColor(DocumentContext documentContext) {
    return colorInformationSuppliers.stream()
      .map(colorInformationSupplier -> colorInformationSupplier.getColorInformation(documentContext))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  public List<ColorPresentation> getColorPresentation(DocumentContext documentContext, ColorPresentationParams params) {
    return colorPresentationSuppliers.stream()
      .map(colorPresentationSupplier -> colorPresentationSupplier.getColorPresentation(documentContext, params))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }
}
