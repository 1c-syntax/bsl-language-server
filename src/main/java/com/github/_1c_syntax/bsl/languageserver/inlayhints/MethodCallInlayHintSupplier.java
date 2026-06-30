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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Единый поставщик подсказок об именах параметров для вызовов методов.
 * <p>
 * Подсказки у вызовов source-defined методов и платформенных методов/глобальных
 * функций/конструкторов выглядят одинаково (имя параметра рядом с передаваемым
 * значением) и отличаются лишь источником метаданных и способом резолва вызова,
 * поэтому управляются единым ключом конфигурации {@code inlayHint.parameters.methodCall}
 * (идентификатор сапплаера — {@code methodCall}).
 * <p>
 * Построение делегируется двум коллекторам:
 * <ul>
 *   <li>{@link SourceDefinedMethodCallInlayHintCollector} — вызовы source-defined
 *       методов (в т.ч. голые вызовы процедур текущего модуля), разрешаемые по
 *       индексу ссылок; метка несёт ссылку на объявление параметра;</li>
 *   <li>{@link PlatformMethodCallInlayHintCollector} — платформенные методы,
 *       глобальные функции и конструкторы, разрешаемые через систему типов.</li>
 * </ul>
 * Области покрытия коллекторов не пересекаются: платформенный коллектор отбрасывает
 * члены с непустым {@code sourceSymbol}, которые относятся к source-defined вызовам.
 */
@Component
public class MethodCallInlayHintSupplier implements InlayHintSupplier<DefaultInlayHintData> {

  private final SourceDefinedMethodCallInlayHintCollector sourceDefinedCollector;
  private final PlatformMethodCallInlayHintCollector platformCollector;

  public MethodCallInlayHintSupplier(
    SourceDefinedMethodCallInlayHintCollector sourceDefinedCollector,
    PlatformMethodCallInlayHintCollector platformCollector
  ) {
    this.sourceDefinedCollector = sourceDefinedCollector;
    this.platformCollector = platformCollector;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Хинты имён параметров проставляют ссылку части метки жадно (там, где она есть)
   * и не откладывают полей на резолв — используется дефолтный дата-класс
   * {@link DefaultInlayHintData}.
   *
   * @return Класс {@link DefaultInlayHintData}.
   */
  @Override
  public Class<DefaultInlayHintData> getInlayHintDataClass() {
    return DefaultInlayHintData.class;
  }

  @Override
  public List<InlayHint> getInlayHints(DocumentContext documentContext, InlayHintParams params) {
    var result = new ArrayList<InlayHint>();
    result.addAll(sourceDefinedCollector.getInlayHints(documentContext, params));
    result.addAll(platformCollector.getInlayHints(documentContext, params));
    return result;
  }
}
