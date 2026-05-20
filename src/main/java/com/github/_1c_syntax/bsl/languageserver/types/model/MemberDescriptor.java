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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Член типа (метод или свойство).
 * <p>
 * Минимальный набор метаданных, необходимый для отображения в hover/completion
 * и сигнатур-помощнике (signature help).
 * <ul>
 *   <li>Для свойств {@code signatures} всегда пуст.</li>
 *   <li>Для методов {@code signatures} может содержать одну или несколько сигнатур.</li>
 *   <li>{@link #returnTypes} — union возможных типов (для composite-членов,
 *       например, атрибут справочника с типом {@code Строка | Число}). Для
 *       single-type содержит один ref; для UNKNOWN — {@link TypeSet#EMPTY}.</li>
 *   <li>{@link #sourceSymbol} — опциональный «бэкинг»-символ. Если член реально
 *       объявлен в коде (например, экспортный метод общего модуля) —
 *       здесь находится
 *       {@link com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol}.</li>
 *   <li>{@link #metadata} — платформенные метаданные (deprecated/sinceVersion/
 *       availabilities/accessMode и т.п.). Для не-платформенных членов —
 *       {@link PlatformMetadata#EMPTY}.</li>
 * </ul>
 *
 * @param name         имя члена в каноническом написании (как пишется в коде)
 * @param kind         метод или свойство
 * @param description  краткое описание (может быть пустым)
 * @param returnTypes  union типов возвращаемого значения / типа свойства
 * @param signatures   список сигнатур для метода (пустой для свойства)
 * @param sourceSymbol опциональный символ-источник члена
 * @param generic      признак «слотового» члена generic-типа платформы (например,
 *                     {@code <Имя реквизита>}, {@code <Имя табличной части>}).
 *                     Используется, чтобы не публиковать эти псевдо-члены при
 *                     наследовании от generic-типа в его специализациях
 *                     ({@code ДокументСсылка.МойДокумент} и т.п.).
 * @param metadata     платформенные метаданные (sinceVersion/deprecated/
 *                     availabilities/accessMode/recommendedReplacements/
 *                     notes/examples/seeAlso/returnValueDescription)
 */
public record MemberDescriptor(
  String name,
  MemberKind kind,
  String description,
  TypeSet returnTypes,
  List<SignatureDescriptor> signatures,
  Symbol sourceSymbol,
  boolean generic,
  PlatformMetadata metadata
) {

  public MemberDescriptor {
    signatures = List.copyOf(signatures);
    if (returnTypes == null) {
      returnTypes = TypeSet.EMPTY;
    }
    if (metadata == null) {
      metadata = PlatformMetadata.EMPTY;
    }
  }

  /**
   * Удобный аксессор для single-type случая: первый из {@link #returnTypes}
   * либо {@link TypeRef#UNKNOWN}.
   */
  public TypeRef returnType() {
    return returnTypes.refs().stream().findFirst().orElse(TypeRef.UNKNOWN);
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
    return new MemberDescriptor(name, kind, description, returnTypes, signatures, symbol, generic, metadata);
  }

  /**
   * @return копия дескриптора с заменёнными метаданными платформы.
   */
  public MemberDescriptor withMetadata(PlatformMetadata newMetadata) {
    return new MemberDescriptor(name, kind, description, returnTypes, signatures, sourceSymbol, generic,
      newMetadata == null ? PlatformMetadata.EMPTY : newMetadata);
  }

  /**
   * Возвращает копию дескриптора, в которой placeholder'ы {@code <X>} в
   * {@link #returnTypes} и {@link SignatureDescriptor#returnType} заменены
   * по {@code bindings} (имя placeholder'а без угловых скобок → имя
   * заменителя). Используется для специализации generic-членов:
   * например, для {@code <Имя справочника>} → {@code Контрагенты} member
   * {@code ПолучитьОбъект()} с returnType {@code СправочникОбъект.<Имя справочника>}
   * превращается в {@code СправочникОбъект.Контрагенты}.
   * <p>
   * Если в дескрипторе нет ни одного placeholder'а — возвращает {@code this}
   * без аллокации.
   *
   * @param bindings подстановки имя placeholder'а → имя заменителя; пустой
   *                 map — no-op
   * @return специализированный дескриптор либо {@code this}, если
   *         специализация не потребовалась
   */
  public MemberDescriptor specialize(Map<String, String> bindings) {
    if (bindings == null || bindings.isEmpty()) {
      return this;
    }
    var newReturnTypes = TypeRef.specialize(returnTypes, bindings);
    var newSignatures = signatures;
    boolean signaturesChanged = false;
    if (!newSignatures.isEmpty()) {
      var rebuilt = new ArrayList<SignatureDescriptor>(newSignatures.size());
      for (var sig : newSignatures) {
        // Специализируем ВЕСЬ union возврата сигнатуры — без потери второго
        // варианта (например, СправочникОбъект.<…> | Неопределено →
        // СправочникОбъект.X | Неопределено).
        var specializedReturn = TypeRef.specialize(sig.returnTypes(), bindings);
        if (specializedReturn == sig.returnTypes()) {
          rebuilt.add(sig);
        } else {
          rebuilt.add(new SignatureDescriptor(sig.parameters(), specializedReturn, sig.description()));
          signaturesChanged = true;
        }
      }
      if (signaturesChanged) {
        newSignatures = rebuilt;
      }
    }
    if (newReturnTypes == returnTypes && !signaturesChanged) {
      return this;
    }
    return new MemberDescriptor(name, kind, description,
      newReturnTypes, newSignatures, sourceSymbol, generic, metadata);
  }

  public static MemberDescriptor method(String name) {
    return new MemberDescriptor(name, MemberKind.METHOD, "", TypeSet.EMPTY, List.of(), null, false, PlatformMetadata.EMPTY);
  }

  public static MemberDescriptor method(String name, List<SignatureDescriptor> signatures) {
    var ret = signatureReturnTypes(signatures);
    return new MemberDescriptor(name, MemberKind.METHOD, "", ret, signatures, null, false, PlatformMetadata.EMPTY);
  }

  public static MemberDescriptor method(String name, String description, List<SignatureDescriptor> signatures) {
    var ret = signatureReturnTypes(signatures);
    return new MemberDescriptor(name, MemberKind.METHOD, description, ret, signatures, null, false, PlatformMetadata.EMPTY);
  }

  public static MemberDescriptor property(String name) {
    return new MemberDescriptor(name, MemberKind.PROPERTY, "", TypeSet.EMPTY, List.of(), null, false, PlatformMetadata.EMPTY);
  }

  public static MemberDescriptor property(String name, TypeRef returnType) {
    return new MemberDescriptor(name, MemberKind.PROPERTY, "", typesOf(returnType), List.of(), null, false, PlatformMetadata.EMPTY);
  }

  public static MemberDescriptor property(String name, TypeRef returnType, String description) {
    return new MemberDescriptor(name, MemberKind.PROPERTY,
      description == null ? "" : description, typesOf(returnType), List.of(), null, false, PlatformMetadata.EMPTY);
  }

  /**
   * Свойство с composite-типом ({@code Строка | Число}).
   */
  public static MemberDescriptor property(String name, TypeSet returnTypes, String description) {
    return new MemberDescriptor(name, MemberKind.PROPERTY,
      description == null ? "" : description,
      returnTypes == null ? TypeSet.EMPTY : returnTypes,
      List.of(), null, false, PlatformMetadata.EMPTY);
  }

  /** Generic-property платформенного типа (например, {@code <Имя реквизита>}). */
  public static MemberDescriptor genericProperty(String name, TypeRef returnType, String description) {
    return new MemberDescriptor(name, MemberKind.PROPERTY,
      description == null ? "" : description, typesOf(returnType), List.of(), null, true, PlatformMetadata.EMPTY);
  }

  private static TypeSet typesOf(TypeRef ref) {
    return ref == null || ref.equals(TypeRef.UNKNOWN) ? TypeSet.EMPTY : TypeSet.of(ref);
  }

  private static TypeSet signatureReturnTypes(List<SignatureDescriptor> signatures) {
    if (signatures.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var ret = signatures.get(0).returnType();
    return typesOf(ret);
  }
}
