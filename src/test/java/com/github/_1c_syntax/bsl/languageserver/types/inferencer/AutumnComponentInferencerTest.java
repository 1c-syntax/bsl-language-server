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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutumnComponentInferencerTest {

  private static final FileType FILE_TYPE = FileType.BSL;

  @Mock
  private TypeRegistry typeRegistry;

  @Mock
  private AutumnBeanIndex beanIndex;

  @Mock
  private OScriptLibraryIndex libraryIndex;

  @Mock
  private ServerContextProvider serverContextProvider;

  private AutumnComponentInferencer inferencer;

  @BeforeEach
  void setup() {
    // Реальный резолвер мета-аннотаций: для базовых имён (Пластилин) он
    // короткозамыкается и не обращается к индексу библиотек.
    var metaResolver = new AutumnMetaAnnotationResolver(libraryIndex, serverContextProvider);
    inferencer = new AutumnComponentInferencer(typeRegistry, beanIndex, metaResolver);
  }

  @Test
  void resolvesBeanByPositionalName() {
    // given
    var beanRef = new TypeRef(TypeKind.USER, "ИмяЖелудя");
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.resolve("ИмяЖелудя", FILE_TYPE)).thenReturn(Optional.of(beanRef));
    var annotations = List.of(plasticine(positional("ИмяЖелудя")));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(beanRef);
  }

  @Test
  void resolvesBeanByNamedValueParameter() {
    // given
    var beanRef = new TypeRef(TypeKind.USER, "ИмяЖелудя");
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.resolve("ИмяЖелудя", FILE_TYPE)).thenReturn(Optional.of(beanRef));
    var annotations = List.of(plasticine(named("Значение", "ИмяЖелудя")));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(beanRef);
  }

  @Test
  void fallsBackToVariableNameWhenAnnotationHasNoValue() {
    // given
    var beanRef = new TypeRef(TypeKind.USER, "Логин");
    when(beanIndex.resolve("Логин")).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.resolve("Логин", FILE_TYPE)).thenReturn(Optional.of(beanRef));
    var annotations = List.of(plasticine());

    // when
    var types = inferencer.inferInjectedType(annotations, "Логин", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(beanRef);
  }

  @Test
  void resolvesCollectionTypeWhenTypeParameterGiven() {
    // given
    var arrayRef = new TypeRef(TypeKind.PLATFORM, "Массив");
    when(typeRegistry.resolve("Массив", FILE_TYPE)).thenReturn(Optional.of(arrayRef));
    var annotations = List.of(plasticine(named("Значение", "ИмяЖелудя"), named("Тип", "Массив")));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(arrayRef);
  }

  @Test
  void fallsBackToBeanNameWhenTypeIsBlank() {
    // given
    var beanRef = new TypeRef(TypeKind.USER, "ИмяЖелудя");
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.resolve("ИмяЖелудя", FILE_TYPE)).thenReturn(Optional.of(beanRef));
    var annotations = List.of(plasticine(named("Значение", "ИмяЖелудя"), named("Тип", "")));

    // when
    var types = inferencer.inferInjectedType(annotations, "Поле", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(beanRef);
  }

  @Test
  void resolvesByBeanNameWhenTypeIsBeanLiteral() {
    // given
    var beanRef = new TypeRef(TypeKind.USER, "ИмяЖелудя");
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.resolve("ИмяЖелудя", FILE_TYPE)).thenReturn(Optional.of(beanRef));
    var annotations = List.of(plasticine(named("Значение", "ИмяЖелудя"), named("Тип", "Желудь")));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(beanRef);
  }

  @Test
  void prefersBeanIndexOverDirectTypeResolution() {
    // given
    var renamedRef = new TypeRef(TypeKind.USER, "ПереименованныйКласс");
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.of(renamedRef));
    var annotations = List.of(plasticine(positional("ИмяЖелудя")));

    // when
    var types = inferencer.inferInjectedType(annotations, "Поле", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(renamedRef);
    verifyNoInteractions(typeRegistry);
  }

  @Test
  void returnsEmptyWhenNoInjectionAnnotation() {
    // given
    var annotations = List.of(annotation("Желудь"));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.isEmpty()).isTrue();
    verifyNoInteractions(typeRegistry, beanIndex);
  }

  @Test
  void returnsEmptyWhenTypeUnresolved() {
    // given
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.resolve("ИмяЖелудя", FILE_TYPE)).thenReturn(Optional.empty());
    var annotations = List.of(plasticine(positional("ИмяЖелудя")));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.isEmpty()).isTrue();
  }

  private static Annotation plasticine(AnnotationParameterDefinition... parameters) {
    return Annotation.builder()
      .name("Пластилин")
      .kind(AnnotationKind.CUSTOM)
      .parameters(List.of(parameters))
      .build();
  }

  private static Annotation annotation(String name) {
    return Annotation.builder()
      .name(name)
      .kind(AnnotationKind.CUSTOM)
      .build();
  }

  private static AnnotationParameterDefinition positional(String value) {
    return new AnnotationParameterDefinition("", Either.forLeft(value), true);
  }

  private static AnnotationParameterDefinition named(String name, String value) {
    return new AnnotationParameterDefinition(name, Either.forLeft(value), true);
  }
}
