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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SelfMemberClassifier;

import java.util.Set;
import java.util.function.Predicate;

/** Сохраняет имена, распознанные как неявные реквизиты текущего модуля. */
final class RecordingSelfMemberClassifier implements SelfMemberClassifier {
  private final SelfMemberClassifier delegate;
  private final Set<String> recordedProperties;

  RecordingSelfMemberClassifier(SelfMemberClassifier delegate, Set<String> recordedProperties) {
    this.delegate = delegate;
    this.recordedProperties = recordedProperties;
  }

  @Override
  public boolean isBareSelfProperty(DocumentContext document, String name) {
    return recorded(delegate.isBareSelfProperty(document, name), name);
  }

  @Override
  public Predicate<String> bareSelfPropertiesOf(DocumentContext document) {
    var bareSelfProperty = delegate.bareSelfPropertiesOf(document);
    return name -> recorded(bareSelfProperty.test(name), name);
  }

  private boolean recorded(boolean bareSelfProperty, String name) {
    if (bareSelfProperty) {
      recordedProperties.add(name);
    }
    return bareSelfProperty;
  }
}
