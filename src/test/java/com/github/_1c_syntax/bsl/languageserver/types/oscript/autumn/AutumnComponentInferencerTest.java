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
package com.github._1c_syntax.bsl.languageserver.types.oscript.autumn;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationRepository;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.oscript.annotations.OScriptMetaAnnotationResolver;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
  private AutumnCollectionIndex collectionIndex;

  private AutumnComponentInferencer inferencer;

  @BeforeEach
  void setup() {
    // Реальный резолвер мета-аннотаций поверх пустого репозитория: для базовых
    // имён (Пластилин) он короткозамыкается, пользовательских аннотаций здесь нет.
    var metaResolver = new OScriptMetaAnnotationResolver(new AnnotationRepository());
    inferencer = new AutumnComponentInferencer(typeRegistry, beanIndex, collectionIndex, metaResolver);
    // Дефолт: коллекция не известна индексу — возвращается пустой TypeSet, а не
    // null. Конкретные тесты переопределяют ответ при необходимости.
    lenient().when(collectionIndex.resolve(anyString())).thenReturn(TypeSet.EMPTY);
  }

  @Test
  void resolvesBeanByPositionalName() {
    // given
    var beanRef = new TypeRef(TypeKind.USER, "ИмяЖелудя");
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.of(beanRef));
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
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.of(beanRef));
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
    when(beanIndex.resolve("Логин")).thenReturn(TypeSet.of(beanRef));
    var annotations = List.of(plasticine());

    // when
    var types = inferencer.inferInjectedType(annotations, "Логин", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(beanRef);
  }

  @Test
  void resolvesCollectionTypeFromCollectionIndex() {
    // given: индекс прилепляемых коллекций знает «Массив» — у его Получить()
    // описано возвращаемое значение ФиксированныйМассив. TypeRegistry-фоллбэк
    // в этом случае не должен использоваться.
    var fixedArrayRef = new TypeRef(TypeKind.PLATFORM, "ФиксированныйМассив");
    when(collectionIndex.resolve("Массив")).thenReturn(TypeSet.of(fixedArrayRef));
    var annotations = List.of(plasticine(named("Значение", "ИмяЖелудя"), named("Тип", "Массив")));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(fixedArrayRef);
    verifyNoInteractions(typeRegistry, beanIndex);
  }

  @Test
  void fallsBackToTypeRegistryWhenCollectionIndexHasNoDescription() {
    // given: индекс не знает коллекцию (нет описания возвращаемого значения у
    // Получить()) — это и есть кейс из issue #3959. Имя коллекции совпадает с
    // именем платформенного типа, поэтому фоллбэк через TypeRegistry даёт «Массив».
    var arrayRef = new TypeRef(TypeKind.PLATFORM, "Массив");
    when(collectionIndex.resolve("Массив")).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.resolve("Массив", FILE_TYPE)).thenReturn(Optional.of(arrayRef));
    var annotations = List.of(plasticine(named("Значение", "ИмяЖелудя"), named("Тип", "Массив")));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(arrayRef);
  }

  @Test
  void returnsEmptyWhenNeitherCollectionIndexNorTypeRegistryResolves() {
    // given: пользовательская коллекция «МояКоллекция» — индекс её не знает,
    // и одноимённого типа в реестре тоже нет.
    when(collectionIndex.resolve("МояКоллекция")).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.resolve("МояКоллекция", FILE_TYPE)).thenReturn(Optional.empty());
    var annotations = List.of(plasticine(named("Значение", "ИмяЖелудя"), named("Тип", "МояКоллекция")));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.isEmpty()).isTrue();
  }

  @Test
  void returnsEmptyWhenTypeIsExplicitlyBlank() {
    // given: явный Тип="" — в autumn это ошибка (не «Желудь» и не коллекция), валидного
    // типа нет, поэтому и bsl-ls тип не выводит
    var annotations = List.of(plasticine(named("Значение", "ИмяЖелудя"), named("Тип", "")));

    // when
    var types = inferencer.inferInjectedType(annotations, "Поле", FILE_TYPE);

    // then
    assertThat(types.isEmpty()).isTrue();
  }

  @Test
  void resolvesByBeanNameWhenTypeIsBeanLiteral() {
    // given
    var beanRef = new TypeRef(TypeKind.USER, "ИмяЖелудя");
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.of(beanRef));
    var annotations = List.of(plasticine(named("Значение", "ИмяЖелудя"), named("Тип", "Желудь")));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(beanRef);
  }

  @Test
  void resolvesBeanNameOnlyViaBeanIndexNotByType() {
    // given: имя желудя резолвится через реестр желудей, а не как имя типа —
    // TypeRegistry для имени желудя не должен дёргаться вовсе.
    var beanRef = new TypeRef(TypeKind.USER, "ПереименованныйКласс");
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.of(beanRef));
    var annotations = List.of(plasticine(positional("ИмяЖелудя")));

    // when
    var types = inferencer.inferInjectedType(annotations, "Поле", FILE_TYPE);

    // then
    assertThat(types.refs()).containsExactly(beanRef);
    verifyNoInteractions(typeRegistry);
  }

  @Test
  void returnsEmptyWhenBeanNameIsBlank() {
    // given
    // у &Пластилин нет имени, и имя переменной тоже пустое -> имя желудя пустое
    var annotations = List.of(plasticine());

    // when
    var types = inferencer.inferInjectedType(annotations, "", FILE_TYPE);

    // then
    assertThat(types.isEmpty()).isTrue();
    verifyNoInteractions(beanIndex, typeRegistry, collectionIndex);
  }

  @Test
  void returnsEmptyWhenNoInjectionAnnotation() {
    // given
    var annotations = List.of(annotation("Желудь"));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.isEmpty()).isTrue();
    verifyNoInteractions(typeRegistry, beanIndex, collectionIndex);
  }

  @Test
  void returnsEmptyWhenBeanNotInIndex() {
    // given: желудя с таким именем нет в реестре желудей
    when(beanIndex.resolve("ИмяЖелудя")).thenReturn(TypeSet.EMPTY);
    var annotations = List.of(plasticine(positional("ИмяЖелудя")));

    // when
    var types = inferencer.inferInjectedType(annotations, "ВнедренныйЖелудь", FILE_TYPE);

    // then
    assertThat(types.isEmpty()).isTrue();
    verifyNoInteractions(typeRegistry);
  }

  @Test
  void injectedBeanReturnsNameForPlainInjection() {
    // given
    var annotations = List.of(plasticine(positional("ИмяЖелудя")));

    // when
    var injectedBean = inferencer.injectedBean(annotations, "Поле");

    // then
    assertThat(injectedBean).hasValueSatisfying(bean -> {
      assertThat(bean.name()).isEqualTo("ИмяЖелудя");
      assertThat(bean.collection()).isFalse();
    });
  }

  @Test
  void injectedBeanFallsBackToMemberNameAndFlagsCollection() {
    // given: имя желудя не задано -> имя члена; Тип=коллекция -> флаг коллекции
    var annotations = List.of(plasticine(named("Тип", "Массив")));

    // when
    var injectedBean = inferencer.injectedBean(annotations, "Обработчики");

    // then
    assertThat(injectedBean).hasValueSatisfying(bean -> {
      assertThat(bean.name()).isEqualTo("Обработчики");
      assertThat(bean.collection()).isTrue();
    });
  }

  @Test
  void injectedBeanEmptyWhenTypeIsExplicitlyBlank() {
    // given: явный Тип="" — ошибка (не «Желудь» и не коллекция); как и inferInjectedType,
    // injectedBean не разрешает такую точку, иначе линза вела бы к ложным целям
    var annotations = List.of(plasticine(named("Тип", "")));

    // when / then
    assertThat(inferencer.injectedBean(annotations, "Поле")).isEmpty();
  }

  @Test
  void injectedBeanEmptyWhenNoInjectionAnnotation() {
    // given
    var annotations = List.of(annotation("Желудь"));

    // when / then
    assertThat(inferencer.injectedBean(annotations, "Поле")).isEmpty();
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
