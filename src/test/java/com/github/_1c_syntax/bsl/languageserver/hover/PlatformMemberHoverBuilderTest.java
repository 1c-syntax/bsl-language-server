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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import org.eclipse.lsp4j.MarkupKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformMemberHoverBuilderTest {

  private static final TypeRef ARRAY = new TypeRef(TypeKind.PLATFORM, "Массив");
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Mock
  private Resources resources;
  @Mock
  private LanguageServerConfiguration configuration;
  @Mock
  private TypeRegistry typeRegistry;

  private PlatformMemberHoverBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new PlatformMemberHoverBuilder(resources, configuration, typeRegistry);

    when(configuration.getLanguage()).thenReturn(Language.RU);

    // typeRegistry.displayName(ref, lang) → ref.qualifiedName() для ru.
    when(typeRegistry.displayName(any(TypeRef.class), any(Language.class)))
      .thenAnswer(inv -> ((TypeRef) inv.getArgument(0)).qualifiedName());

    // resources.getResourceString(clazz, key) → ключ как «значение» (для проверок
    // через contains() на ключе; реальные ru-properties здесь не нужны).
    lenient().when(resources.getResourceString(eq(PlatformMemberHoverBuilder.class), any(String.class)))
      .thenAnswer(inv -> "[" + inv.getArgument(1) + "]");
  }

  @Test
  void propertyWithSimpleType() {
    // given
    var descriptor = MemberDescriptor.property("Количество", NUMBER);

    // when
    var content = builder.build(ARRAY, descriptor, -1);

    // then
    assertThat(content.getKind()).isEqualTo(MarkupKind.MARKDOWN);
    assertThat(content.getValue())
      .contains("Количество")
      .contains("Число")
      .contains("[memberOf]")
      .contains("Массив");
  }

  @Test
  void globalFunctionWithoutOwner() {
    // given
    var descriptor = MemberDescriptor.method("Сообщить");

    // when
    var content = builder.build(null, descriptor, -1);

    // then
    assertThat(content.getValue())
      .contains("Сообщить")
      .contains("[globalFunction]")
      .doesNotContain("[memberOf]");
  }

  @Test
  void globalPropertyWithoutOwner() {
    // given
    var descriptor = MemberDescriptor.property("Истина");

    // when
    var content = builder.build(null, descriptor, -1);

    // then
    assertThat(content.getValue())
      .contains("Истина")
      .contains("[globalProperty]")
      .doesNotContain("[memberOf]");
  }

  @Test
  void appendMetadataFullSnapshot() {
    // given
    var meta = new PlatformMetadata(
      "8.3.10", "8.3.20",
      List.of("ЗаменаA", "ЗаменаB"),
      Set.of(Availability.SERVER, Availability.THIN_CLIENT),
      AccessMode.READ,
      BilingualString.of("результат метода", "method result"),
      BilingualString.of("важное замечание", "important note"),
      List.of(BilingualString.of("Пример1", "Example1")),
      List.of(BilingualString.of("См.ссылка", "See link"))
    );
    var descriptor = new MemberDescriptor(
      BilingualString.of("X"), MemberKind.METHOD, BilingualString.EMPTY,
      TypeSet.EMPTY, List.of(), null, false, meta
    );

    // when
    var content = builder.build(null, descriptor, -1);

    // then
    var value = content.getValue();
    assertThat(value)
      .contains("[sinceVersion]").contains("8.3.10")
      .contains("[deprecatedSince]").contains("8.3.20")
      .contains("[recommendedReplacements]").contains("ЗаменаA").contains("ЗаменаB")
      .contains("[accessMode]").contains("[accessReadOnly]")
      .contains("[availabilities]")
      .contains("[returnValueDescription]").contains("результат метода")
      .contains("[notes]").contains("важное замечание")
      .contains("[example]").contains("Пример1")
      .contains("[seeAlso]").contains("См.ссылка");
  }

  @Test
  void asyncMethodRendersAsyncLabel() {
    var descriptor = new MemberDescriptor(
      BilingualString.of("ИнициализироватьАсинх"), MemberKind.METHOD, BilingualString.EMPTY,
      TypeSet.EMPTY, List.of(), null, false, PlatformMetadata.EMPTY
    ).withAsync(true);

    var value = builder.build(null, descriptor, -1).getValue();

    assertThat(value).contains("[asyncMethod]");
  }

  @Test
  void nonAsyncMethodHasNoAsyncLabel() {
    var descriptor = new MemberDescriptor(
      BilingualString.of("Инициализировать"), MemberKind.METHOD, BilingualString.EMPTY,
      TypeSet.EMPTY, List.of(), null, false, PlatformMetadata.EMPTY
    );

    var value = builder.build(null, descriptor, -1).getValue();

    assertThat(value).doesNotContain("[asyncMethod]");
  }

  @Test
  void appendMetadataReadWriteAccessMode() {
    // given
    var meta = new PlatformMetadata(
      "", "", List.of(), Set.of(), AccessMode.READ_WRITE,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()
    );
    var descriptor = new MemberDescriptor(
      BilingualString.of("X"), MemberKind.PROPERTY, BilingualString.EMPTY,
      TypeSet.EMPTY, List.of(), null, false, meta
    );

    // when
    var content = builder.build(null, descriptor, -1);

    // then
    assertThat(content.getValue())
      .contains("[accessMode]")
      .contains("[accessReadWrite]");
  }

  @Test
  void emptyMetadataDoesNotRenderMetadataBlock() {
    // given
    var descriptor = MemberDescriptor.property("X", NUMBER);

    // when
    var content = builder.build(null, descriptor, -1);

    // then
    assertThat(content.getValue())
      .doesNotContain("[sinceVersion]")
      .doesNotContain("[deprecatedSince]")
      .doesNotContain("[recommendedReplacements]");
  }

  @Test
  void methodWithSingleSignatureShowsParameters() {
    // given
    var param = new ParameterDescriptor(
      BilingualString.of("Значение"), TypeSet.of(NUMBER), false,
      BilingualString.of("число"), "");
    var signature = new SignatureDescriptor(List.of(param), TypeSet.of(NUMBER), "");
    var descriptor = MemberDescriptor.method("Удвоить", "удвоить число", List.of(signature));

    // when
    var content = builder.build(ARRAY, descriptor, 1);

    // then
    var value = content.getValue();
    assertThat(value)
      .contains("Удвоить(Значение)")
      .contains("[parameters]")
      .contains("`Значение`")
      .contains("Число")
      .contains("— число");
  }

  @Test
  void methodWithMultipleSignaturesListsAllCallVariants() {
    // given
    var sig1 = new SignatureDescriptor(
      List.of(new ParameterDescriptor(BilingualString.of("a"), TypeSet.of(NUMBER),
        false, BilingualString.EMPTY, "")),
      TypeSet.of(NUMBER), "");
    var sig2 = new SignatureDescriptor(
      List.of(new ParameterDescriptor(BilingualString.of("a"), TypeSet.of(STRING),
        false, BilingualString.EMPTY, "")),
      TypeSet.of(STRING), "");
    var descriptor = MemberDescriptor.method("F", "перегруженный", List.of(sig1, sig2));

    // when
    var content = builder.build(null, descriptor, 1);

    // then
    assertThat(content.getValue())
      .contains("[allCallVariants]")
      .contains("`F(a)`");
  }

  @Test
  void methodArityMismatchAddsDisclaimNote() {
    // given — у обоих сигнатур ровно 1 параметр, но callArgCount=5
    var sig = new SignatureDescriptor(
      List.of(new ParameterDescriptor(BilingualString.of("x"), TypeSet.of(NUMBER),
        false, BilingualString.EMPTY, "")),
      TypeSet.of(NUMBER), "");
    var sig2 = new SignatureDescriptor(
      List.of(new ParameterDescriptor(BilingualString.of("y"), TypeSet.of(NUMBER),
        false, BilingualString.EMPTY, "")),
      TypeSet.of(NUMBER), "");
    var descriptor = MemberDescriptor.method("F", "", List.of(sig, sig2));

    // when
    var content = builder.build(null, descriptor, 5);

    // then
    assertThat(content.getValue()).contains("[noMatchingSignature]");
  }

  @Test
  void parameterWithDefaultValueIsRendered() {
    // given
    var param = new ParameterDescriptor(
      BilingualString.of("Опц"), TypeSet.of(NUMBER), true,
      BilingualString.EMPTY, "0");
    var signature = new SignatureDescriptor(List.of(param), TypeSet.of(NUMBER), "");
    var descriptor = MemberDescriptor.method("X", "", List.of(signature));

    // when
    var content = builder.build(null, descriptor, 0);

    // then
    var value = content.getValue();
    assertThat(value)
      .contains("`Опц`: Число?")
      .doesNotContain("[optionalParameter]")
      .contains("= 0");
  }

  @Test
  void typeAwareSignaturePickWithArgTypes() {
    // given — две сигнатуры одной арности (1 параметр) с разными типами;
    // type-aware pick должен выбрать ту, что соответствует argType=Строка.
    var sigNumber = new SignatureDescriptor(
      List.of(new ParameterDescriptor(BilingualString.of("a"), TypeSet.of(NUMBER),
        false, BilingualString.EMPTY, "")),
      TypeSet.of(NUMBER), "number variant");
    var sigString = new SignatureDescriptor(
      List.of(new ParameterDescriptor(BilingualString.of("a"), TypeSet.of(STRING),
        false, BilingualString.EMPTY, "")),
      TypeSet.of(STRING), "string variant");
    var descriptor = MemberDescriptor.method("F", "", List.of(sigNumber, sigString));

    // when — argTypes говорит, что переданная строка
    var content = builder.build(null, descriptor, 1, List.of(TypeSet.of(STRING)));

    // then — выбран string variant (нет noMatchingSignature)
    assertThat(content.getValue())
      .contains("string variant")
      .doesNotContain("[noMatchingSignature]");
  }

  @Test
  void compositePropertyRendersUnionTypes() {
    // given — свойство с union-типами (Строка | Число).
    var descriptor = MemberDescriptor.property("Поле",
      TypeSet.of(NUMBER, STRING), "");

    // when
    var content = builder.build(ARRAY, descriptor, -1);

    // then
    var value = content.getValue();
    assertThat(value).contains("Поле");
    assertThat(value).containsAnyOf("Число | Строка", "Строка | Число");
  }

  @Test
  void methodWithoutMatchingArityFallsBackToFirstSignature() {
    // given — 2 сигнатуры, ни одна не подходит под callArgCount=10.
    var sig1 = new SignatureDescriptor(
      List.of(new ParameterDescriptor(BilingualString.of("a"), TypeSet.of(NUMBER),
        false, BilingualString.EMPTY, "")),
      TypeSet.of(NUMBER), "");
    var sig2 = new SignatureDescriptor(
      List.of(new ParameterDescriptor(BilingualString.of("a"), TypeSet.of(NUMBER),
        false, BilingualString.EMPTY, "")),
      TypeSet.of(NUMBER), "");
    var descriptor = MemberDescriptor.method("F", "", List.of(sig1, sig2));

    // when
    var content = builder.build(null, descriptor, 10);

    // then — disclaim + всё равно отрисовали сигнатуру.
    assertThat(content.getValue()).contains("[noMatchingSignature]");
  }

  @Test
  void methodWithChosenSignatureReturnTypeFromSignatureFallback() {
    // given — descriptor с пустым returnTypes, но у signature есть returnType.
    var param = new ParameterDescriptor(
      BilingualString.of("A"), TypeSet.of(NUMBER), false, BilingualString.EMPTY, "");
    var sig = new SignatureDescriptor(List.of(param), NUMBER, "");
    var descriptor = new MemberDescriptor(
      BilingualString.of("F"), MemberKind.METHOD, BilingualString.EMPTY,
      TypeSet.EMPTY,          // пустой descriptor.returnTypes
      List.of(sig),
      null, false, PlatformMetadata.EMPTY
    );

    // when
    var content = builder.build(null, descriptor, 1);

    // then — fallback к chosen.returnType() → Число.
    assertThat(content.getValue()).contains(": Число");
  }

  @Test
  void compositePropertyFallsBackToReturnType() {
    // given — property с пустым returnTypes, но returnType установлен.
    // Используется compat-ctor через property factory.
    var descriptor = MemberDescriptor.property("X", NUMBER);

    // when
    var content = builder.build(null, descriptor, -1);

    // then
    assertThat(content.getValue()).contains(": Число");
  }

  @Test
  void methodWithMultipleSignaturesEmptyReturnTypesFallsBackToDescriptor() {
    // given — две сигнатуры, sig1 пустой returnTypes, sig2 с returnTypes;
    // descriptor returnTypes установлен. Должны увидеть fallback (L225).
    var param = new ParameterDescriptor(
      BilingualString.of("A"), TypeSet.of(NUMBER), false, BilingualString.EMPTY, "");
    var sig1 = new SignatureDescriptor(List.of(param), TypeRef.UNKNOWN, "");
    var sig2 = new SignatureDescriptor(List.of(param), NUMBER, "");
    var descriptor = new MemberDescriptor(
      BilingualString.of("Calc"), MemberKind.METHOD, BilingualString.EMPTY,
      TypeSet.of(NUMBER),    // descriptor.returnTypes
      List.of(sig1, sig2),
      null, false, PlatformMetadata.EMPTY
    );

    // when
    var content = builder.build(null, descriptor, 0);

    // then — рендерится с типом Число (fallback с descriptor.returnTypes).
    assertThat(content.getValue()).contains(": Число");
  }

  @Test
  void methodWithSignaturesButAllReturnTypeNullEffectiveReturnTypeIsNull() {
    // given — descriptor без returnTypes, signature без returnType.
    var param = new ParameterDescriptor(
      BilingualString.of("A"), TypeSet.of(NUMBER), false, BilingualString.EMPTY, "");
    var sig = new SignatureDescriptor(List.of(param), (TypeRef) null, "");
    var descriptor = new MemberDescriptor(
      BilingualString.of("F"), MemberKind.METHOD, BilingualString.EMPTY,
      TypeSet.EMPTY, List.of(sig),
      null, false, PlatformMetadata.EMPTY
    );

    // when — effectiveReturnType должен вернуть null (L354).
    var content = builder.build(null, descriptor, 0);

    // then — нет ": Что-то" в label метода.
    var lines = content.getValue().split("\n");
    // первая строка: "```bsl"
    // вторая: "F(A): Тип" или "F(A)"
    var bslLine = java.util.Arrays.stream(lines).filter(l -> l.startsWith("F(")).findFirst().orElse("");
    assertThat(bslLine).doesNotContain(": null");
  }

  @Test
  void renderTypeSetFiltersEmptyDisplayNames() {
    // given — typeRegistry возвращает пустую строку для определённого TypeRef.
    var emptyDisplayRef = new TypeRef(TypeKind.PLATFORM, "_EMPTY_");
    when(typeRegistry.displayName(eq(emptyDisplayRef), any(Language.class)))
      .thenReturn("");

    var descriptor = new MemberDescriptor(
      BilingualString.of("X"), MemberKind.PROPERTY, BilingualString.EMPTY,
      TypeSet.of(java.util.Set.of(emptyDisplayRef, NUMBER)),
      List.of(),
      null, false, PlatformMetadata.EMPTY
    );

    // when
    var content = builder.build(null, descriptor, -1);

    // then — _EMPTY_ filtered out, Число остаётся.
    assertThat(content.getValue()).contains(": Число");
  }

  @Test
  void metadataWithRecommendedReplacementsRenderedAsList() {
    // given — replacements ИЛИ blank — пропускаются.
    var meta = new PlatformMetadata(
      "", "8.3.0", List.of("ИспользоватьНовый", "", "ИспользоватьДругой"),
      Set.of(), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()
    );
    var descriptor = new MemberDescriptor(
      BilingualString.of("X"), MemberKind.PROPERTY, BilingualString.EMPTY,
      TypeSet.EMPTY, List.of(), null, false, meta
    );

    // when
    var content = builder.build(null, descriptor, -1);

    // then — список замен есть, пустые элементы пропущены.
    assertThat(content.getValue())
      .contains("ИспользоватьНовый")
      .contains("ИспользоватьДругой");
  }

  @Test
  void emptyMetadataSetSkipsAvailabilitiesBlock() {
    // given
    var meta = new PlatformMetadata(
      "", "", List.of(), Set.of(), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()
    );
    var descriptor = new MemberDescriptor(
      BilingualString.of("X"), MemberKind.PROPERTY, BilingualString.EMPTY,
      TypeSet.EMPTY, List.of(), null, false, meta
    );

    // when
    var content = builder.build(null, descriptor, -1);

    // then — нет блока availabilities, accessMode и т.п.
    assertThat(content.getValue())
      .doesNotContain("[availabilities]")
      .doesNotContain("[accessMode]");
  }
}
