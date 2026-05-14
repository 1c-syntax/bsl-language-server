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
package com.github._1c_syntax.bsl.languageserver.types.symbol;

import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTreeVisitor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Optional;

/**
 * Символ, объявление которого лежит вне BSL/OScript кода: платформенные
 * глобалы, члены платформенных типов, элементы коллекций конфигурации.
 *
 * <p>
 * В отличие от {@link com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol},
 * у synthetic-символов нет {@code URI} и {@code Range} — они не привязаны
 * к месту в коде. Совместим с интерфейсом {@link Symbol} и потому поддерживается
 * единым механизмом resolution (см. {@code GlobalSymbolScope}).
 */
@Getter
@EqualsAndHashCode(of = {"name", "syntheticKind"})
@RequiredArgsConstructor
public final class SyntheticSymbol implements Symbol {

  private final String name;
  private final SyntheticKind syntheticKind;
  private final String description;
  /**
   * Тип значения, на которое ссылается этот символ
   * (например, для {@code Справочники} — {@code СправочникиМенеджер}).
   * {@link TypeRef#UNKNOWN} если тип неизвестен.
   */
  private final TypeRef valueType;
  /**
   * Родительский символ (например, для {@code Массив.Количество} — synthetic
   * для типа {@code Массив}); {@code null} для глобальных synthetic-символов.
   */
  private final Symbol owner;

  public SyntheticSymbol(String name, SyntheticKind kind, String description, TypeRef valueType) {
    this(name, kind, description, valueType, null);
  }

  public SyntheticSymbol(String name, SyntheticKind kind, String description) {
    this(name, kind, description, TypeRef.UNKNOWN, null);
  }

  @Override
  public SymbolKind getSymbolKind() {
    return switch (syntheticKind) {
      case PLATFORM_GLOBAL_METHOD, PLATFORM_MEMBER_METHOD -> SymbolKind.Method;
      case PLATFORM_GLOBAL_PROPERTY, PLATFORM_MEMBER_PROPERTY -> SymbolKind.Property;
      case CONFIGURATION_OBJECT -> SymbolKind.Class;
      case LIBRARY_MODULE -> SymbolKind.Module;
      case PLATFORM_GLOBAL_VARIABLE -> SymbolKind.Variable;
    };
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    // Synthetic-символы не участвуют в обходе symbol-tree модуля.
  }

  public Optional<Symbol> getOwnerSymbol() {
    return Optional.ofNullable(owner);
  }

  /**
   * Унифицированное описание для hover/completion/signature-help.
   * Платформенные synthetic-символы поставляют только текст; пометка об
   * устаревании сейчас не поддерживается схемой JSON, но интерфейс к этому
   * готов.
   */
  public SymbolDescription getSymbolDescription() {
    return SymbolDescription.of(description);
  }
}
