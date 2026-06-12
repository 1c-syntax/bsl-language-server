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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Describable;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolDescription;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Член типа (метод или свойство).
 * <p>
 * Двуязычность: имя и описание хранятся как {@link BilingualString} — ru+en
 * пара из bsl-context (для legacy-источников en-сторона может быть пуста,
 * тогда {@link #displayName(Language)}/{@link #displayDescription(Language)}
 * fallback'ятся на primary). Compat-аксессоры {@link #name()} и
 * {@link #description()} возвращают primary-строку.
 *
 * @param bilingualName        двуязычное имя (ru + en); primary = ru или en если ru пуст
 * @param kind                 метод или свойство
 * @param bilingualDescription двуязычное краткое описание
 * @param returnTypes          union возможных типов
 * @param signatures           список сигнатур для метода
 * @param sourceSymbol         опциональный символ-источник
 * @param generic              признак «слотового» члена generic-типа платформы
 * @param metadata             платформенные метаданные
 * @param async                асинхронный метод (await-стиль, суффикс Асинх/Async)
 */
public record MemberDescriptor(
  BilingualString bilingualName,
  MemberKind kind,
  BilingualString bilingualDescription,
  TypeSet returnTypes,
  List<SignatureDescriptor> signatures,
  @Nullable Symbol sourceSymbol,
  boolean generic,
  PlatformMetadata metadata,
  boolean async
) {

  public MemberDescriptor {
    signatures = List.copyOf(signatures);
    if (returnTypes == null) {
      returnTypes = TypeSet.EMPTY;
    }
    if (metadata == null) {
      metadata = PlatformMetadata.EMPTY;
    }
    if (bilingualName == null) {
      bilingualName = BilingualString.EMPTY;
    }
    if (bilingualDescription == null) {
      bilingualDescription = BilingualString.EMPTY;
    }
  }

  /** Compat-конструктор без {@code async} (async = false). */
  public MemberDescriptor(BilingualString bilingualName, MemberKind kind, BilingualString bilingualDescription,
                          TypeSet returnTypes, List<SignatureDescriptor> signatures, @Nullable Symbol sourceSymbol,
                          boolean generic, PlatformMetadata metadata) {
    this(bilingualName, kind, bilingualDescription, returnTypes, signatures, sourceSymbol,
      generic, metadata, false);
  }

  /** Compat-конструктор: одноязычные {@code name}/{@code description}. */
  public MemberDescriptor(String name, MemberKind kind, String description, TypeSet returnTypes,
                          List<SignatureDescriptor> signatures, @Nullable Symbol sourceSymbol,
                          boolean generic, PlatformMetadata metadata) {
    this(BilingualString.of(name), kind, BilingualString.of(description), returnTypes,
      signatures, sourceSymbol, generic, metadata);
  }

  /** Compat-конструктор: одноязычное описание + двуязычное имя. */
  public MemberDescriptor(String name, MemberKind kind, String description, TypeSet returnTypes,
                          List<SignatureDescriptor> signatures, @Nullable Symbol sourceSymbol,
                          boolean generic, PlatformMetadata metadata,
                          BilingualString bilingualName) {
    this(bilingualName == null || bilingualName.isEmpty() ? BilingualString.of(name) : bilingualName,
      kind, BilingualString.of(description), returnTypes,
      signatures, sourceSymbol, generic, metadata);
  }

  /** Compat-аксессор: primary написание имени (для legacy-callsite'ов). */
  public String name() {
    return bilingualName.primary();
  }

  /** Compat-аксессор: primary описание (для legacy-callsite'ов). */
  public String description() {
    return bilingualDescription.primary();
  }

  /**
   * Сравнивает имя члена с {@code candidate} без учёта регистра по обоим
   * локализованным написаниям.
   */
  public boolean matches(String candidate) {
    return bilingualName.matches(candidate);
  }

  /** Имя члена для отображения в указанной локали LS. */
  public String displayName(Language language) {
    return bilingualName.forLanguage(language);
  }

  /**
   * Применим ли член к указанному языку скрипта — есть ли у него написание в
   * этой локали. Двуязычные и нейтральные члены применимы к обеим локалям;
   * одноязычные (например, англоязычный {@code [DeprecatedName]}-алиас
   * OneScript без русской пары) — лишь к своей. Признак зависит только от
   * имени члена, а не от прочих его свойств.
   */
  public boolean appliesTo(Language language) {
    return bilingualName.hasLanguage(language);
  }

  /** Описание члена для отображения в указанной локали LS. */
  public String displayDescription(Language language) {
    return bilingualDescription.forLanguage(language);
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
   * Иначе — используется primary описание {@link #bilingualDescription}.
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
    return SymbolDescription.of(description());
  }

  /** Копия дескриптора с прикреплённым символом-источником. */
  public MemberDescriptor withSourceSymbol(Symbol symbol) {
    return new MemberDescriptor(bilingualName, kind, bilingualDescription, returnTypes,
      signatures, symbol, generic, metadata, async);
  }

  /** Копия дескриптора с заменёнными метаданными платформы. */
  public MemberDescriptor withMetadata(PlatformMetadata newMetadata) {
    return new MemberDescriptor(bilingualName, kind, bilingualDescription, returnTypes,
      signatures, sourceSymbol, generic, newMetadata, async);
  }

  /** Копия дескриптора с заполненным двуязычным именем (ru + en). */
  public MemberDescriptor withBilingualName(BilingualString newName) {
    return new MemberDescriptor(newName,
      kind, bilingualDescription, returnTypes, signatures, sourceSymbol, generic, metadata, async);
  }

  /** Копия дескриптора с заполненным двуязычным описанием (ru + en). */
  public MemberDescriptor withBilingualDescription(BilingualString newDescription) {
    return new MemberDescriptor(bilingualName, kind, newDescription,
      returnTypes, signatures, sourceSymbol, generic, metadata, async);
  }

  /** Копия дескриптора с признаком асинхронности. */
  public MemberDescriptor withAsync(boolean newAsync) {
    return new MemberDescriptor(bilingualName, kind, bilingualDescription, returnTypes,
      signatures, sourceSymbol, generic, metadata, newAsync);
  }

  /** Копия дескриптора с подменёнными сигнатурами. */
  public MemberDescriptor withSignatures(List<SignatureDescriptor> newSignatures) {
    return new MemberDescriptor(bilingualName, kind, bilingualDescription, returnTypes,
      newSignatures, sourceSymbol, generic, metadata, async);
  }

  /** Копия дескриптора с признаком generic (placeholder в имени). */
  public MemberDescriptor withGeneric(boolean newGeneric) {
    if (this.generic == newGeneric) {
      return this;
    }
    return new MemberDescriptor(bilingualName, kind, bilingualDescription, returnTypes,
      signatures, sourceSymbol, newGeneric, metadata, async);
  }

  /** Compat shortcut для двуязычных имён ru/en строками. */
  public MemberDescriptor withLocalizedNames(String newNameRu, String newNameEn) {
    return withBilingualName(BilingualString.of(newNameRu, newNameEn));
  }

  /**
   * Возвращает копию дескриптора, в которой placeholder'ы {@code <X>} в
   * {@link #returnTypes} и {@link SignatureDescriptor#returnType} заменены
   * по {@code bindings}.
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
        var specializedReturn = TypeRef.specialize(sig.returnTypes(), bindings);
        var specializedParameters = specializeParameters(sig.parameters(), bindings);
        var returnChanged = !specializedReturn.equals(sig.returnTypes());
        var parametersChanged = !specializedParameters.equals(sig.parameters());
        if (returnChanged || parametersChanged) {
          rebuilt.add(new SignatureDescriptor(specializedParameters, specializedReturn,
            sig.bilingualDescription()));
          signaturesChanged = true;
        } else {
          rebuilt.add(sig);
        }
      }
      if (signaturesChanged) {
        newSignatures = rebuilt;
      }
    }
    if (newReturnTypes.equals(returnTypes) && !signaturesChanged) {
      return this;
    }
    return new MemberDescriptor(bilingualName, kind, bilingualDescription,
      newReturnTypes, newSignatures, sourceSymbol, generic, metadata, async);
  }

  /**
   * Проносит {@code bindings} в типы параметров сигнатуры. Если ни один
   * параметр не изменился — возвращает исходный список, чтобы вверху
   * сравнение по ссылке сработало без аллокаций.
   */
  private static List<ParameterDescriptor> specializeParameters(List<ParameterDescriptor> params,
                                                                Map<String, String> bindings) {
    if (params.isEmpty()) {
      return params;
    }
    List<ParameterDescriptor> rebuilt = null;
    for (var i = 0; i < params.size(); i++) {
      var p = params.get(i);
      var specializedTypes = TypeRef.specialize(p.types(), bindings);
      if (specializedTypes.equals(p.types())) {
        if (rebuilt != null) {
          rebuilt.add(p);
        }
        continue;
      }
      if (rebuilt == null) {
        rebuilt = new ArrayList<>(params.size());
        for (var j = 0; j < i; j++) {
          rebuilt.add(params.get(j));
        }
      }
      rebuilt.add(new ParameterDescriptor(p.bilingualName(), specializedTypes, p.optional(),
        p.bilingualDescription(), p.defaultValue(), p.variadic()));
    }
    return rebuilt == null ? params : List.copyOf(rebuilt);
  }

  public static MemberDescriptor method(String name) {
    return new MemberDescriptor(name, MemberKind.METHOD, "", TypeSet.EMPTY, List.of(), null, false,
      PlatformMetadata.EMPTY);
  }

  public static MemberDescriptor method(String name, List<SignatureDescriptor> signatures) {
    var ret = signatureReturnTypes(signatures);
    return new MemberDescriptor(name, MemberKind.METHOD, "", ret, signatures, null, false,
      PlatformMetadata.EMPTY);
  }

  public static MemberDescriptor method(String name, String description,
                                        List<SignatureDescriptor> signatures) {
    var ret = signatureReturnTypes(signatures);
    return new MemberDescriptor(name, MemberKind.METHOD, description, ret, signatures, null, false,
      PlatformMetadata.EMPTY);
  }

  public static MemberDescriptor property(String name) {
    return new MemberDescriptor(name, MemberKind.PROPERTY, "", TypeSet.EMPTY, List.of(), null, false,
      PlatformMetadata.EMPTY);
  }

  public static MemberDescriptor property(String name, TypeRef returnType) {
    return new MemberDescriptor(name, MemberKind.PROPERTY, "", typesOf(returnType), List.of(),
      null, false, PlatformMetadata.EMPTY);
  }

  public static MemberDescriptor property(String name, TypeRef returnType, String description) {
    return new MemberDescriptor(name, MemberKind.PROPERTY,
      description, typesOf(returnType), List.of(), null, false,
      PlatformMetadata.EMPTY);
  }

  /** Свойство с composite-типом ({@code Строка | Число}). */
  public static MemberDescriptor property(String name, TypeSet returnTypes, String description) {
    return new MemberDescriptor(name, MemberKind.PROPERTY,
      description,
      returnTypes,
      List.of(), null, false, PlatformMetadata.EMPTY);
  }

  /** Generic-property платформенного типа (например, {@code <Имя реквизита>}). */
  public static MemberDescriptor genericProperty(String name, TypeRef returnType, String description) {
    return new MemberDescriptor(name, MemberKind.PROPERTY,
      description, typesOf(returnType), List.of(), null, true,
      PlatformMetadata.EMPTY);
  }

  /**
   * Событие платформенного типа (например, {@code Форма.ПриОткрытии}). У события
   * есть сигнатура параметров (handler-контракт), но возвращаемого типа нет.
   */
  public static MemberDescriptor event(String name, String description,
                                       List<SignatureDescriptor> signatures) {
    return new MemberDescriptor(name, MemberKind.EVENT, description, TypeSet.EMPTY, signatures,
      null, false, PlatformMetadata.EMPTY);
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
