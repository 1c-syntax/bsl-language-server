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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Символ модуля документа.
 */
@Value
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"children"})
public class ModuleSymbol implements SourceDefinedSymbol {

  private static final Set<ModuleType> MODULE_TYPES_TO_APPEND_NAME = EnumSet.of(
    ModuleType.ObjectModule,
    ModuleType.ManagerModule
  );

  /**
   * Имя символа.
   * <p>
   * Если у документа есть валидный mdoRef, то содержит его и (при необходимости) квалификатор в виде типа модуля
   * ({@link com.github._1c_syntax.bsl.types.ModuleType}).
   * В остальных случаях содержит строковое представление uri ({@link DocumentContext#getUri()}.
   */
  String name;

  @EqualsAndHashCode.Include
  DocumentContext owner;

  Range range;

  /**
   * Область первого токена модуля
   */
  @EqualsAndHashCode.Include
  Range selectionRange;

  @Builder.Default
  List<SourceDefinedSymbol> children = new ArrayList<>();

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Module;
  }

  @Override
  public Optional<SourceDefinedSymbol> getParent() {
    return Optional.empty();
  }

  @Override
  public void setParent(Optional<SourceDefinedSymbol> parent) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitModule(this);
  }

  /**
   * Вычислить имя символа модуля по ссылке на объект-метаданные и типу модуля.
   * <p>
   * Имя является чистой производной от {@code mdoRef} и {@code moduleType}: к {@code mdoRef}
   * для отдельных типов модулей дописывается квалификатор-тип, чтобы различать несколько модулей
   * одного объекта метаданных. Метод детерминирован и не зависит от загрузки самого документа,
   * поэтому одинаково применим как при построении символа, так и при индексации ссылок на модуль.
   *
   * @param mdoRef     Ссылка на объект-метаданные модуля (например, {@code CommonModule.ОбщийМодуль1}).
   * @param moduleType Тип модуля (например, {@link ModuleType#CommonModule}).
   * @return Имя символа модуля, совпадающее с {@link ModuleSymbol#getName()}.
   */
  public static String nameOf(String mdoRef, ModuleType moduleType) {
    var name = mdoRef;
    if (MODULE_TYPES_TO_APPEND_NAME.contains(moduleType)) {
      name += "." + moduleType.name();
    }
    return name;
  }

}
