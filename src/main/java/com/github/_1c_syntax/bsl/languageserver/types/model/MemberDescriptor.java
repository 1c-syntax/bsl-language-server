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
package com.github._1c_syntax.bsl.languageserver.types.model;

import com.github._1c_syntax.bsl.languageserver.context.symbol.Describable;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolDescription;

import java.util.List;
import java.util.Optional;

/**
 * Член типа (метод или свойство).
 * <p>
 * Минимальный набор метаданных, необходимый для отображения в hover/completion
 * и сигнатур-помощнике (signature help).
 * <ul>
 *   <li>Для свойств {@code signatures} всегда пуст.</li>
 *   <li>Для методов {@code signatures} может содержать одну или несколько сигнатур;
 *       при пустом списке поведение совместимо с прежним API: метод считается
 *       без параметров с типом возврата {@link #returnType}.</li>
 *   <li>{@link #returnTypes} — union возможных типов для composite-членов
 *       (например, атрибут справочника с типом {@code Строка | Число}). Для
 *       single-type членов содержит {@code TypeSet.of(returnType)}; для
 *       {@link TypeRef#UNKNOWN} — {@link TypeSet#EMPTY}.</li>
 *   <li>{@link #sourceSymbol} — опциональный «бэкинг»-символ. Если член реально
 *       объявлен в коде (например, экспортный метод общего модуля или
 *       экспортная переменная объектного модуля) — здесь находится
 *       {@link com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol}.
 *       Если член платформенный — здесь может лежать
 *       {@link com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol}.</li>
 * </ul>
 *
 * @param name        имя члена в каноническом написании (как пишется в коде)
 * @param kind        метод или свойство
 * @param description краткое описание (может быть пустым)
 * @param returnType  тип возвращаемого значения / тип свойства; {@link TypeRef#UNKNOWN} если неизвестен
 * @param returnTypes union типов возвращаемого значения / типа свойства; для single-type
 *                    содержит один ref, для composite — несколько; для UNKNOWN — пустой
 * @param signatures  список сигнатур для метода (пустой для свойства)
 * @param sourceSymbol опциональный символ-источник члена (см. описание выше)
 */
public record MemberDescriptor(
  String name,
  MemberKind kind,
  String description,
  TypeRef returnType,
  TypeSet returnTypes,
  List<SignatureDescriptor> signatures,
  Symbol sourceSymbol
) {

  public MemberDescriptor {
    signatures = List.copyOf(signatures);
    if (returnTypes == null) {
      returnTypes = returnType == null || returnType.equals(TypeRef.UNKNOWN)
        ? TypeSet.EMPTY
        : TypeSet.of(returnType);
    }
  }

  /**
   * Конструктор для обратной совместимости (без returnTypes/sourceSymbol).
   */
  public MemberDescriptor(
    String name, MemberKind kind, String description,
    TypeRef returnType, List<SignatureDescriptor> signatures
  ) {
    this(name, kind, description, returnType, null, signatures, null);
  }

  /**
   * Конструктор для обратной совместимости (без сигнатур/returnTypes/sourceSymbol).
   */
  public MemberDescriptor(String name, MemberKind kind, String description, TypeRef returnType) {
    this(name, kind, description, returnType, null, List.of(), null);
  }

  public Optional<Symbol> getSourceSymbol() {
    return Optional.ofNullable(sourceSymbol);
  }

  /**
   * Унифицированное описание члена.
   * <p>
   * Если {@link #sourceSymbol} реализует {@link Describable} — описание
   * берётся из него (BSL-doc-comment, с поддержкой пометки об устаревании).
   * Иначе — используется {@link #description}, переданный явно (платформенный
   * JSON, конфигурационные метаданные и т.п.).
   *
   * @return унифицированное описание или {@link SymbolDescription#EMPTY}.
   */
  public SymbolDescription getSymbolDescription() {
    if (sourceSymbol instanceof Describable describable) {
      var fromSource = describable.getSymbolDescription();
      if (!fromSource.isEmpty()) {
        return fromSource;
      }
    }
    return SymbolDescription.of(description);
  }

  /**
   * @return копия дескриптора с прикреплённым символом-источником.
   */
  public MemberDescriptor withSourceSymbol(Symbol symbol) {
    return new MemberDescriptor(name, kind, description, returnType, returnTypes, signatures, symbol);
  }

  public static MemberDescriptor method(String name) {
    return new MemberDescriptor(name, MemberKind.METHOD, "", TypeRef.UNKNOWN, null, List.of(), null);
  }

  public static MemberDescriptor method(String name, List<SignatureDescriptor> signatures) {
    var ret = signatures.isEmpty() ? TypeRef.UNKNOWN : signatures.get(0).returnType();
    return new MemberDescriptor(name, MemberKind.METHOD, "", ret, null, signatures, null);
  }

  public static MemberDescriptor method(String name, String description, List<SignatureDescriptor> signatures) {
    var ret = signatures.isEmpty() ? TypeRef.UNKNOWN : signatures.get(0).returnType();
    return new MemberDescriptor(name, MemberKind.METHOD, description, ret, null, signatures, null);
  }

  public static MemberDescriptor property(String name) {
    return new MemberDescriptor(name, MemberKind.PROPERTY, "", TypeRef.UNKNOWN, null, List.of(), null);
  }

  public static MemberDescriptor property(String name, TypeRef returnType) {
    return new MemberDescriptor(name, MemberKind.PROPERTY, "", returnType, null, List.of(), null);
  }

  public static MemberDescriptor property(String name, TypeRef returnType, String description) {
    return new MemberDescriptor(name, MemberKind.PROPERTY,
      description == null ? "" : description, returnType, null, List.of(), null);
  }

  /**
   * Свойство с composite-типом ({@code Строка | Число}).
   *
   * @param name        имя свойства
   * @param returnTypes union возможных типов
   * @param description описание
   */
  public static MemberDescriptor property(String name, TypeSet returnTypes, String description) {
    var head = returnTypes == null || returnTypes.isEmpty()
      ? TypeRef.UNKNOWN
      : returnTypes.refs().iterator().next();
    return new MemberDescriptor(name, MemberKind.PROPERTY,
      description == null ? "" : description, head, returnTypes, List.of(), null);
  }
}
