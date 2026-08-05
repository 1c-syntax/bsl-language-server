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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser.AccessPropertyContext;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Обращение к данным строки динамического списка через {@code ТекущаяСтрока}.
 * <p>
 * {@code ТаблицаФормы.ТекущаяСтрока.Реквизит} вызывает чтение объекта из базы
 * для каждой строки. Правильно использовать {@code .ТекущиеДанные.Реквизит},
 * который работает с уже считанными данными.
 * <p>
 * Диагностика срабатывает только если {@code ТекущаяСтрока} вызывается на
 * таблице формы, отображающей динамический список (тип
 * {@code ТаблицаФормы.ДинамическийСписок}): у таблиц над табличными частями,
 * деревьями значений и прочими данными формы обращение к текущей строке
 * базу не дёргает.
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  minutesToFix = 2,
  tags = { DiagnosticTag.PERFORMANCE, DiagnosticTag.BADPRACTICE }
)
@RequiredArgsConstructor
public class CurrentRowAccessDiagnostic extends AbstractVisitorDiagnostic {

  private static final String DYNAMIC_LIST_TABLE = "ТаблицаФормы.ДинамическийСписок";

  private final TypeService typeService;

  @Override
  public ParseTree visitAccessProperty(AccessPropertyContext ctx) {
    var id = ctx.IDENTIFIER();
    if (id == null) return ctx;

    if (!"ТекущаяСтрока".equalsIgnoreCase(id.getText())
      && !"CurrentRow".equalsIgnoreCase(id.getText())) return ctx;

    // .ТекущаяСтрока живёт в complexIdentifier (внутри выражения) либо в
    // callStatement (вызов вида X.ТекущаяСтрока.Записать()); dereference — когда
    // после модификатора .ТекущаяСтрока в контейнере есть ещё дети
    var modifier = ctx.getParent();
    var container = modifier.getParent();
    if (container == null || !hasFollowingChildren(container, modifier)) return ctx;

    // Ресивер должен быть таблицей формы над динамическим списком
    var receiverTypes = typeService.receiverTypesAt(documentContext, Ranges.create(id).getStart());
    if (receiverTypes.refs().stream().noneMatch(type -> isDynamicListTable(type, typeService))) {
      return ctx;
    }

    diagnosticStorage.addDiagnostic(id, info.getMessage("ТекущиеДанные", "CurrentData"));
    return ctx;
  }

  /**
   * Таблица формы над динамическим списком: сам ресивер может быть типизирован
   * как {@code ТаблицаФормы.ДинамическийСписок} (колонки строки неизвестны), либо
   * как пер-форма тип {@code ТаблицаФормы.<mdoRef>.<имя элемента>}, наследующий
   * вид данных через расширение — проверяем оба пути.
   */
  private static boolean isDynamicListTable(TypeRef type, TypeService typeService) {
    if (type.qualifiedName().equalsIgnoreCase(DYNAMIC_LIST_TABLE)) {
      return true;
    }
    return typeService.extensionsOf(type).stream()
      .anyMatch(extension -> extension.qualifiedName().equalsIgnoreCase(DYNAMIC_LIST_TABLE));
  }

  /** Есть ли у контейнера дети после {@code child}. */
  private static boolean hasFollowingChildren(ParseTree container, ParseTree child) {
    for (int i = 0; i < container.getChildCount(); i++) {
      if (container.getChild(i) == child) {
        return i < container.getChildCount() - 1;
      }
    }
    return false;
  }
}
