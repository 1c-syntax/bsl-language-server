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

import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;

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
 * @param signatures  список сигнатур для метода (пустой для свойства)
 * @param sourceSymbol опциональный символ-источник члена (см. описание выше)
 */
public record MemberDescriptor(
  String name,
  MemberKind kind,
  String description,
  TypeRef returnType,
  List<SignatureDescriptor> signatures,
  Symbol sourceSymbol
) {

  public MemberDescriptor {
    signatures = List.copyOf(signatures);
  }

  /**
   * Конструктор для обратной совместимости (без sourceSymbol).
   */
  public MemberDescriptor(
    String name, MemberKind kind, String description,
    TypeRef returnType, List<SignatureDescriptor> signatures
  ) {
    this(name, kind, description, returnType, signatures, null);
  }

  /**
   * Конструктор для обратной совместимости (без сигнатур и sourceSymbol).
   */
  public MemberDescriptor(String name, MemberKind kind, String description, TypeRef returnType) {
    this(name, kind, description, returnType, List.of(), null);
  }

  public Optional<Symbol> getSourceSymbol() {
    return Optional.ofNullable(sourceSymbol);
  }

  /**
   * @return копия дескриптора с прикреплённым символом-источником.
   */
  public MemberDescriptor withSourceSymbol(Symbol symbol) {
    return new MemberDescriptor(name, kind, description, returnType, signatures, symbol);
  }

  public static MemberDescriptor method(String name) {
    return new MemberDescriptor(name, MemberKind.METHOD, "", TypeRef.UNKNOWN, List.of(), null);
  }

  public static MemberDescriptor method(String name, List<SignatureDescriptor> signatures) {
    var ret = signatures.isEmpty() ? TypeRef.UNKNOWN : signatures.get(0).returnType();
    return new MemberDescriptor(name, MemberKind.METHOD, "", ret, signatures, null);
  }

  public static MemberDescriptor method(String name, String description, List<SignatureDescriptor> signatures) {
    var ret = signatures.isEmpty() ? TypeRef.UNKNOWN : signatures.get(0).returnType();
    return new MemberDescriptor(name, MemberKind.METHOD, description, ret, signatures, null);
  }

  public static MemberDescriptor property(String name) {
    return new MemberDescriptor(name, MemberKind.PROPERTY, "", TypeRef.UNKNOWN, List.of(), null);
  }

  public static MemberDescriptor property(String name, TypeRef returnType) {
    return new MemberDescriptor(name, MemberKind.PROPERTY, "", returnType, List.of(), null);
  }
}
