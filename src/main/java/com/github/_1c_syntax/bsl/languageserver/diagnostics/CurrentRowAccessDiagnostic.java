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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.*;
import com.github._1c_syntax.bsl.parser.BSLParser.AccessPropertyContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Обращение к свойствам через {@code ТекущаяСтрока} динамического списка.
 * <p>
 * {@code ТабличноеПоле.ТекущаяСтрока.Реквизит} вызывает чтение объекта из базы
 * для каждой строки. Правильно использовать {@code .ТекущиеДанные.Реквизит},
 * который работает с уже считанными данными.
 * <p>
 * Детект dereference: в грамматике bsl-parser каждый {@code accessProperty}
 * обёрнут в {@code modifier} внутри {@code complexIdentifier}. Когда
 * {@code .ТекущаяСтрока} используется как ресивер для дальнейшего доступа,
 * внешний {@code complexIdentifier} содержит дополнительного ребёнка —
 * отсюда проверка {@code childCount >= 3}.
 *
 * @see <a href="https://its.1c.ru/db/metod8dev/content/2812/hdoc">ИТС: Динамический список</a>
 */
@DiagnosticMetadata(type=DiagnosticType.CODE_SMELL, severity=DiagnosticSeverity.MAJOR,
  scope=DiagnosticScope.BSL, minutesToFix=2,
  tags={DiagnosticTag.PERFORMANCE, DiagnosticTag.BADPRACTICE})
public class CurrentRowAccessDiagnostic extends AbstractVisitorDiagnostic {

  /**
   * Посещает узел доступа к свойству. Если свойство — {@code ТекущаяСтрока}
   * (или {@code CurrentRow}) и оно используется как ресивер для дальнейшего
   * обращения (dereference), добавляет диагностику.
   *
   * @param ctx контекст свойства доступа (содержит DOT и IDENTIFIER).
   * @return {@code ctx} для продолжения обхода дерева.
   */
  @Override public ParseTree visitAccessProperty(AccessPropertyContext ctx) {
    var id = ctx.IDENTIFIER();
    if (id == null) return ctx;
    if (!"ТекущаяСтрока".equalsIgnoreCase(id.getText())
      && !"CurrentRow".equalsIgnoreCase(id.getText())) return ctx;

    var ci = ctx.getParent().getParent(); // modifier → complexIdentifier
    if (ci != null && ci.getChildCount() >= 3) {
      diagnosticStorage.addDiagnostic(id, info.getMessage("ТекущиеДанные", "CurrentData"));
    }
    return ctx;
  }
}
