/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.context.symbol.annotations;

import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.Getter;

import java.util.stream.Stream;

/**
 * Класс хранит информацию о виде аннотации.
 * См. {@link Annotation}
 */
public enum AnnotationKind {
  BEFORE(BSLParser.ANNOTATION_BEFORE_SYMBOL),
  AFTER(BSLParser.ANNOTATION_AFTER_SYMBOL),
  AROUND(BSLParser.ANNOTATION_AROUND_SYMBOL),
  CHANGEANDVALIDATE(BSLParser.ANNOTATION_CHANGEANDVALIDATE_SYMBOL),
  CUSTOM(BSLParser.ANNOTATION_CUSTOM_SYMBOL);

  @Getter
  private final int tokenType;

  AnnotationKind(int tokenType) {
    this.tokenType = tokenType;
  }

  public static AnnotationKind of(int tokenType) {
    return Stream.of(values())
      .filter(annotationKind -> annotationKind.getTokenType() == tokenType)
      .findAny().orElse(CUSTOM);
  }
}
